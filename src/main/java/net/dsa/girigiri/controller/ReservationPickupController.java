package net.dsa.girigiri.controller;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.PickupBatchItemResultDto;
import net.dsa.girigiri.domain.dto.PickupLookupResponseDto;
import net.dsa.girigiri.domain.entity.ReceiptEntity;
import net.dsa.girigiri.domain.entity.ReservationEntity;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.exception.PickupNotAllowedException;
import net.dsa.girigiri.exception.ReservationAccessDeniedException;
import net.dsa.girigiri.service.LookupService;
import net.dsa.girigiri.service.ReceiptService;
import net.dsa.girigiri.service.ReservationService;
import net.dsa.girigiri.service.StoreAccessService;
import net.dsa.girigiri.util.SupabaseStorageClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 현장 픽업 처리(QR 스캔/코드 입력)와 영수증 다운로드.
 * 2026-09-03 — 686줄이던 ReservationController에서 분리했다(레이어 규칙 정리, 도메인 분할).
 * @RequestMapping("/reservation")은 그대로라 URL은 하나도 안 바뀐다.
 */
@Controller
@RequestMapping("/reservation")
@RequiredArgsConstructor
public class ReservationPickupController {

	private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("MM월 dd일 HH:mm");

	private final ReservationService reservationService;
	private final ReceiptService receiptService;
	private final LookupService lookupService;
	private final StoreAccessService storeAccessService;
	private final SupabaseStorageClient supabaseStorageClient;

	/** 사장님이 픽업 현장에서 픽업 코드를 입력하는 화면. */
	@GetMapping("/pickup")
	public String pickupForm() {
		return "reservationView/pickup";
	}

	// 추가됨 (2026-09-08, 코드 감사) — 왜: pickup/pickupBatch가 픽업 코드만으로 예약을 찾아 상태를
	// 바꾸면서 매장 소유권을 확인하지 않고 있었다 — ReservationStoreController#storeCancel이 이미
	// "다른 매장 픽업 코드를 알기만 하면 취소시킬 수 있는 구멍"을 고친 것과 같은 유형의 버그가
	// 픽업 확인 경로에는 남아있던 것. 그 컨트롤러와 동일한 패턴(resolveCurrentStoreId)을 쓴다.
	private Long resolveCurrentStoreId(HttpSession session) {
		Long userId = (Long) session.getAttribute("userId");
		return storeAccessService.getMyStore(userId).getId();
	}

	/**
	 * QR 스캔 직후, 실제로 "픽업 완료 처리" 버튼을 누르기 전에 매장/상품명/수량을 화면에 미리
	 * 보여주기 위한 조회 전용 API. confirmPickup과 달리 예약 상태를 바꾸지 않고 조회만 하므로
	 * 스캔이 여러 번 잡혀도 안전하다. (JS fetch로 호출, 응답은 JSON)
	 */
	@GetMapping("/pickup/lookup")
	@ResponseBody
	public PickupLookupResponseDto pickupLookup(@RequestParam String pickupCode) {
		ReservationEntity reservation = reservationService.findByPickupCode(pickupCode).orElse(null);
		if (reservation == null) {
			return PickupLookupResponseDto.notFound();
		}

		// 변경됨 (2026-09-08, 코드 감사) — "픽업 가능 상태" 판정을 여기 로컬 switch 대신
		// ReservationService.blockedPickupMessage로 통일(confirmPickup과 메시지가 갈라져 있던 것 정리).
		String blockedMessage = reservationService.blockedPickupMessage(reservation);
		if (blockedMessage != null) {
			return PickupLookupResponseDto.blocked(blockedMessage);
		}

		StoreEntity store = reservationService.findStoreById(reservation.getStoreId()).orElse(null);

		return PickupLookupResponseDto.success(
				store != null ? store.getStoreName() : "-",
				reservation.getProductName(),
				reservation.getReservedQuantity(),
				reservation.getTotalPrice());
	}

	/**
	 * 여러 예약을 한 번에 픽업 처리한다. 화면에서 QR을 여러 번 스캔해서 "장바구니"처럼 모아뒀다가
	 * 한 번에 이 API로 보낸다. 코드 하나가 실패(이미 픽업됨/취소됨 등)해도 나머지 코드는 계속
	 * 처리하도록, confirmPickup을 코드마다 개별로 try/catch 해서 결과를 모은다 — 배치 전체가
	 * 한 건의 실패 때문에 통째로 막히면 안 되기 때문.
	 */
	@PostMapping("/pickup/batch")
	@ResponseBody
	public List<PickupBatchItemResultDto> pickupBatch(@RequestParam List<String> pickupCodes, HttpSession session) {
		Long storeId = resolveCurrentStoreId(session);
		List<PickupBatchItemResultDto> results = new ArrayList<>();

		for (String pickupCode : pickupCodes) {
			try {
				ReservationEntity target = reservationService.findByPickupCode(pickupCode)
						.orElseThrow(() -> new EntityNotFoundException("픽업 코드를 찾을 수 없습니다: " + pickupCode));
				if (!target.getStoreId().equals(storeId)) {
					throw new PickupNotAllowedException("다른 매장의 예약은 픽업 처리할 수 없어요.");
				}

				ReservationEntity reservation = reservationService.confirmPickup(pickupCode);
				results.add(PickupBatchItemResultDto.success(
						pickupCode,
						reservation.getProductName(),
						reservation.getReservedQuantity()));
			} catch (EntityNotFoundException | PickupNotAllowedException e) {
				results.add(PickupBatchItemResultDto.failure(pickupCode, e.getMessage()));
			}
		}

		return results;
	}

	/**
	 * 픽업 코드 확인 처리: ReservationService.confirmPickup 하나만 부르면 된다.
	 * 결과 화면에서 "뭘 픽업 처리한 건지" 바로 보이게, 상품/매장 정보도 같이 조회해서 넘긴다.
	 *
	 * (참고: 화면(pickup.html)은 이제 스캔/입력한 코드를 모았다가 위 pickupBatch로 한 번에 보내는
	 *  방식으로 바뀌어서, 이 단건 엔드포인트는 화면에서는 더 이상 안 쓰인다. 다른 곳에서 단건 처리가
	 *  필요해질 수도 있어 일단 남겨둔다.)
	 */
	@PostMapping("/pickup")
	public String pickup(@RequestParam String pickupCode, HttpSession session, Model model) {
		ReservationEntity target = reservationService.findByPickupCode(pickupCode)
				.orElseThrow(() -> new EntityNotFoundException("픽업 코드를 찾을 수 없습니다: " + pickupCode));
		if (!target.getStoreId().equals(resolveCurrentStoreId(session))) {
			throw new PickupNotAllowedException("다른 매장의 예약은 픽업 처리할 수 없어요.");
		}

		ReservationEntity reservation = reservationService.confirmPickup(pickupCode);

		StoreEntity store = reservationService.findStoreById(reservation.getStoreId()).orElse(null);

		model.addAttribute("pickupCode", reservation.getPickupCode());
		model.addAttribute("pickedAt", reservation.getPickedAt().format(DISPLAY_FORMAT));
		model.addAttribute("productName", reservation.getProductName());
		model.addAttribute("quantity", reservation.getReservedQuantity());
		model.addAttribute("totalPrice", reservation.getTotalPrice());
		model.addAttribute("storeName", store != null ? store.getStoreName() : "-");

		return "reservationView/pickupResult";
	}

	/**
	 * 영수증 PDF는 (Supabase가 설정돼 있으면) Supabase Storage 클라우드에 있다. Supabase가 아직 설정
	 * 안 된 상태라면 ReceiptService가 예전처럼 로컬 receipts/ 폴더에 저장해뒀을 거라, 그 경우엔 예전처럼
	 * 파일을 직접 읽어서 내려준다. (pdfUrl이 http(s)로 시작하는지 보고 두 경우를 구분한다.)
	 *
	 * 수정됨 (2026-09-08, 코드 감사) — 왜: Supabase 케이스에서 그 공개 URL로 그냥 302 리다이렉트만
	 * 시켜주고 있었다 — 파일명이 receipt-{reservationId}.pdf라 URL 패턴만 알면 아래 소유권 체크를
	 * 완전히 건너뛰고 순차 조회로 전체 영수증을 긁어갈 수 있었다(코드 감사에서 발견). 이제 서버가
	 * SupabaseStorageClient.downloadPdf()로 대신 받아서 바이트로 내려준다 — 브라우저는 Supabase URL을
	 * 아예 볼 일이 없어져서, 아래 소유권 체크가 실질적인 게이트가 된다. 업로드/저장 구조(publicUrl을
	 * DB에 그대로 저장해두고 재사용하는 방식)는 그대로 둔다.
	 *
	 * 영수증은 원래 결제 확인 시점(ReservationService.confirmPayment)에 한 번 만들어지고, 이후 취소/노쇼로 상태가
	 * 바뀌는 "그 순간"(cancelReservation/cancelByStore/processNoShows 안에서) 다시 만들어지기 때문에,
	 * 여기서는 보통 DB에 이미 있는 Receipt 레코드를 찾아서 URL만 꺼내 쓰면 된다.
	 * 예외적으로 sample-data.sql로 직접 넣어서 우리 코드를 한 번도 안 거친 예약처럼 Receipt 레코드
	 * 자체가 없는 경우에만, 그 자리에서 한 번 만들어준다.
	 */
	@GetMapping("/{id}/receipt")
	public ResponseEntity<?> receipt(@PathVariable Long id, HttpSession session) {
		ReservationEntity reservation = lookupService.getReservation(id);
		// 추가됨 (2026-09-01) — 본인 예약이 아니면 영수증도 못 보게 막는다 (complete/qrImage/cancel 참고).
		if (!reservation.getUserId().equals(ReservationController.resolveCurrentUserId(session))) {
			throw new ReservationAccessDeniedException("본인 예약의 영수증만 볼 수 있어요.");
		}

		ReceiptEntity receipt = receiptService.getOrGenerateReceipt(id);

		String pdfUrl = receipt.getPdfUrl();

		if (pdfUrl.startsWith("http://") || pdfUrl.startsWith("https://")) {
			byte[] pdf = supabaseStorageClient.downloadPdf(pdfUrl);
			return ResponseEntity.ok()
					.contentType(MediaType.APPLICATION_PDF)
					.header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=receipt-" + id + ".pdf")
					.body(pdf);
		}

		// Supabase 미설정 상태의 로컬 폴백: pdfUrl에 로컬 파일 경로가 그대로 들어있다.
		try {
			byte[] pdf = Files.readAllBytes(Path.of(pdfUrl));
			return ResponseEntity.ok()
					.contentType(MediaType.APPLICATION_PDF)
					.header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=receipt-" + id + ".pdf")
					.body(pdf);
		} catch (IOException e) {
			throw new EntityNotFoundException("영수증 파일을 찾을 수 없습니다. reservationId=" + id);
		}
	}
}

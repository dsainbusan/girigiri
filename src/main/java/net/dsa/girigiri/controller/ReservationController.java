package net.dsa.girigiri.controller;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.ReservationListItemDto;
import net.dsa.girigiri.domain.entity.ProductEntity;
import net.dsa.girigiri.domain.entity.ReservationEntity;
import net.dsa.girigiri.domain.entity.ReceiptEntity;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.repository.ProductRepository;
import net.dsa.girigiri.repository.ReceiptRepository;
import net.dsa.girigiri.repository.ReservationRepository;
import net.dsa.girigiri.repository.StoreRepository;
import net.dsa.girigiri.service.ReceiptService;
import net.dsa.girigiri.service.ReservationService;
import net.dsa.girigiri.util.QrCodeUtil;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 예약 확인 -> 예약 생성 -> 완료 화면(QR/영수증)까지 담당하는 컨트롤러.
 * ReservationService/ReceiptService(이미 만들어서 테스트해둔 부품)를 실제 화면 버튼과 연결하는 역할만 한다.
 *
 * 주의: 로그인이 아직 없어서, 지금은 임시로 sample-data.sql의 user id=1을 "로그인한 사용자"로
 *      취급한다 (resolveCurrentUserId() 참고). 나중에 로그인/세션이 만들어지면 이 메서드 안쪽만
 *      세션에서 실제 userId를 꺼내오도록 바꾸면 되고, 그 아래 로직(체크아웃/완료 화면)은 안 바뀐다.
 *
 * 주의2: 픽업 시간을 사용자가 직접 고르는 화면이 아직 없어서, 지금은 임시로 "예약 확정 시점부터
 *      2시간 후"로 고정한다. 픽업 시간 선택 UI가 생기면 그 값을 그대로 받아서 넘기도록 바꾸면 된다.
 */
@Controller
@RequestMapping("/reservation")
@RequiredArgsConstructor
public class ReservationController {

	private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("MM월 dd일 HH:mm");

	private final ReservationService reservationService;
	private final ReceiptService receiptService;
	private final ReservationRepository reservationRepository;
	private final ProductRepository productRepository;
	private final StoreRepository storeRepository;
	private final ReceiptRepository receiptRepository;

	// TODO(송보미 로그인 완료 후): 세션에서 실제 로그인한 userId를 꺼내오도록 교체
	private Long resolveCurrentUserId() {
		return 1L;
	}

	/** 예약하기 버튼을 누르면 오는 확인 화면: 상품 정보 + 결제 예정 금액을 보여준다. */
	@GetMapping("/checkout")
	public String checkout(@RequestParam Long productId,
							@RequestParam(defaultValue = "1") int quantity,
							Model model) {
		ProductEntity product = productRepository.findById(productId)
				.orElseThrow(() -> new EntityNotFoundException("상품을 찾을 수 없습니다. id=" + productId));

		model.addAttribute("productId", product.getId());
		model.addAttribute("name", product.getName());
		model.addAttribute("originalPrice", product.getOriginalPrice());
		model.addAttribute("discountedPrice", product.getDiscountedPrice());
		model.addAttribute("remainingQuantity", product.getRemainingQuantity());
		model.addAttribute("quantity", quantity);
		model.addAttribute("totalPrice", product.getDiscountedPrice() * quantity);

		return "reservationView/checkout";
	}

	/** "예약 확정하기" 버튼 제출: 실제 예약 생성(재고차감+QR+결제+영수증)이 여기서 전부 처리된다. */
	@PostMapping
	public String create(@RequestParam Long productId,
						  @RequestParam(defaultValue = "1") int quantity) {
		LocalDateTime pickupTime = LocalDateTime.now().plusHours(2);

		ReservationEntity reservation = reservationService.createReservation(
				resolveCurrentUserId(), productId, quantity, pickupTime);

		return "redirect:/reservation/" + reservation.getId() + "/complete";
	}

	/** 예약 완료 화면: QR 코드 + 픽업 코드 + 영수증 링크를 보여준다. */
	@GetMapping("/{id}/complete")
	public String complete(@PathVariable Long id, Model model) {
		ReservationEntity reservation = reservationRepository.findById(id)
				.orElseThrow(() -> new EntityNotFoundException("예약을 찾을 수 없습니다. id=" + id));
		ProductEntity product = productRepository.findById(reservation.getProductId())
				.orElseThrow(() -> new EntityNotFoundException("상품을 찾을 수 없습니다. id=" + reservation.getProductId()));
		StoreEntity store = storeRepository.findById(reservation.getStoreId())
				.orElseThrow(() -> new EntityNotFoundException("매장을 찾을 수 없습니다. id=" + reservation.getStoreId()));

		model.addAttribute("reservationId", reservation.getId());
		model.addAttribute("storeName", store.getStoreName());
		model.addAttribute("productName", product.getName());
		model.addAttribute("quantity", reservation.getReservedQuantity());
		model.addAttribute("totalPrice", reservation.getTotalPrice());
		model.addAttribute("pickupTime", reservation.getPickupTime().format(DISPLAY_FORMAT));
		model.addAttribute("pickupCode", reservation.getPickupCode());

		return "reservationView/complete";
	}

	/** 완료 화면의 <img> 태그가 실제로 부르는 QR 이미지 바이트. */
	@GetMapping("/{id}/qr-image")
	@ResponseBody
	public ResponseEntity<byte[]> qrImage(@PathVariable Long id) {
		ReservationEntity reservation = reservationRepository.findById(id)
				.orElseThrow(() -> new EntityNotFoundException("예약을 찾을 수 없습니다. id=" + id));
		try {
			byte[] png = QrCodeUtil.generateQrImage(reservation.getPickupCode(), 240);
			return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(png);
		} catch (Exception e) {
			throw new IllegalStateException("QR 이미지 생성에 실패했습니다. reservationId=" + id, e);
		}
	}

	/** 마이페이지 예약 목록: 진행중/픽업완료/노쇼·취소 3탭. */
	@GetMapping("/my")
	public String myReservations(@RequestParam(defaultValue = ReservationService.TAB_PROGRESS) String tab,
								  Model model) {
		List<ReservationListItemDto> reservations =
				reservationService.getMyReservations(resolveCurrentUserId(), tab);

		model.addAttribute("reservations", reservations);
		model.addAttribute("activeTab", tab);

		return "reservationView/myReservations";
	}

	/** "예약 취소" 버튼: 재고 복구 + 결제 취소 표시 + 상태 변경까지 ReservationService.cancelReservation 하나로 처리. */
	@PostMapping("/{id}/cancel")
	public String cancel(@PathVariable Long id) {
		reservationService.cancelReservation(id);
		return "redirect:/reservation/my?tab=cancelled";
	}

	/** 사장님이 재고 착오 등으로 예약을 취소해야 할 때 픽업 코드를 입력하는 화면. */
	@GetMapping("/store-cancel")
	public String storeCancelForm() {
		return "reservationView/storeCancel";
	}

	/** 매장 취소 처리: 시간 제한 없이 언제든 가능하고, ReservationService.cancelByStore가 환불 처리까지 담당한다. */
	@PostMapping("/store-cancel")
	public String storeCancel(@RequestParam String pickupCode,
							   @RequestParam(required = false) String reason,
							   Model model) {
		ReservationEntity target = reservationRepository.findByPickupCode(pickupCode)
				.orElseThrow(() -> new EntityNotFoundException("픽업 코드를 찾을 수 없습니다: " + pickupCode));

		ReservationEntity cancelled = reservationService.cancelByStore(target.getId(), reason);

		model.addAttribute("pickupCode", cancelled.getPickupCode());
		model.addAttribute("cancelReason", cancelled.getCancelReason());

		return "reservationView/storeCancelResult";
	}

	/** 사장님이 픽업 현장에서 픽업 코드를 입력하는 화면. */
	@GetMapping("/pickup")
	public String pickupForm() {
		return "reservationView/pickup";
	}

	/** 픽업 코드 확인 처리: ReservationService.confirmPickup 하나만 부르면 된다. */
	@PostMapping("/pickup")
	public String pickup(@RequestParam String pickupCode, Model model) {
		ReservationEntity reservation = reservationService.confirmPickup(pickupCode);

		model.addAttribute("pickupCode", reservation.getPickupCode());
		model.addAttribute("pickedAt", reservation.getPickedAt().format(DISPLAY_FORMAT));

		return "reservationView/pickupResult";
	}

	/**
	 * 영수증 PDF는 이제 (Supabase가 설정돼 있으면) Supabase Storage 클라우드에 있어서, 그 실제 URL로
	 * 리다이렉트만 시켜준다. Supabase가 아직 설정 안 된 상태라면 ReceiptService가 예전처럼 로컬
	 * receipts/ 폴더에 저장해뒀을 거라, 그 경우엔 예전처럼 파일을 직접 읽어서 내려준다.
	 * (pdfUrl이 http(s)로 시작하는지 보고 두 경우를 구분한다.)
	 *
	 * 영수증은 원래 예약 생성 시점(createReservation)에 한 번 만들어지고, 이후 취소/노쇼로 상태가
	 * 바뀌는 "그 순간"(cancelReservation/cancelByStore/processNoShows 안에서) 다시 만들어지기 때문에,
	 * 여기서는 보통 DB에 이미 있는 Receipt 레코드를 찾아서 URL만 꺼내 쓰면 된다.
	 * 예외적으로 sample-data.sql로 직접 넣어서 우리 코드를 한 번도 안 거친 예약처럼 Receipt 레코드
	 * 자체가 없는 경우에만, 그 자리에서 한 번 만들어준다.
	 */
	@GetMapping("/{id}/receipt")
	public ResponseEntity<?> receipt(@PathVariable Long id) {
		reservationRepository.findById(id)
				.orElseThrow(() -> new EntityNotFoundException("예약을 찾을 수 없습니다. id=" + id));

		ReceiptEntity receipt = receiptRepository.findByReservationId(id)
				.orElseGet(() -> receiptService.generateReceipt(id));

		String pdfUrl = receipt.getPdfUrl();

		if (pdfUrl.startsWith("http://") || pdfUrl.startsWith("https://")) {
			return ResponseEntity.status(302).location(URI.create(pdfUrl)).build();
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

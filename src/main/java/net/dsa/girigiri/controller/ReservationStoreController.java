package net.dsa.girigiri.controller;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.CancellableReservationDto;
import net.dsa.girigiri.domain.dto.PickupLookupResponseDto;
import net.dsa.girigiri.domain.entity.ReservationEntity;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.exception.CancellationNotAllowedException;
import net.dsa.girigiri.service.LookupService;
import net.dsa.girigiri.service.ReservationService;
import net.dsa.girigiri.service.StoreAccessService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * 점주용 예약 관리 화면 — 매장 취소(재고 착오 등으로 사장님이 직접 취소).
 * 2026-09-03 — 686줄이던 ReservationController에서 분리했다(레이어 규칙 정리, 도메인 분할).
 * 2026-09-08 — 이 파일이 다시 301줄까지 자라서(자체 300줄 분리 기준 초과, 코드 감사 LOW 항목)
 * "들어온 예약 확인/수락 + 완료 내역"은 ReservationIncomingController로, "픽업 설정"은
 * ReservationSettingsController로 옮기고 매장 취소만 여기 남겼다.
 * @RequestMapping("/reservation")은 그대로라 URL은 하나도 안 바뀐다.
 */
@Controller
@RequestMapping("/reservation")
@RequiredArgsConstructor
public class ReservationStoreController {

	private final ReservationService reservationService;
	private final LookupService lookupService;
	private final StoreAccessService storeAccessService;

	// 변경됨 — 왜: 매장별로 필터링하는 화면(완료된 거래 내역 등)에서 하드코딩된 store id=1이 실제
	// 로그인한 점주의 매장(예: id=2)과 안 맞아서 데이터가 안 보이는 문제가 있었다. StoreController가
	// 이미 쓰는 패턴(session.userId → storeRepository.findByOwnerId)과 동일하게 세션 기반으로 바꿨다.
	private Long resolveCurrentStoreId(HttpSession session) {
		Long userId = (Long) session.getAttribute("userId");
		return storeAccessService.getMyStore(userId).getId();
	}

	/**
	 * 사장님이 재고 착오 등으로 예약을 취소해야 할 때 쓰는 화면.
	 * (2026-08-21 변경) 픽업 코드를 직접 타이핑하는 대신, 지금 취소 가능한 예약 목록에서 골라
	 * 취소하도록 바꿨다 — 코드를 손으로 옮겨 적다 오타가 나거나, 이미 지나간 예약 번호를 잘못
	 * 입력하는 걸 막을 수 있다.
	 */
	@GetMapping("/store-cancel")
	public String storeCancelForm(HttpSession session, Model model) {
		List<CancellableReservationDto> cancellable = reservationService.getCancellableReservations(resolveCurrentStoreId(session));
		model.addAttribute("cancellable", cancellable);
		return "reservationView/storeCancel";
	}

	/**
	 * 매장 취소 화면에서, 픽업 코드를 입력하는 동안 어떤 예약(상품/수량/매장)을 취소하려는 건지
	 * 미리 보여주는 조회 전용 API. pickupLookup과 비슷하지만 "취소 가능 상태" 기준이 다르다 —
	 * 픽업은 ready 상태만 가능하지만, 매장 취소는 ReservationService.checkCancellableState와
	 * 동일하게 pending/confirmed/ready 다 가능하다 (손님이 아직 픽업 전이면 매장 수락 여부와 상관없이
	 * 언제든 매장이 취소 가능).
	 */
	@GetMapping("/store-cancel/lookup")
	@ResponseBody
	public PickupLookupResponseDto storeCancelLookup(@RequestParam String pickupCode) {
		ReservationEntity reservation = reservationService.findByPickupCode(pickupCode).orElse(null);
		if (reservation == null) {
			return PickupLookupResponseDto.notFound();
		}

		// 변경됨 (2026-09-08, 코드 감사) — "취소 가능 상태" 판정을 여기 로컬 switch 대신
		// ReservationService.blockedCancelMessage로 통일(4곳 중복 정리).
		String blockedMessage = reservationService.blockedCancelMessage(reservation);
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
	 * 매장 취소 처리: 시간 제한 없이 언제든 가능하고, ReservationService.cancelByStore가 환불 처리까지 담당한다.
	 * 결과 화면에서 "뭘 취소한 건지" 바로 보이게, 상품/매장 정보도 같이 조회해서 넘긴다.
	 *
	 * 추가됨 — 왜: pickupCode로만 예약을 찾아서, 다른 매장의 픽업 코드를 알기만 하면(또는 목록
	 * 필터링 버그로 노출됐던 다른 매장 코드로) 취소시킬 수 있는 구멍이 있었다. 로그인한 점주의
	 * 매장 소유가 아니면 막는다.
	 */
	@PostMapping("/store-cancel")
	public String storeCancel(@RequestParam String pickupCode,
							   @RequestParam(required = false) String reason,
							   HttpSession session,
							   Model model) {
		ReservationEntity target = reservationService.findByPickupCode(pickupCode)
				.orElseThrow(() -> new EntityNotFoundException("픽업 코드를 찾을 수 없습니다: " + pickupCode));

		if (!target.getStoreId().equals(resolveCurrentStoreId(session))) {
			throw new CancellationNotAllowedException("다른 매장의 예약은 취소할 수 없어요.");
		}

		ReservationEntity cancelled = reservationService.cancelByStore(target.getId(), reason);

		StoreEntity store = reservationService.findStoreById(cancelled.getStoreId()).orElse(null);

		model.addAttribute("pickupCode", cancelled.getPickupCode());
		model.addAttribute("cancelReason", cancelled.getCancelReason());
		model.addAttribute("productName", cancelled.getProductName());
		model.addAttribute("quantity", cancelled.getReservedQuantity());
		model.addAttribute("totalPrice", cancelled.getTotalPrice());
		model.addAttribute("storeName", store != null ? store.getStoreName() : "-");

		return "reservationView/storeCancelResult";
	}

	/**
	 * 예약 확인 화면에서 바로 누르는 "취소" 버튼. (2026-08-21 추가 — 상의 후 결정: 예약 확인 화면과
	 * 매장 취소 화면은 성격이 달라서 계속 따로 두되, 새 주문이 들어온 그 자리에서 바로 거절도 할 수
	 * 있게 이 버튼만 추가했다.) 실제 취소 처리는 매장 취소 화면과 동일하게 cancelByStore를 그대로
	 * 재사용한다 — 재고 복구/환불 표시/상태 변경 로직을 중복 작성하지 않기 위함.
	 *
	 * 추가됨 — 왜: id만 받아서 다른 매장 예약도 취소시킬 수 있는 구멍이 있었다. 로그인한 점주의
	 * 매장 소유가 아니면 막는다.
	 */
	@PostMapping("/{id}/store-cancel")
	public String storeCancelById(@PathVariable Long id,
								   @RequestParam(required = false) String reason,
								   HttpSession session,
								   RedirectAttributes redirectAttributes) {
		ReservationEntity reservation = lookupService.getReservation(id);
		if (!reservation.getStoreId().equals(resolveCurrentStoreId(session))) {
			throw new CancellationNotAllowedException("다른 매장의 예약은 취소할 수 없어요.");
		}

		reservationService.cancelByStore(id, reason);
		redirectAttributes.addFlashAttribute("cancelledMessage", "예약을 취소했어요. 결제하신 금액은 환불됩니다.");
		return "redirect:/reservation/incoming";
	}
}

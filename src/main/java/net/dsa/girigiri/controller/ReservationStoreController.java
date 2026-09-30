package net.dsa.girigiri.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.entity.ReservationEntity;
import net.dsa.girigiri.exception.CancellationNotAllowedException;
import net.dsa.girigiri.service.LookupService;
import net.dsa.girigiri.service.ReservationService;
import net.dsa.girigiri.service.StoreAccessService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 점주용 예약 관리 화면 — 매장 취소(재고 착오 등으로 사장님이 직접 취소).
 * 2026-09-03 — 686줄이던 ReservationController에서 분리했다(레이어 규칙 정리, 도메인 분할).
 * 2026-09-08 — 이 파일이 다시 301줄까지 자라서(자체 300줄 분리 기준 초과, 코드 감사 LOW 항목)
 * "들어온 예약 확인/수락 + 완료 내역"은 ReservationIncomingController로, "픽업 설정"은
 * ReservationSettingsController로 옮기고 매장 취소만 여기 남겼다.
 * @RequestMapping("/reservation")은 그대로라 URL은 하나도 안 바뀐다.
 *
 * 변경됨 (2026-09-30) — 왜: 픽업코드를 직접 입력해서 취소하는 독립 화면(GET/POST /store-cancel,
 * GET /store-cancel/lookup)을 지웠다. 어느 화면에서도 링크가 안 걸려 있던 고아 화면이었고(코드
 * 감사에서 발견), "예약 확인" 화면(새 주문/픽업 대기중 두 탭 다)의 취소 버튼이 아래 storeCancelById
 * (/{id}/store-cancel)로 같은 목적(수락 여부 무관 매장 취소)을 이미 커버해서 중복이었다.
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

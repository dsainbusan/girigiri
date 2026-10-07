package net.dsa.girigiri.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.ReservationAllOrderRowDto;
import net.dsa.girigiri.domain.dto.ReservationDetailDto;
import net.dsa.girigiri.domain.entity.ReservationEntity;
import net.dsa.girigiri.service.AdminRefundService;
import net.dsa.girigiri.service.LookupService;
import net.dsa.girigiri.service.ReservationService;
import net.dsa.girigiri.service.SuperAdminSupportService;
import net.dsa.girigiri.util.PaginationUtil;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * 추가됨 (2026-09-09) — 예약(주문) 관련 슈퍼어드민 화면 두 개를 다룬다.
 *
 * 1) "매장 주문 내역"(storeOrders.html)·"회원 예약 내역"(memberReservations.html) 표에서 행을
 *    클릭하면 들어오는 예약 상세 — 두 목록이 같은 ReservationEntity를 가리키는 조회 전용 상세라
 *    화면도 하나만 두고, 어디서 들어왔는지는 ?from=store|member|orders 로 구분해서 "← 목록" 링크만
 *    다르게 보여준다(예상 밖 값이면 store 기준으로 기본 처리).
 * 2) "전체 주문 내역"(orders.html, 2026-09-09 추가) — 특정 매장/회원으로 좁히지 않은 플랫폼 전체
 *    예약 목록. reports.html/SuperAdminSupportController와 동일한 page 파라미터 + PaginationUtil
 *    페이지 자르기 패턴.
 *
 * 클래스 매핑을 /superadmin/reservations가 아니라 /superadmin으로 두고 메서드마다 전체 경로를
 * 써서(SuperAdminStoreController와 동일 스타일), /superadmin/orders와 /superadmin/reservations/{id}
 * 두 서로 다른 하위 경로를 한 클래스에서 자연스럽게 다룬다.
 *
 * 추가됨 (2026-10-06) — "/superadmin/orders엔 취소·환불 로직이 없다"는 지적으로 주문 상세에
 * 취소(cancel)·환불(refund) 액션을 추가했다. 지금까지 ReservationService.cancelByAdmin/
 * AdminRefundService.refund는 전부 "신고 상세"에서만 호출 가능했는데, 신고가 아예 안 들어온
 * 주문도 운영자가 직접 처리할 수 있어야 한다는 요구 — cancelByAdmin은 원래도 신고 의존이 없어서
 * 그대로 재사용, AdminRefundService는 신고 없이 쓸 수 있는 refundDirect()를 새로 추가해서 썼다.
 */
@Controller
@RequestMapping("/superadmin")
@RequiredArgsConstructor
public class SuperAdminReservationController {

	private static final int PAGE_SIZE = 10;

	private final LookupService lookupService;
	private final ReservationService reservationService;
	private final AdminRefundService adminRefundService;
	private final SuperAdminSupportService supportService;

	@GetMapping("/reservations/{id}")
	public String reservationDetail(@PathVariable Long id,
	                                 @RequestParam(required = false) String from,
	                                 Model model) {
		ReservationEntity reservation = lookupService.getReservation(id);
		ReservationDetailDto detail = reservationService.getReservationDetail(reservation);

		model.addAttribute("r", detail);
		model.addAttribute("from", "member".equals(from) ? "member" : ("orders".equals(from) ? "orders" : "store"));

		// 추가됨 (2026-10-06) — 신고 없이도 주문 상세에서 바로 취소/환불할 수 있게. 픽업 전(pending/
		// confirmed/ready)이면 취소 버튼, 픽업완료(picked)면 환불 버튼, 그 외(이미 취소·노쇼·환불)면
		// 버튼 없이 안내 배지만 보여준다 — blockedCancelMessage/blockedRefundMessage 둘 다 null이면
		// "지금 가능하다"는 뜻이라 두 분기가 절대 동시에 열리지 않는다(picked만 환불 가능, picked가
		// 아니어야 취소 가능이라 서로 배타적).
		model.addAttribute("blockedCancelMessage", reservationService.blockedCancelMessage(reservation));
		model.addAttribute("blockedRefundMessage", adminRefundService.blockedRefundMessage(reservation));
		adminRefundService.findRefund(id).ifPresent(refund -> {
			model.addAttribute("refundInfo", refund);
			model.addAttribute("refundProcessedByName", supportService.adminDisplayName(refund.getRequestedBy()));
		});

		return "superAdminView/reservationDetail";
	}

	/** 아직 픽업 전(pending/confirmed/ready)인 주문을 신고 없이 바로 취소 — ReservationService.cancelByAdmin 재사용. */
	@PostMapping("/reservations/{id}/cancel")
	public String cancelOrder(@PathVariable Long id,
	                           @RequestParam(required = false) String reason,
	                           @RequestParam(required = false) String from,
	                           RedirectAttributes redirectAttributes) {
		reservationService.cancelByAdmin(id, reason);
		redirectAttributes.addFlashAttribute("actionMessage", "주문을 취소하고 환불 처리했어요.");
		return "redirect:/superadmin/reservations/" + id + (from != null ? "?from=" + from : "");
	}

	/** 픽업 완료(picked)된 주문을 신고 없이 바로 환불 — AdminRefundService.refundDirect 재사용. */
	@PostMapping("/reservations/{id}/refund")
	public String refundOrder(@PathVariable Long id,
	                           @RequestParam String reason,
	                           @RequestParam(required = false) String from,
	                           HttpSession session,
	                           RedirectAttributes redirectAttributes) {
		Long adminId = (Long) session.getAttribute("userId");
		AdminRefundService.RefundResult result = adminRefundService.refundDirect(id, reason, adminId);

		redirectAttributes.addFlashAttribute("actionMessage", result.success()
				? "환불 처리됐어요."
				: "환불에 실패했어요: " + result.failReason());
		return "redirect:/superadmin/reservations/" + id + (from != null ? "?from=" + from : "");
	}

	/**
	 * 추가됨 (2026-09-09) — "전체 오더 볼 수 있는 페이지" 요청으로 신설. statusTab(pending/progress/
	 * picked/cancelled, 없으면 전체)·q(매장명·주문자 검색)로 거른 다음 최신순으로 10건씩 페이지네이션.
	 */
	@GetMapping("/orders")
	public String orders(@RequestParam(required = false) String status,
	                      @RequestParam(required = false) String q,
	                      @RequestParam(defaultValue = "0") int page,
	                      Model model) {
		List<ReservationAllOrderRowDto> all = reservationService.getAllOrders(status, q);

		model.addAttribute("orders", PaginationUtil.paginate(all, page, PAGE_SIZE));
		model.addAttribute("totalPages", PaginationUtil.totalPages(all.size(), PAGE_SIZE));
		model.addAttribute("totalCount", all.size());
		model.addAttribute("status", status);
		model.addAttribute("q", q);
		model.addAttribute("page", page);
		return "superAdminView/orders";
	}
}

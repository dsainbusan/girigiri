package net.dsa.girigiri.controller;

import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.ReservationAllOrderRowDto;
import net.dsa.girigiri.domain.dto.ReservationDetailDto;
import net.dsa.girigiri.domain.entity.ReservationEntity;
import net.dsa.girigiri.service.LookupService;
import net.dsa.girigiri.service.ReservationService;
import net.dsa.girigiri.util.PaginationUtil;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

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
 */
@Controller
@RequestMapping("/superadmin")
@RequiredArgsConstructor
public class SuperAdminReservationController {

	private static final int PAGE_SIZE = 10;

	private final LookupService lookupService;
	private final ReservationService reservationService;

	@GetMapping("/reservations/{id}")
	public String reservationDetail(@PathVariable Long id,
	                                 @RequestParam(required = false) String from,
	                                 Model model) {
		ReservationEntity reservation = lookupService.getReservation(id);
		ReservationDetailDto detail = reservationService.getReservationDetail(reservation);

		model.addAttribute("r", detail);
		model.addAttribute("from", "member".equals(from) ? "member" : ("orders".equals(from) ? "orders" : "store"));
		return "superAdminView/reservationDetail";
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

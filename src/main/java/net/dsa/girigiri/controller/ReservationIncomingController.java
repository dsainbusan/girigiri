package net.dsa.girigiri.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.ReservationCompletedItemDto;
import net.dsa.girigiri.domain.dto.ReservationIncomingItemDto;
import net.dsa.girigiri.domain.dto.ReservationOrderItemDto;
import net.dsa.girigiri.domain.entity.ReservationEntity;
import net.dsa.girigiri.exception.AcceptNotAllowedException;
import net.dsa.girigiri.service.LookupService;
import net.dsa.girigiri.service.ReservationService;
import net.dsa.girigiri.service.StoreAccessService;
import net.dsa.girigiri.util.PaginationUtil;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 점주용 "들어온 예약 확인/수락" + "완료된 거래 내역" 화면.
 * 2026-09-08 — ReservationStoreController(301줄, 자체 300줄 분리 기준 초과)에서 분리했다(코드 감사
 * LOW 항목 대응). 매장 취소(store-cancel)는 ReservationStoreController에 남기고, 픽업 설정은
 * ReservationSettingsController로 분리 — 3개 다 @RequestMapping("/reservation")은 그대로라
 * URL은 하나도 안 바뀐다.
 */
@Controller
@RequestMapping("/reservation")
@RequiredArgsConstructor
public class ReservationIncomingController {

	private final ReservationService reservationService;
	private final LookupService lookupService;
	private final StoreAccessService storeAccessService;

	// 추가됨 (2026-09-28) — 목록이 수십 건 쌓이면 한 화면에 다 나와서 스크롤이 끝없이 길어진다는
	// 피드백. 카드 한 장이 꽤 큰 편이라 리뷰 관리(StoreReviewService.PAGE_SIZE)와 같은 5개로 잡는다.
	private static final int LIST_PAGE_SIZE = 5;
	private static final int LIST_PAGE_WINDOW = 7;

	/**
	 * 목록 화면 공통 페이징 — 화면에 뿌릴 page/totalPages/번호 윈도우를 model에 담고, 범위를
	 * 보정한 현재 페이지를 돌려준다(호출부에서 PaginationUtil.paginate에 그대로 넘기면 된다).
	 * 번호 윈도우: 앱 최대폭(var(--app-max))이 페이지가 열 개를 넘으면 번호를 전부 나열할 수
	 * 없을 만큼은 좁아서 현재 페이지 주변 몇 개만 그린다. (2026-09-28 — 앱 최대폭을 420→600px로
	 * 넓히면서 한 줄에 더 들어갈 여유가 생겨 5개→7개로 늘림. .pagination 항목(store.css)이
	 * min-width 32px+gap 4px라 화살표 2개+숫자 7개 기준 약 248px로, 600px 컨테이너(좌우 패딩
	 * 제외 시 약 568px 가용폭)에 여유 있게 들어간다.)
	 */
	private int applyPaging(Model model, int page, int totalItems) {
		int totalPages = PaginationUtil.totalPages(totalItems, LIST_PAGE_SIZE);
		int safePage = Math.max(0, Math.min(page, totalPages - 1));
		int windowStart = Math.max(0, Math.min(safePage - 2, totalPages - LIST_PAGE_WINDOW));

		model.addAttribute("page", safePage);
		model.addAttribute("totalPages", totalPages);
		model.addAttribute("pageWindowStart", windowStart);
		model.addAttribute("pageWindowEnd", Math.min(totalPages - 1, windowStart + LIST_PAGE_WINDOW - 1));
		return safePage;
	}

	private Long resolveCurrentStoreId(HttpSession session) {
		Long userId = (Long) session.getAttribute("userId");
		return storeAccessService.getMyStore(userId).getId();
	}

	/**
	 * 사장님용 "들어온 예약 확인" 화면: 결제 완료됐지만 아직 매장이 수락 안 한(confirmed) 주문 목록.
	 * (2026-08-21 추가) 매장이 여기서 "수락" 버튼을 눌러야(ready로 전환) 손님이 픽업하러 올 수 있다.
	 */
	@GetMapping("/incoming")
	public String incoming(HttpSession session, Model model) {
		Long storeId = resolveCurrentStoreId(session);
		List<ReservationIncomingItemDto> incoming = reservationService.getIncomingReservations(storeId);
		model.addAttribute("incoming", incoming);

		// 추가됨 — 왜: 수락(ready)까지는 됐는데 손님이 아직 QR/코드를 안 보여줘서 픽업 처리가 안 된
		// 예약을 확인할 방법이 없었다. "확인할 새 주문" 목록 화면에 자연스럽게 이어 붙인다.
		model.addAttribute("ready", reservationService.getReadyReservations(storeId));

		return "reservationView/incoming";
	}

	/**
	 * 추가됨 — 왜: 손님이 실제로 픽업해서 거래가 끝난 내역을 점주가 볼 화면이 없었다.
	 * 변경됨 (2026-08-30, 문창호) — 왜: 내역이 쌓이면 찾기 힘들어서 날짜별 그룹 + 날짜/시간대 필터 추가.
	 *   date=YYYY-MM-DD, from/to=HH:mm (그 날의 시간대). 값이 이상하면 무시하고 전체를 보여준다.
	 */
	@GetMapping("/completed")
	public String completed(@RequestParam(required = false) String date,
	                        @RequestParam(required = false) String from,
	                        @RequestParam(required = false) String to,
	                        @RequestParam(defaultValue = "0") int page,
	                        HttpSession session, Model model) {
		LocalDate d = parseLocalDate(date);
		LocalTime f = parseLocalTime(from);
		LocalTime t = parseLocalTime(to);

		List<ReservationCompletedItemDto> items =
				reservationService.getCompletedTransactions(resolveCurrentStoreId(session), d, f, t);

		// 건수·합계는 필터에 걸린 전체 기준이고, 목록만 페이지 단위로 자른다.
		int safePage = applyPaging(model, page, items.size());

		// 날짜별 그룹 (서비스가 pickedAt DESC로 넘겨줘서 최신 날짜가 먼저 들어온다). 이미 정렬된
		// 리스트를 자른 뒤에 묶으므로 페이지 안에서도 날짜 순서가 그대로 유지된다.
		Map<String, List<ReservationCompletedItemDto>> groups = new LinkedHashMap<>();
		for (ReservationCompletedItemDto it : PaginationUtil.paginate(items, safePage, LIST_PAGE_SIZE)) {
			groups.computeIfAbsent(it.pickedDate(), k -> new ArrayList<>()).add(it);
		}

		model.addAttribute("groups", groups);
		model.addAttribute("totalCount", items.size());
		model.addAttribute("totalAmount", items.stream().mapToLong(ReservationCompletedItemDto::totalPrice).sum());
		model.addAttribute("filterDate", date == null ? "" : date);
		model.addAttribute("filterFrom", from == null ? "" : from);
		model.addAttribute("filterTo", to == null ? "" : to);
		model.addAttribute("filterActive", d != null || f != null || t != null);
		return "reservationView/completed";
	}

	/**
	 * 추가됨 (2026-09-15) — 마이페이지 "내 매장 신뢰도" 카드에서 취소율만 보여주고 그 뒤의 예약
	 * 목록으로는 못 들어갔다는 요청으로 추가. 상태(대기/확정/픽업가능/픽업완료/취소/노쇼)와 무관하게
	 * 지금까지의 전체 예약을 최신순으로 보여준다 — 슈퍼어드민 "매장 주문 내역"과 같은 계산
	 * (ReservationService.getOrdersForStore)을 그대로 재사용한다.
	 */
	@GetMapping("/orders")
	public String orders(@RequestParam(defaultValue = "0") int page, HttpSession session, Model model) {
		Long storeId = resolveCurrentStoreId(session);
		List<ReservationOrderItemDto> orders = reservationService.getOrdersForStore(storeId);

		int safePage = applyPaging(model, page, orders.size());
		model.addAttribute("orders", PaginationUtil.paginate(orders, safePage, LIST_PAGE_SIZE));
		model.addAttribute("totalCount", orders.size());
		model.addAttribute("storeReliability", reservationService.getStoreCancelStats(storeId));
		return "reservationView/orders";
	}

	private LocalDate parseLocalDate(String s) {
		try {
			return (s == null || s.isBlank()) ? null : LocalDate.parse(s.trim());
		} catch (Exception e) {
			return null;
		}
	}

	private LocalTime parseLocalTime(String s) {
		try {
			return (s == null || s.isBlank()) ? null : LocalTime.parse(s.trim());
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * "수락" 버튼 제출: confirmed -> ready로 전환한다. 이후부터 픽업 화면에서 이 예약을 처리할 수 있다.
	 * 추가됨 — 왜: id만 받아서 다른 매장 예약도 수락시킬 수 있는 구멍이 있었다. 로그인한 점주의
	 * 매장 소유가 아니면 막는다.
	 */
	@PostMapping("/{id}/accept")
	public String accept(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
		ReservationEntity reservation = lookupService.getReservation(id);
		if (!reservation.getStoreId().equals(resolveCurrentStoreId(session))) {
			throw new AcceptNotAllowedException("다른 매장의 예약은 수락할 수 없어요.");
		}

		reservationService.acceptReservation(id);
		redirectAttributes.addFlashAttribute("acceptedMessage", "예약을 확인했어요. 이제 손님이 픽업하러 올 수 있어요.");
		return "redirect:/reservation/incoming";
	}
}

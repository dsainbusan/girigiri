package net.dsa.girigiri.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.ReservationCompletedItemDto;
import net.dsa.girigiri.domain.dto.ReservationIncomingItemDto;
import net.dsa.girigiri.domain.entity.ReservationEntity;
import net.dsa.girigiri.exception.AcceptNotAllowedException;
import net.dsa.girigiri.service.LookupService;
import net.dsa.girigiri.service.ReservationService;
import net.dsa.girigiri.service.StoreAccessService;
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
	                        HttpSession session, Model model) {
		LocalDate d = parseLocalDate(date);
		LocalTime f = parseLocalTime(from);
		LocalTime t = parseLocalTime(to);

		List<ReservationCompletedItemDto> items =
				reservationService.getCompletedTransactions(resolveCurrentStoreId(session), d, f, t);

		// 날짜별 그룹 (서비스가 pickedAt DESC로 넘겨줘서 최신 날짜가 먼저 들어온다)
		Map<String, List<ReservationCompletedItemDto>> groups = new LinkedHashMap<>();
		for (ReservationCompletedItemDto it : items) {
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

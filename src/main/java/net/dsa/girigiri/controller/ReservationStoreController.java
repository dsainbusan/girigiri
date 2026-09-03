package net.dsa.girigiri.controller;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.CancellableReservationDto;
import net.dsa.girigiri.domain.dto.PickupLookupResponseDto;
import net.dsa.girigiri.domain.dto.ReservationCompletedItemDto;
import net.dsa.girigiri.domain.dto.ReservationIncomingItemDto;
import net.dsa.girigiri.domain.entity.ReservationEntity;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.exception.AcceptNotAllowedException;
import net.dsa.girigiri.exception.CancellationNotAllowedException;
import net.dsa.girigiri.service.LookupService;
import net.dsa.girigiri.service.ReservationService;
import net.dsa.girigiri.service.StoreAccessService;
import net.dsa.girigiri.util.OperatingHoursUtil;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 점주용 예약 관리 화면 — 들어온 예약 확인/수락, 완료된 거래 내역, 매장 취소, 픽업 설정.
 * 2026-09-03 — 686줄이던 ReservationController에서 분리했다(레이어 규칙 정리, 도메인 분할).
 * @RequestMapping("/reservation")은 그대로라 URL은 하나도 안 바뀐다.
 */
@Controller
@RequestMapping("/reservation")
@RequiredArgsConstructor
public class ReservationStoreController {

	// 매장이 아직 prepTimeMinutes를 설정 안 했을 때 쓰는 기본 준비시간 (StoreEntity 기본값과 동일하게 맞춤)
	private static final int DEFAULT_PREP_TIME_MINUTES = 20;

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

		String blockedMessage = switch (reservation.getStatus()) {
			case "picked" -> "이미 픽업 완료된 예약은 취소할 수 없어요.";
			case "cancelled" -> "이미 취소된 예약이에요.";
			case "noshowed" -> "이미 노쇼 처리된 예약이라 취소할 수 없어요.";
			default -> null;   // "pending", "confirmed", "ready"만 정상 진행
		};
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

	// 수정됨 (2026-08-24, 점검/정리) — 왜: 여기 직접 만들었던 정규식 기반 파서가
	// ReservationService.isTooCloseToClosing()이 이미 쓰고 있던 net.dsa.girigiri.util.OperatingHoursUtil의
	// parseClosingTime과 로직이 겹치면서(중복), 실패 시 동작도 서로 달랐다(이쪽은 null 반환, 저쪽은
	// IllegalArgumentException 발생) — 같은 "영업시간 문자열 파싱"을 두 곳에서 다르게 하고 있던 셈이라
	// 한쪽만 고치면 다른 쪽은 안 고쳐지는 버그가 나기 쉬웠다. OperatingHoursUtil을 유일한 파서로 쓰고,
	// 여기서는 그 예외를 잡아서 이 화면이 원래 기대하던 "파싱 실패 시 null(= 이 옵션 숨김)" 동작만 감싸준다.
	private LocalTime parseClosingTime(String operatingHours) {
		try {
			return OperatingHoursUtil.parseClosingTime(operatingHours);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/**
	 * 매장이 "준비 시간"/"마지막 픽업 시간"을 직접 설정하는 화면. (2026-08-21 추가, 이후 영업종료시간
	 * 옵션 추가) 이 값들이 PickupAvailabilityUtil의 자동계산(체크아웃 화면 "예상 픽업 가능 시각") 기준이
	 * 된다. 아직 설정 안 한 매장(마지막 픽업시간 NULL)은 "제한 없음"으로 취급된다 — StoreEntity 주석 참고.
	 */
	@GetMapping("/settings")
	public String settingsForm(HttpSession session, Model model) {
		StoreEntity store = lookupService.getStore(resolveCurrentStoreId(session));

		LocalTime closingTime = parseClosingTime(store.getOperatingHours());

		String pickupTimeMode;
		if (store.getLastPickupTime() == null) {
			pickupTimeMode = "unlimited";
		} else if (closingTime != null && closingTime.equals(store.getLastPickupTime())) {
			pickupTimeMode = "close";
		} else {
			pickupTimeMode = "manual";
		}

		model.addAttribute("storeName", store.getStoreName());
		model.addAttribute("prepTimeMinutes",
				store.getPrepTimeMinutes() != null ? store.getPrepTimeMinutes() : DEFAULT_PREP_TIME_MINUTES);
		model.addAttribute("lastPickupTime", store.getLastPickupTime() != null ? store.getLastPickupTime().toString() : "");
		model.addAttribute("operatingHours", store.getOperatingHours());
		model.addAttribute("closingTimeDisplay", closingTime != null ? closingTime.toString() : null);
		model.addAttribute("pickupTimeMode", pickupTimeMode);
		return "reservationView/pickupSettings";
	}

	/**
	 * "저장" 버튼 제출. pickupTimeMode: "manual"(직접 입력한 lastPickupTime 사용) /
	 * "close"(영업 종료 시간을 매번 다시 계산해서 사용 — operatingHours가 나중에 바뀌어도 따라간다) /
	 * "unlimited"(제한 없음, NULL 저장).
	 */
	@PostMapping("/settings")
	public String saveSettings(@RequestParam int prepTimeMinutes,
								@RequestParam(required = false) String lastPickupTime,
								@RequestParam(defaultValue = "manual") String pickupTimeMode,
								HttpSession session,
								RedirectAttributes redirectAttributes) {
		StoreEntity store = lookupService.getStore(resolveCurrentStoreId(session));

		reservationService.updatePickupSettings(store, prepTimeMinutes, lastPickupTime, pickupTimeMode);
		redirectAttributes.addFlashAttribute("savedMessage", "주문 마감 설정을 저장했어요.");
		return "redirect:/reservation/settings";
	}
}

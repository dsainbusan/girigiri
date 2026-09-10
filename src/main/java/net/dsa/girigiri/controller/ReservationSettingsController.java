package net.dsa.girigiri.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.service.LookupService;
import net.dsa.girigiri.service.ReservationService;
import net.dsa.girigiri.service.StoreAccessService;
import net.dsa.girigiri.util.OperatingHoursUtil;
import net.dsa.girigiri.util.PickupAvailabilityUtil;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalTime;

/**
 * 매장이 "준비 시간"/"마지막 픽업 시간"을 설정하는 화면.
 * 2026-09-08 — ReservationStoreController(301줄, 자체 300줄 분리 기준 초과)에서 분리했다(코드 감사
 * LOW 항목 대응). @RequestMapping("/reservation")은 그대로라 URL은 하나도 안 바뀐다.
 */
@Controller
@RequestMapping("/reservation")
@RequiredArgsConstructor
public class ReservationSettingsController {

	private final ReservationService reservationService;
	private final LookupService lookupService;
	private final StoreAccessService storeAccessService;

	private Long resolveCurrentStoreId(HttpSession session) {
		Long userId = (Long) session.getAttribute("userId");
		return storeAccessService.getMyStore(userId).getId();
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
				store.getPrepTimeMinutes() != null ? store.getPrepTimeMinutes() : PickupAvailabilityUtil.DEFAULT_PREP_TIME_MINUTES);
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

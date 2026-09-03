package net.dsa.girigiri.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.StoreDashboardStatsDto;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.service.StoreAccessService;
import net.dsa.girigiri.service.StoreService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 점주 대시보드 (WBS 3.0 매장 운영 — 문창호 담당: POS json 연동, 할인율 자동계산, 판매/등록 현황
 * 대시보드, 통계 그래프, 판매/폐기 리포트).
 *
 * 변경됨 (2026-08-21) — 왜: 목업 껍데기 단계를 지나 ProductRepository/ReservationRepository
 * 실데이터로 교체했다.
 * - 등록/판매중/폐기/구제율/오늘매출은 ProductEntity(registeredAt, status, quantity,
 *   remainingQuantity, discountedPrice)만으로 계산 — 새 컬럼 없이 지금 있는 필드로 충분했다.
 *   구제율 = 판매÷등록 (CLAUDE.md 리포트 스펙 그대로).
 * - 픽업 예약(대기/완료)은 ProductEntity엔 없는 개념이라 ReservationRepository로 별도 집계.
 * - "어제 대비" 매출 증감은 다음 단계 TODO — 지금 샘플 데이터가 전부 NOW() 타임스탬프라 어제
 *   데이터가 없어서, 가짜 값을 보여주느니 빈 값으로 둔다.
 *
 * 2026-09-03 — 373줄이던 이 클래스에서 판매·폐기 리포트/정산(report/settlement) 6개 엔드포인트를
 * StoreReportController로 분리했다(레이어 규칙 정리, 도메인 분할). 이 클래스는 대시보드·구제율
 * 목표·매장 정보 수정만 남는다. @RequestMapping("/store")은 그대로라 URL은 하나도 안 바뀐다.
 */
@Controller
@RequestMapping("/store")
@RequiredArgsConstructor
public class StoreController {

	private final StoreAccessService storeAccessService;
	private final StoreService storeService;

	@Value("${kakao.map.js-key}")
	private String kakaoMapJsKey;

	@GetMapping("/dashboard")
	public String dashboard(HttpSession session, Model model) {
		Long userId = (Long) session.getAttribute("userId");
		if (userId == null) {
			return "redirect:/auth/loginForm";
		}

		StoreEntity store = storeAccessService.findMyStore(userId).orElse(null);
		model.addAttribute("storeName", store != null ? store.getStoreName() : "매장 정보 없음");
		model.addAttribute("todayLabel", LocalDate.now().format(
				DateTimeFormatter.ofPattern("M월 d일 EEEE", java.util.Locale.KOREAN)));

		StoreDashboardStatsDto stats = store == null
				? storeService.emptyDashboardStats()
				: storeService.buildDashboardStats(store);

		model.addAttribute("todaySales", stats.todaySales());
		model.addAttribute("salesDelta", stats.salesDelta());
		model.addAttribute("salesDeltaClass", stats.salesDeltaClass());
		model.addAttribute("soldCount", stats.soldCount());
		model.addAttribute("registeredCount", stats.registeredCount());
		model.addAttribute("sellingNowCount", stats.sellingNowCount());
		model.addAttribute("reservationCount", stats.reservationCount());
		model.addAttribute("reservationWaiting", stats.reservationWaiting());
		model.addAttribute("reservationDone", stats.reservationDone());
		model.addAttribute("reservationCancelled", stats.reservationCancelled());
		model.addAttribute("expiredCount", stats.expiredCount());
		model.addAttribute("rescueRate", stats.rescueRate());
		model.addAttribute("rescueGoalPercent", stats.rescueGoalPercent());
		model.addAttribute("rescueGoal", stats.rescueGoal());

		model.addAttribute("totalQuantity", stats.totalQuantity());
		model.addAttribute("pickedCount", stats.pickedCount());
		model.addAttribute("reservedNotPickedCount", stats.reservedNotPickedCount());
		model.addAttribute("idleCount", stats.idleCount());
		model.addAttribute("isClosed", stats.isClosed());
		model.addAttribute("closingCountdownLabel", stats.closingCountdownLabel());
		model.addAttribute("donutPickedPct", stats.donutPickedPct());
		model.addAttribute("donutReservedCumPct", stats.donutReservedCumPct());
		model.addAttribute("donutSoldCumPct", stats.donutSoldCumPct());

		model.addAttribute("weeklySalesBars", stats.weeklySalesBars());
		model.addAttribute("weeklySoldTotal", stats.weeklySoldTotal());
		model.addAttribute("weeklyWasteTotal", stats.weeklyWasteTotal());

		model.addAttribute("weeklyRescuedCount", stats.weeklyRescuedCount());
		model.addAttribute("weeklyRecoveredAmount", stats.weeklyRecoveredAmount());
		model.addAttribute("weeklyCo2Kg", stats.weeklyCo2Kg());
		model.addAttribute("todayCo2Kg", stats.todayCo2Kg());

		model.addAttribute("draftPendingCount", stats.draftPendingCount());
		model.addAttribute("incomingReservationCount", stats.incomingReservationCount());

		model.addAttribute("needsAutomationSetup", stats.needsAutomationSetup());

		model.addAttribute("needsBankAccount", stats.needsBankAccount());

		model.addAttribute("settlementPayout", stats.settlementPayout());

		return "storeView/dashboard";
	}

	/**
	 * 추가됨 — 왜: "폐기 절감(구제율)" 카드의 목표치(70%)가 하드코딩이라 점주가 못 바꿨다. 별도 설정
	 * 화면을 만들 정도의 값은 아니라서, 대시보드 pill 옆 연필 아이콘 → prompt() → 이 엔드포인트로
	 * 바로 저장하는 가벼운 방식으로 처리한다.
	 */
	@PostMapping("/rescue-goal")
	public ResponseEntity<Void> updateRescueGoal(@RequestParam int percent, HttpSession session) {
		Long userId = (Long) session.getAttribute("userId");
		if (userId == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}

		StoreEntity store = storeAccessService.findMyStore(userId).orElse(null);
		if (store == null) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
		}

		storeService.updateRescueGoal(store, percent);
		return ResponseEntity.ok().build();
	}

	/**
	 * 추가됨 — 왜: "매장 정보 등록/수정 (영업시간·위치·카테고리)" (WBS 3.0, 문창호 담당).
	 * /auth/owner-apply를 승인 후 수정 용도로 재사용하면 approvalStatus가 PENDING으로 리셋되는
	 * 문제가 있어서(그 화면은 "신청" 전용), 승인된 매장이 안전하게 정보만 고칠 수 있는 별도 화면을 둔다.
	 * 이 화면은 approvalStatus/businessNumber는 아예 건드리지 않는다.
	 */
	@GetMapping("/edit")
	public String editForm(HttpSession session, Model model) {
		Long userId = (Long) session.getAttribute("userId");
		if (userId == null) {
			return "redirect:/auth/loginForm";
		}

		StoreEntity store = storeAccessService.findMyStore(userId).orElse(null);
		if (store == null) {
			return "redirect:/auth/owner-apply";
		}

		model.addAttribute("store", store);
		model.addAttribute("kakaoMapJsKey", kakaoMapJsKey);

		// POS 연동 상태 요약 (자세한 관리는 /store/pos) — 2026-08-27 문창호
		model.addAttribute("posConnected", store.getPosProvider() != null);
		model.addAttribute("posProviderLabel", posProviderLabel(store.getPosProvider()));
		model.addAttribute("posMenuCount", storeService.getPosMenuCount(store.getId()));
		return "storeView/edit";
	}

	private String posProviderLabel(String provider) {
		return net.dsa.girigiri.service.PosCatalogService.providerLabel(provider);
	}

	/**
	 * 상호명/사업자등록번호/주소는 승인 심사의 근거였던 값이라 여기서 안 받는다(폼에도 읽기전용으로만
	 * 표시) — 점주가 마음대로 바꾸면 "승인 안 된 가게가 승인된 척" 할 수 있는 구멍이 생긴다.
	 * 이 셋을 바꾸고 싶으면 운영자 재심사가 필요한데, 그건 슈퍼어드민 화면(송보미 담당)이 생긴 뒤
	 * "변경 요청 → 재승인" 플로우로 별도 구현할 예정 — 지금은 범위 밖.
	 *
	 * 위치(latitude/longitude)는 폼에서 직접 입력받지 않는다 — 주소를 사람이 손으로 좌표로 옮기는 건
	 * 비현실적이라, 화면에서 카카오맵 Geocoder(주소→좌표 변환)로 미리 계산해서 hidden input으로
	 * 같이 제출한다. 변환에 실패하면 null로 넘어와서 좌표 없이 저장되고(홈 지도엔 그 매장만 안 뜸),
	 * 그래도 나머지 정보 수정 자체는 막지 않는다.
	 */
	@PostMapping("/edit")
	public String editSubmit(@RequestParam String category,
	                         @RequestParam String phone,
	                         @RequestParam(required = false) String operatingHours,
	                         @RequestParam(required = false) Double latitude,
	                         @RequestParam(required = false) Double longitude,
	                         @RequestParam(required = false) String bankName,
	                         @RequestParam(required = false) String bankAccount,
	                         @RequestParam(required = false) String accountHolder,
	                         HttpSession session) {
		Long userId = (Long) session.getAttribute("userId");
		if (userId == null) {
			return "redirect:/auth/loginForm";
		}

		StoreEntity store = storeAccessService.findMyStore(userId).orElse(null);
		if (store == null) {
			return "redirect:/auth/owner-apply";
		}

		if (!storeService.isEditValid(category, phone)) {
			return "redirect:/store/edit?error";
		}

		storeService.updateStoreInfo(store, category, phone, operatingHours, latitude, longitude,
				bankName, bankAccount, accountHolder);

		return "redirect:/store/dashboard?edited";
	}

}

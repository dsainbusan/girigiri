package net.dsa.girigiri.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.StoreDashboardStatsDto;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.service.StoreAccessService;
import net.dsa.girigiri.service.StoreService;
import net.dsa.girigiri.util.StoreReportExcelGenerator;
import net.dsa.girigiri.util.StoreReportPdfGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
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
 */
@Controller
@RequestMapping("/store")
@RequiredArgsConstructor
public class StoreController {

	private final StoreAccessService storeAccessService;
	private final StoreService storeService;
	private final net.dsa.girigiri.service.StoreReportService storeReportService;
	private final net.dsa.girigiri.service.SettlementService settlementService;

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
	 * 판매·폐기 리포트 — 미리보기 화면 (WBS 3.0, 문창호). 대시보드 "오늘"/"최근 7일" 탭에서 진입.
	 * period: daily(오늘, 기본) / weekly(최근 7일). 이 화면에서 Excel/PDF 다운로드로 이어진다.
	 */
	@GetMapping("/report")
	public String reportPage(@RequestParam(defaultValue = "daily") String period, HttpSession session, Model model) {
		Long userId = (Long) session.getAttribute("userId");
		if (userId == null) {
			return "redirect:/auth/loginForm";
		}
		StoreEntity store = storeAccessService.findMyStore(userId).orElse(null);
		if (store == null) {
			return "redirect:/auth/owner-apply";
		}
		boolean weekly = "weekly".equals(period);
		model.addAttribute("report", storeReportService.build(store, weekly));
		model.addAttribute("period", weekly ? "weekly" : "daily");
		return "reportView/report";
	}

	/**
	 * 리포트 Excel 다운로드. dashboard()와 같은 집계(StoreReportService)를 써서 화면·파일 숫자가 항상 일치한다.
	 */
	@GetMapping("/report/excel")
	public ResponseEntity<byte[]> reportExcel(@RequestParam(defaultValue = "daily") String period, HttpSession session) throws IOException {
		StoreEntity store = reportStore(session);
		if (store == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		boolean weekly = "weekly".equals(period);
		byte[] excel = StoreReportExcelGenerator.generate(storeReportService.build(store, weekly));

		// 파일명에 한글(매장명)을 넣으면 일부 브라우저에서 Content-Disposition 인코딩이 깨질 수 있어 ASCII로 고정.
		String filename = "store-report-" + (weekly ? "weekly-" : "") + LocalDate.now() + ".xlsx";
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
				.body(excel);
	}

	/** 리포트 PDF 다운로드. reportExcel()과 데이터 소스 동일, 포맷만 PDF. */
	@GetMapping("/report/pdf")
	public ResponseEntity<byte[]> reportPdf(@RequestParam(defaultValue = "daily") String period, HttpSession session) throws IOException {
		StoreEntity store = reportStore(session);
		if (store == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		boolean weekly = "weekly".equals(period);
		byte[] pdf = StoreReportPdfGenerator.generate(storeReportService.build(store, weekly));

		String filename = "store-report-" + (weekly ? "weekly-" : "") + LocalDate.now() + ".pdf";
		return ResponseEntity.ok()
				.contentType(MediaType.APPLICATION_PDF)
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
				.body(pdf);
	}

	private StoreEntity reportStore(HttpSession session) {
		Long userId = (Long) session.getAttribute("userId");
		return userId == null ? null : storeAccessService.findMyStore(userId).orElse(null);
	}

	/**
	 * 매장 정산 페이지 — 미리보기 화면 (WBS 2.0, 문창호). 기간별 결제 집계 / 수수료·정산 예정액 / 정산 내역.
	 * period: today / week / month(기본). from·to(yyyy-MM-dd)를 둘 다 주면 그 날짜 구간으로 집계(preset 무시).
	 * 판매·폐기 리포트와 다른 문서 — 이건 회계(정산)용.
	 */
	@GetMapping("/settlement")
	public String settlementPage(@RequestParam(defaultValue = "month") String period,
	                             @RequestParam(required = false) String from,
	                             @RequestParam(required = false) String to,
	                             HttpSession session, Model model) {
		Long userId = (Long) session.getAttribute("userId");
		if (userId == null) {
			return "redirect:/auth/loginForm";
		}
		StoreEntity store = storeAccessService.findMyStore(userId).orElse(null);
		if (store == null) {
			return "redirect:/auth/owner-apply";
		}
		LocalDate fromDate = parseDateOrNull(from);
		LocalDate toDate = parseDateOrNull(to);
		boolean custom = fromDate != null && toDate != null && !toDate.isBefore(fromDate);
		String p = normalizeSettlementPeriod(period);

		model.addAttribute("settlement", settlementService.build(store, p, fromDate, toDate));
		model.addAttribute("period", custom ? "custom" : p);
		model.addAttribute("from", custom ? fromDate.toString() : "");
		model.addAttribute("to", custom ? toDate.toString() : "");
		model.addAttribute("issuedDate", LocalDate.now().toString());

		// 정산 내역 (주간 확정 기록) — 최근 주간이 위로
		model.addAttribute("settlements", storeService.getSettlementHistory(store.getId()));
		model.addAttribute("bankRegistered",
				store.getBankName() != null && !store.getBankName().isBlank()
						&& store.getBankAccount() != null && !store.getBankAccount().isBlank());
		model.addAttribute("minPayout", net.dsa.girigiri.service.SettlementService.MIN_PAYOUT);
		return "settlementView/settlement";
	}

	@GetMapping("/settlement/excel")
	public ResponseEntity<byte[]> settlementExcel(@RequestParam(defaultValue = "month") String period,
	                                              @RequestParam(required = false) String from,
	                                              @RequestParam(required = false) String to,
	                                              HttpSession session) throws IOException {
		StoreEntity store = reportStore(session);
		if (store == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		LocalDate fromDate = parseDateOrNull(from);
		LocalDate toDate = parseDateOrNull(to);
		String p = normalizeSettlementPeriod(period);
		byte[] excel = net.dsa.girigiri.util.SettlementExcelGenerator.generate(
				settlementService.build(store, p, fromDate, toDate));
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + settlementFilename(p, fromDate, toDate, "xlsx") + "\"")
				.body(excel);
	}

	@GetMapping("/settlement/pdf")
	public ResponseEntity<byte[]> settlementPdf(@RequestParam(defaultValue = "month") String period,
	                                            @RequestParam(required = false) String from,
	                                            @RequestParam(required = false) String to,
	                                            HttpSession session) throws IOException {
		StoreEntity store = reportStore(session);
		if (store == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		LocalDate fromDate = parseDateOrNull(from);
		LocalDate toDate = parseDateOrNull(to);
		String p = normalizeSettlementPeriod(period);
		byte[] pdf = net.dsa.girigiri.util.SettlementPdfGenerator.generate(
				settlementService.build(store, p, fromDate, toDate));
		return ResponseEntity.ok()
				.contentType(MediaType.APPLICATION_PDF)
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + settlementFilename(p, fromDate, toDate, "pdf") + "\"")
				.body(pdf);
	}

	private String normalizeSettlementPeriod(String period) {
		return switch (period == null ? "" : period) {
			case "today", "week" -> period;
			default -> "month";
		};
	}

	private LocalDate parseDateOrNull(String s) {
		if (s == null || s.isBlank()) {
			return null;
		}
		try {
			return LocalDate.parse(s.trim());
		} catch (java.time.format.DateTimeParseException e) {
			return null;
		}
	}

	private String settlementFilename(String period, LocalDate from, LocalDate to, String ext) {
		boolean custom = from != null && to != null && !to.isBefore(from);
		String tag = custom ? (from + "_" + to) : (period + "-" + LocalDate.now());
		return "store-settlement-" + tag + "." + ext;
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

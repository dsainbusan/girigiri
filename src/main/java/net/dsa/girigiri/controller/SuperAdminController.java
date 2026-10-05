package net.dsa.girigiri.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.DailyPlatformStatsDto;
import net.dsa.girigiri.domain.dto.PlatformStatsDto;
import net.dsa.girigiri.domain.dto.StoreStatsRowDto;
import net.dsa.girigiri.service.NotificationService;
import net.dsa.girigiri.service.SuperAdminDashboardService;
import net.dsa.girigiri.util.DailyPlatformReportExcelGenerator;
import net.dsa.girigiri.util.DailyPlatformReportPdfGenerator;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;

/**
 * 슈퍼어드민(플랫폼 운영자) 대시보드/공통코드/알림 라우팅.
 * common/layout-admin 을 쓰는 wide 레이아웃 전용 — 나머지 컨트롤러(common/layout, 420px)와는 별도 트랙.
 *
 * 2026-09-03, 레이어 규칙 2단계 — 회원/매장/공지사항/신고·문의는 도메인별 컨트롤러
 * (SuperAdminMemberController/SuperAdminStoreController/SuperAdminNoticeController/
 * SuperAdminSupportController)로 분리됐고, 여기는 그 어디에도 깔끔히 안 묶이는 나머지
 * (대시보드/공통코드/알림)만 남는다.
 */
@Controller
@RequestMapping("/superadmin")
@RequiredArgsConstructor
public class SuperAdminController {

	private final SuperAdminDashboardService dashboardService;
	private final NotificationService notificationService;

	// 변경됨 (2026-09-30) — 통계 대시보드 전면 개편. KPI 5카드 + 처리 대기 통합 리스트로 바뀌면서
	// 모델 속성이 SuperAdminDashboardStatsDto 하나(kpi/pendingQueue/asOfLabel/updatedAtLabel)로
	// 단순해졌다 — 예전엔 개별 카운트 6개 + 위젯 2개를 따로 풀어서 넘겼다.
	//
	// 추가됨 (2026-10-05) — "달력에서 날짜를 클릭하면 그 날 현황을 보고 싶다"는 요청으로 일별 현황
	// 달력을 추가했다. date 쿼리스트링(yyyy-MM-dd)이 있으면 그 날의 DailyPlatformStatsDto를 같이
	// 내려주고, month(yyyy-MM)로 달력을 다른 달로 넘길 수 있다. 둘 다 없으면 이번 달 달력만 보여주고
	// (선택된 날짜 없음) 상단 KPI 5카드(오늘 기준)는 그대로 유지한다.
	@GetMapping("/dashboard")
	public String dashboard(@RequestParam(required = false) String date,
	                        @RequestParam(required = false) String month,
	                        Model model) {
		model.addAttribute("stats", dashboardService.getDashboardStats());
		populateDailyReportModel(date, month, model);
		return "superAdminView/dashboard";
	}

	/**
	 * 추가됨 (2026-10-05) — 달력/월이동 클릭마다 페이지 전체가 새로고침되는 게 불편하다는 피드백으로
	 * 추가. dashboard.html의 #daily-report 안쪽(dailyReportBody 프래그먼트)만 떼어 돌려주면,
	 * 브라우저에서 그 부분만 fetch로 받아 innerHTML을 갈아끼운다(dashboard.html 하단 스크립트).
	 * 모델 데이터는 dashboard()와 완전히 같은 populateDailyReportModel을 써서 — 풀페이지로 봤을 때와
	 * 숫자가 어긋날 일이 없다.
	 */
	@GetMapping("/dashboard/daily")
	public String dailyReportFragment(@RequestParam(required = false) String date,
	                                  @RequestParam(required = false) String month,
	                                  Model model) {
		populateDailyReportModel(date, month, model);
		return "superAdminView/dashboard :: dailyReportBody";
	}

	private void populateDailyReportModel(String date, String month, Model model) {
		LocalDate selectedDate = parseDate(date);
		YearMonth calendarMonth = month != null ? parseMonth(month)
				: (selectedDate != null ? YearMonth.from(selectedDate) : YearMonth.now());
		model.addAttribute("calendar", dashboardService.buildCalendar(calendarMonth, selectedDate));
		if (selectedDate != null) {
			model.addAttribute("dailyStats", dashboardService.getDailyPlatformStats(selectedDate));
		}
	}

	/** 일별 현황 리포트 Excel. 화면(dailyStats)과 같은 집계라 숫자가 항상 일치한다. */
	@GetMapping("/dashboard/daily/excel")
	public ResponseEntity<byte[]> dailyExcel(@RequestParam String date) throws IOException {
		LocalDate parsed = parseDate(date);
		if (parsed == null) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
		}
		DailyPlatformStatsDto stats = dashboardService.getDailyPlatformStats(parsed);
		byte[] xlsx = DailyPlatformReportExcelGenerator.generate(stats);
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
				.header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"platform-daily-report-" + parsed + ".xlsx\"")
				.body(xlsx);
	}

	/** 일별 현황 리포트 PDF. excel()과 데이터 소스 동일, 포맷만 PDF. */
	@GetMapping("/dashboard/daily/pdf")
	public ResponseEntity<byte[]> dailyPdf(@RequestParam String date) throws IOException {
		LocalDate parsed = parseDate(date);
		if (parsed == null) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
		}
		DailyPlatformStatsDto stats = dashboardService.getDailyPlatformStats(parsed);
		byte[] pdf = DailyPlatformReportPdfGenerator.generate(stats);
		return ResponseEntity.ok()
				.contentType(MediaType.APPLICATION_PDF)
				.header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"platform-daily-report-" + parsed + ".pdf\"")
				.body(pdf);
	}

	// date는 미래 날짜/형식 오류 시 null로 취급(선택 안 한 것과 동일하게 — 아직 있을 수 없는 데이터라서).
	private LocalDate parseDate(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		try {
			LocalDate parsed = LocalDate.parse(raw);
			return parsed.isAfter(LocalDate.now()) ? null : parsed;
		} catch (DateTimeParseException e) {
			return null;
		}
	}

	private YearMonth parseMonth(String raw) {
		try {
			return YearMonth.parse(raw);
		} catch (DateTimeParseException e) {
			return YearMonth.now();
		}
	}

	/**
	 * 추가됨 (2026-09-09) — 대시보드 "거래량/구제량/매출" 카드를 눌렀을 때 가는 화면. 그동안 이 셋을
	 * 따로 보여주는 화면이 없어서 임시로 매장 관리로 보내고 있었는데, 실제 페이지를 만들어달라는
	 * 요청으로 신설했다. period(오늘/7일/30일/전체)는 유효하지 않으면 서비스가 "all"로 취급한다.
	 */
	@GetMapping("/stats")
	public String stats(@RequestParam(required = false) String period, Model model) {
		model.addAttribute("stats", dashboardService.getPlatformStats(period));
		return "superAdminView/stats";
	}

	/**
	 * 추가됨 (2026-09-09) — 플랫폼 통계 화면의 매장별 표를 CSV로 내려받기. exportStores/exportMembers와
	 * 동일 패턴(BOM 붙여서 엑셀에서 한글 안 깨지게). 지금 보고 있던 기간 필터를 그대로 반영한다.
	 */
	@GetMapping("/stats/export")
	public ResponseEntity<byte[]> exportStats(@RequestParam(required = false) String period) {
		PlatformStatsDto stats = dashboardService.getPlatformStats(period);

		StringBuilder csv = new StringBuilder("﻿");
		csv.append("매장명,거래량,구제량,매출,결제 완료 주문 수,취소율(%),노쇼율(%)\n");
		for (StoreStatsRowDto row : stats.storeRows()) {
			csv.append(csvField(row.storeName())).append(',')
					.append(row.transactionCount()).append(',')
					.append(row.rescuedQuantity()).append(',')
					.append(row.revenue()).append(',')
					.append(row.totalOrderCount()).append(',')
					.append(row.cancelRatePercent()).append(',')
					.append(row.noshowRatePercent())
					.append('\n');
		}

		String filename = "platform-stats-" + stats.period() + ".csv";
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
				.contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
				.body(csv.toString().getBytes(StandardCharsets.UTF_8));
	}

	private String csvField(String value) {
		String safe = value == null ? "" : value.replace("\"", "\"\"");
		return "\"" + safe + "\"";
	}

	@GetMapping("/codes")
	public String codes() {
		return "superAdminView/codes";
	}

	/**
	 * 알림 패널에서 알림 하나를 클릭했을 때. NotificationController#open(/user/alerts/{id})와
	 * 동일한 패턴 — 읽음 처리 후 linkUrl로 보낸다(없으면 대시보드로).
	 *
	 * 변경됨 (2026-09-29) — 왜: "role=ADMIN인 첫 계정"을 운영자로 취급하던 스톱갭
	 * (SuperAdminDashboardService.findAdminIdOrNull)을 제거하고 세션의 실제 로그인 계정(userId)을
	 * 쓴다. SuperAdminAccessInterceptor(WebMvcConfig, /superadmin/** 전체 적용)가 이 시점에 이미
	 * session.role==ADMIN을 강제해뒀으므로 userId도 항상 그 관리자 본인의 값이다. 운영자가 2명
	 * 이상이 되면 스톱갭은 항상 같은 한 명에게로 알림 읽음 처리가 쏠리는 문제가 있었다.
	 */
	@GetMapping("/notifications/{id}")
	public String openNotification(@PathVariable Long id, HttpSession session) {
		Long adminId = (Long) session.getAttribute("userId");
		String linkUrl = notificationService.markRead(adminId, id);
		return linkUrl != null && !linkUrl.isBlank() ? "redirect:" + linkUrl : "redirect:/superadmin/dashboard";
	}

	@PostMapping("/notifications/read-all")
	public String readAllNotifications(@RequestHeader(value = "Referer", required = false) String referer,
										HttpServletRequest request, HttpSession session) {
		Long adminId = (Long) session.getAttribute("userId");
		notificationService.markAllRead(adminId);
		return "redirect:" + resolveReturnTo(referer, request);
	}

	// 추가됨 (2026-09-08, 코드 감사) — 알림 패널이 슈퍼어드민 공용 레이아웃(layout-admin)에 있어서
	// 어느 화면에서나 이 폼이 제출될 수 있다 보니 "방금 있던 화면으로" 되돌리는 값을 Referer 헤더
	// 그대로 썼다. ReviewController#resolveRedirect가 임의 문자열을 그대로 redirect에 안 쓰고
	// 화이트리스트로 거르듯, 여기도 같은 호스트(OriginCheckFilter와 동일한 판정) + "/superadmin/"
	// 경로일 때만 그 값을 쓰고, 그 외(다른 호스트, 이상한 형식, 없음)는 대시보드로 보낸다.
	private String resolveReturnTo(String referer, HttpServletRequest request) {
		if (referer == null || referer.isBlank()) {
			return "/superadmin/dashboard";
		}
		try {
			URI uri = new URI(referer);
			boolean sameHost = uri.getHost() != null && uri.getHost().equalsIgnoreCase(request.getServerName());
			boolean superAdminPath = uri.getPath() != null && uri.getPath().startsWith("/superadmin/");
			if (sameHost && superAdminPath) {
				return referer;
			}
		} catch (URISyntaxException e) {
			// 형식이 이상하면 판단 불가 — 안전하게 대시보드로.
		}
		return "/superadmin/dashboard";
	}
}

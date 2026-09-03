package net.dsa.girigiri.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.service.StoreAccessService;
import net.dsa.girigiri.service.StoreService;
import net.dsa.girigiri.util.StoreReportExcelGenerator;
import net.dsa.girigiri.util.StoreReportPdfGenerator;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.time.LocalDate;

/**
 * 점주용 판매·폐기 리포트 및 정산 화면 (WBS 3.0/2.0, 문창호 담당).
 * 2026-09-03 — 373줄이던 StoreController에서 분리했다(레이어 규칙 정리, 도메인 분할).
 * @RequestMapping("/store")은 그대로라 URL은 하나도 안 바뀐다.
 */
@Controller
@RequestMapping("/store")
@RequiredArgsConstructor
public class StoreReportController {

	private final StoreAccessService storeAccessService;
	private final StoreService storeService;
	private final net.dsa.girigiri.service.StoreReportService storeReportService;
	private final net.dsa.girigiri.service.SettlementService settlementService;

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
	 * 매장 정산 — 목록 화면 (WBS 2.0, 문창호). "내 정산 확인"만: 이번 정산 주간(진행 중) + 확정된 주간 기록.
	 * 특정 기간의 계산식·거래 명세·정산서 다운로드는 /store/settlement/detail 로 분리(목록→상세).
	 */
	@GetMapping("/settlement")
	public String settlementPage(HttpSession session, Model model) {
		Long userId = (Long) session.getAttribute("userId");
		if (userId == null) {
			return "redirect:/auth/loginForm";
		}
		StoreEntity store = storeAccessService.findMyStore(userId).orElse(null);
		if (store == null) {
			return "redirect:/auth/owner-apply";
		}
		model.addAttribute("storeName", store.getStoreName());
		model.addAttribute("settlements", storeService.getSettlementHistory(store.getId()));
		model.addAttribute("currentWeek", settlementService.currentWeek(store));
		model.addAttribute("bankRegistered", settlementService.isBankRegistered(store));
		model.addAttribute("minPayout", net.dsa.girigiri.service.SettlementService.MIN_PAYOUT);
		return "settlementView/settlement";
	}

	/**
	 * 매장 정산 — 상세(정산서) 화면. period: today / week / month(기본). from·to(yyyy-MM-dd) 둘 다 주면 그 구간.
	 * 목록의 주간 행 클릭 또는 "기간 직접 조회"로 진입. 뒤로가기 = 목록.
	 */
	@GetMapping("/settlement/detail")
	public String settlementDetail(@RequestParam(defaultValue = "month") String period,
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
		LocalDate fromDate = settlementService.parseDateOrNull(from);
		LocalDate toDate = settlementService.parseDateOrNull(to);
		boolean custom = fromDate != null && toDate != null && !toDate.isBefore(fromDate);
		String p = settlementService.normalizeSettlementPeriod(period);

		model.addAttribute("settlement", settlementService.build(store, p, fromDate, toDate));
		model.addAttribute("period", custom ? "custom" : p);
		model.addAttribute("from", custom ? fromDate.toString() : "");
		model.addAttribute("to", custom ? toDate.toString() : "");
		model.addAttribute("issuedDate", LocalDate.now().toString());
		return "settlementView/settlementDetail";
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
		LocalDate fromDate = settlementService.parseDateOrNull(from);
		LocalDate toDate = settlementService.parseDateOrNull(to);
		String p = settlementService.normalizeSettlementPeriod(period);
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
		LocalDate fromDate = settlementService.parseDateOrNull(from);
		LocalDate toDate = settlementService.parseDateOrNull(to);
		String p = settlementService.normalizeSettlementPeriod(period);
		byte[] pdf = net.dsa.girigiri.util.SettlementPdfGenerator.generate(
				settlementService.build(store, p, fromDate, toDate));
		return ResponseEntity.ok()
				.contentType(MediaType.APPLICATION_PDF)
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + settlementFilename(p, fromDate, toDate, "pdf") + "\"")
				.body(pdf);
	}

	private String settlementFilename(String period, LocalDate from, LocalDate to, String ext) {
		boolean custom = from != null && to != null && !to.isBefore(from);
		String tag = custom ? (from + "_" + to) : (period + "-" + LocalDate.now());
		return "store-settlement-" + tag + "." + ext;
	}
}

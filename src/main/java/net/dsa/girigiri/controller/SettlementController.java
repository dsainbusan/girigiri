package net.dsa.girigiri.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.service.SettlementService;
import net.dsa.girigiri.service.StoreAccessService;
import net.dsa.girigiri.service.StoreService;
import net.dsa.girigiri.util.SettlementExcelGenerator;
import net.dsa.girigiri.util.SettlementPdfGenerator;
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
 * 점주용 매장 정산 화면 (WBS 2.0, 문창호 담당).
 * 2026-09-03에 StoreController에서 분리(조장)됐고, 2026-09-07에 판매·폐기 리포트가
 * 매출 리포트(SalesReportController, Supabase)로 흡수되면서 정산만 남아 이름을 SettlementController로 바꿨다.
 * @RequestMapping("/store")은 그대로라 URL은 안 바뀐다.
 */
@Controller
@RequestMapping("/store")
@RequiredArgsConstructor
public class SettlementController {

	private final StoreAccessService storeAccessService;
	private final StoreService storeService;
	private final SettlementService settlementService;

	/**
	 * 매장 정산 — 목록 화면. "내 정산 확인"만: 이번 정산 주간(진행 중) + 확정된 주간 기록.
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
		model.addAttribute("minPayout", SettlementService.MIN_PAYOUT);
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
		StoreEntity store = sessionStore(session);
		if (store == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		LocalDate fromDate = settlementService.parseDateOrNull(from);
		LocalDate toDate = settlementService.parseDateOrNull(to);
		String p = settlementService.normalizeSettlementPeriod(period);
		byte[] excel = SettlementExcelGenerator.generate(settlementService.build(store, p, fromDate, toDate));
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
		StoreEntity store = sessionStore(session);
		if (store == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		LocalDate fromDate = settlementService.parseDateOrNull(from);
		LocalDate toDate = settlementService.parseDateOrNull(to);
		String p = settlementService.normalizeSettlementPeriod(period);
		byte[] pdf = SettlementPdfGenerator.generate(settlementService.build(store, p, fromDate, toDate));
		return ResponseEntity.ok()
				.contentType(MediaType.APPLICATION_PDF)
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + settlementFilename(p, fromDate, toDate, "pdf") + "\"")
				.body(pdf);
	}

	private StoreEntity sessionStore(HttpSession session) {
		Long userId = (Long) session.getAttribute("userId");
		return userId == null ? null : storeAccessService.findMyStore(userId).orElse(null);
	}

	private String settlementFilename(String period, LocalDate from, LocalDate to, String ext) {
		boolean custom = from != null && to != null && !to.isBefore(from);
		String tag = custom ? (from + "_" + to) : (period + "-" + LocalDate.now());
		return "store-settlement-" + tag + "." + ext;
	}
}

package net.dsa.girigiri.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.security.LoginRequired;
import net.dsa.girigiri.domain.dto.PayoutStatementDto;
import net.dsa.girigiri.domain.dto.SettlementData;
import net.dsa.girigiri.service.PayoutStatementService;
import net.dsa.girigiri.service.StoreAccessService;
import net.dsa.girigiri.service.StoreService;
import net.dsa.girigiri.util.PaginationUtil;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

/**
 * 점주용 매장 정산 화면 (WBS 2.0, 문창호 담당).
 * 2026-09-03 — 373줄이던 StoreController에서 분리했다(레이어 규칙 정리, 도메인 분할).
 * 2026-09-07 — 여기 있던 판매·폐기 리포트(/report*)는 매출 리포트(SalesReportController, Supabase)로
 *              통합돼서 삭제했다. 클래스명은 diff 최소화를 위해 그대로 두고 정산만 담당한다.
 * @RequestMapping("/store")은 그대로라 URL은 하나도 안 바뀐다.
 */
@Controller
@RequestMapping("/store")
@RequiredArgsConstructor
@LoginRequired   // /store/** 로그인 강제는 LoginRequiredInterceptor가 담당 (2026-09-09 문창호)
public class StoreReportController {

	private final StoreAccessService storeAccessService;
	private final StoreService storeService;
	private final net.dsa.girigiri.service.SettlementService settlementService;
	private final PayoutStatementService payoutStatementService;

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
	// 추가됨 (2026-09-28) — "정산 대상 거래"가 기간에 따라 수십 건 쌓이면 스크롤이 끝없이 길어진다는
	// 피드백. Excel/PDF는 이 페이징과 무관하게 항상 전체(settlement.lines())를 그대로 쓴다 — 화면
	// 표시용으로만 잘라서 pagedLines로 따로 넘긴다.
	private static final int SETTLEMENT_PAGE_SIZE = 5;
	private static final int SETTLEMENT_PAGE_WINDOW = 5;

	@GetMapping("/settlement/detail")
	public String settlementDetail(@RequestParam(defaultValue = "month") String period,
	                               @RequestParam(required = false) String from,
	                               @RequestParam(required = false) String to,
	                               @RequestParam(defaultValue = "0") int page,
	                               HttpSession session, Model model) {
		Long userId = (Long) session.getAttribute("userId");
		StoreEntity store = storeAccessService.findMyStore(userId).orElse(null);
		if (store == null) {
			return "redirect:/auth/owner-apply";
		}
		LocalDate fromDate = settlementService.parseDateOrNull(from);
		LocalDate toDate = settlementService.parseDateOrNull(to);
		boolean custom = fromDate != null && toDate != null && !toDate.isBefore(fromDate);
		String p = settlementService.normalizeSettlementPeriod(period);

		SettlementData settlement = settlementService.build(store, p, fromDate, toDate);
		model.addAttribute("settlement", settlement);

		List<SettlementData.Line> lines = settlement.lines();
		int totalPages = PaginationUtil.totalPages(lines.size(), SETTLEMENT_PAGE_SIZE);
		int safePage = Math.max(0, Math.min(page, totalPages - 1));
		int windowStart = Math.max(0, Math.min(safePage - 2, totalPages - SETTLEMENT_PAGE_WINDOW));
		model.addAttribute("pagedLines", PaginationUtil.paginate(lines, safePage, SETTLEMENT_PAGE_SIZE));
		model.addAttribute("page", safePage);
		model.addAttribute("totalPages", totalPages);
		model.addAttribute("pageWindowStart", windowStart);
		model.addAttribute("pageWindowEnd", Math.min(totalPages - 1, windowStart + SETTLEMENT_PAGE_WINDOW - 1));

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

	/**
	 * 추가됨 (2026-10-07) — 지급 완료된 주간 정산 1건의 "지급 명세서". 은행 이체확인증 캡처 대신 플랫폼이
	 * 정산 확정 기록으로 직접 발행한다(PayoutStatementService 참고). 내 매장 건 + PAID만 열린다.
	 */
	@GetMapping("/settlement/{id}/statement")
	public String payoutStatement(@PathVariable Long id, HttpSession session, Model model) {
		StoreEntity store = reportStore(session);
		if (store == null) {
			return "redirect:/auth/owner-apply";
		}
		model.addAttribute("statement", payoutStatementService.getPaidStatement(store, id));
		return "settlementView/payoutStatement";
	}

	@GetMapping("/settlement/{id}/statement/pdf")
	public ResponseEntity<byte[]> payoutStatementPdf(@PathVariable Long id, HttpSession session) throws IOException {
		StoreEntity store = reportStore(session);
		if (store == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		PayoutStatementDto statement = payoutStatementService.getPaidStatement(store, id);
		return ResponseEntity.ok()
				.contentType(MediaType.APPLICATION_PDF)
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"payout-statement-" + statement.statementNo() + ".pdf\"")
				.body(net.dsa.girigiri.util.PayoutStatementPdfGenerator.generate(statement));
	}

	private String settlementFilename(String period, LocalDate from, LocalDate to, String ext) {
		boolean custom = from != null && to != null && !to.isBefore(from);
		String tag = custom ? (from + "_" + to) : (period + "-" + LocalDate.now());
		return "store-settlement-" + tag + "." + ext;
	}
}

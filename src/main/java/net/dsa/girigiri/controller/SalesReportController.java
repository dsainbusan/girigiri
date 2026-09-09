package net.dsa.girigiri.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.security.LoginRequired;
import net.dsa.girigiri.service.SalesReportService;
import net.dsa.girigiri.service.StoreAccessService;
import net.dsa.girigiri.util.SalesReportExcelGenerator;
import net.dsa.girigiri.util.SalesReportPdfGenerator;
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
 * 점주용 매출 리포트 (WBS 3.0 "일/주간 판매·폐기 리포트", 문창호 담당).
 *
 * 매출 성과(회수 매출·전주 대비) + 구제 성과(구제율·폐기·CO₂·할인 제공액)를 한 화면에서 낸다.
 * 데이터 소스는 MySQL이 아니라 Supabase(sales 테이블, PostgREST) — 선생님 권장 Supabase 실습 겸.
 * 2026-09-07에 기존 판매·폐기 리포트(/store/report, MySQL)를 이 리포트로 통합했다.
 *
 * sales 적재는 실서비스라면 POS 웹훅이, 데모에선 sql/supabase-sales-seed.sql이 담당한다.
 */
@Controller
@RequestMapping("/store")
@RequiredArgsConstructor
@LoginRequired   // /store/** 로그인 강제는 LoginRequiredInterceptor가 담당 (2026-09-09 문창호)
public class SalesReportController {

	private final StoreAccessService storeAccessService;
	private final SalesReportService salesReportService;

	/**
	 * 매출 리포트 화면. period: today / thisweek(이번 주, 기본) / lastweek(지난 주).
	 * 주 경계는 정산 주(월~일)와 같다. from·to(yyyy-MM-dd)가 둘 다 오면 그 구간을 직접 조회한다.
	 */
	@GetMapping("/sales-report")
	public String salesReportPage(@RequestParam(defaultValue = "thisweek") String period,
	                              @RequestParam(required = false) String from,
	                              @RequestParam(required = false) String to,
	                              HttpSession session, Model model) {
		StoreEntity store = currentStore(session);
		if (store == null) {
			return redirectForNoStore(session);
		}
		model.addAttribute("configured", salesReportService.isConfigured());
		model.addAttribute("storeName", store.getStoreName());
		if (salesReportService.isConfigured()) {
			model.addAttribute("report", salesReportService.build(store, period, from, to));
		}
		return "salesReportView/salesReport";
	}

	/**
	 * 매출 리포트 Excel. 화면과 같은 집계(SalesReportService.build)라 숫자가 항상 일치한다.
	 * Content-Disposition: inline — 새 탭에서 열리고, 저장은 뷰어의 다운로드 버튼으로.
	 */
	@GetMapping("/sales-report/excel")
	public ResponseEntity<byte[]> excel(@RequestParam(defaultValue = "thisweek") String period,
	                                    @RequestParam(required = false) String from,
	                                    @RequestParam(required = false) String to,
	                                    HttpSession session) throws IOException {
		StoreEntity store = currentStore(session);
		if (store == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		if (!salesReportService.isConfigured()) {
			return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
		}
		byte[] xlsx = SalesReportExcelGenerator.generate(salesReportService.build(store, period, from, to));
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
				.header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename("xlsx") + "\"")
				.body(xlsx);
	}

	/** 매출 리포트 PDF. excel()과 데이터 소스 동일, 포맷만 PDF. 마찬가지로 inline. */
	@GetMapping("/sales-report/pdf")
	public ResponseEntity<byte[]> pdf(@RequestParam(defaultValue = "thisweek") String period,
	                                  @RequestParam(required = false) String from,
	                                  @RequestParam(required = false) String to,
	                                  HttpSession session) throws IOException {
		StoreEntity store = currentStore(session);
		if (store == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		if (!salesReportService.isConfigured()) {
			return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
		}
		byte[] pdf = SalesReportPdfGenerator.generate(salesReportService.build(store, period, from, to));
		return ResponseEntity.ok()
				.contentType(MediaType.APPLICATION_PDF)
				.header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename("pdf") + "\"")
				.body(pdf);
	}

	/** 파일명에 한글(매장명)을 넣으면 Content-Disposition 인코딩이 깨질 수 있어 ASCII로 고정. */
	private String filename(String ext) {
		return "sales-report-" + LocalDate.now() + "." + ext;
	}

	private StoreEntity currentStore(HttpSession session) {
		Long userId = (Long) session.getAttribute("userId");
		return userId == null ? null : storeAccessService.findMyStore(userId).orElse(null);
	}

	private String redirectForNoStore(HttpSession session) {
		Long userId = (Long) session.getAttribute("userId");
		return userId == null ? "redirect:/auth/loginForm" : "redirect:/auth/owner-apply";
	}
}

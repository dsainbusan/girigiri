package net.dsa.girigiri.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.service.SalesReportService;
import net.dsa.girigiri.service.StoreAccessService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 점주용 매출 리포트 (WBS 3.0 "일/주간 판매·폐기 리포트" 확장, 문창호 담당).
 *
 * 기존 판매·폐기 리포트(StoreReportController, MySQL 집계 · 운영/환경 지표)와는 별개 기능이다.
 * 매출 성과(총매출 · 전주 대비 · 상품별 · 일별)를 Supabase(Postgres + REST)에 적재한 데이터로 조회한다.
 * 선생님 권장으로 Supabase를 실제로 써보는 실습 목적도 겸한다.
 *
 * 화면 조회 전용이다 — Excel/PDF 문서 출력은 /store/report(판매·폐기 리포트)가 이미 담당하므로
 * 여기서는 중복해서 만들지 않는다. sales 테이블 적재는 실서비스라면 POS 웹훅이, 데모에선
 * sql/supabase-sales-seed.sql이 담당한다. 조장이 리팩터링한 StoreReportController는 건드리지 않는다.
 */
@Controller
@RequestMapping("/store")
@RequiredArgsConstructor
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

	private StoreEntity currentStore(HttpSession session) {
		Long userId = (Long) session.getAttribute("userId");
		return userId == null ? null : storeAccessService.findMyStore(userId).orElse(null);
	}

	private String redirectForNoStore(HttpSession session) {
		Long userId = (Long) session.getAttribute("userId");
		return userId == null ? "redirect:/auth/loginForm" : "redirect:/auth/owner-apply";
	}
}

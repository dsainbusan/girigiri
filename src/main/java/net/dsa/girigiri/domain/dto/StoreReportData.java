package net.dsa.girigiri.domain.dto;

import java.util.List;

/**
 * 판매·폐기 리포트 데이터 — 미리보기 화면(reportView/report.html), Excel, PDF가 모두 이 하나를 쓴다
 * (화면 숫자와 다운로드 파일 숫자가 항상 일치하도록). 조립은 StoreReportService.
 *
 * WBS 3.0 "일/주간 판매·폐기 리포트 (Excel + PDF)" — 문창호. 2026-08-30.
 */
public record StoreReportData(
		String storeName,
		String periodLabel,   // "2026-08-30 (오늘)" / "2026-08-24 ~ 2026-08-30 (최근 7일)"
		boolean weekly,
		List<Line> lines,
		Totals totals
) {

	/**
	 * 표의 한 줄.
	 * - 일간: 상품 1건 (등록/판매/폐기/매출/할인액)
	 * - 주간: 판매(예약) 1건 (수량=sold, 금액=sales, 상태=status) — 등록/폐기/할인 필드는 0
	 */
	public record Line(
			String dateLabel,     // 일간=등록일 / 주간=픽업일 (MM/dd)
			String name,
			int originalPrice,
			int discountedPrice,
			int discountRate,     // %
			int registered,       // 등록 수량
			int sold,             // 판매 수량
			int wasted,           // 폐기 수량
			long sales,           // 회수 매출
			long discountGiven,   // 할인액
			String status         // 주간 판매내역의 상태("픽업 완료" 등). 일간은 "".
	) {}

	public record Totals(
			int registered,
			int sold,
			int wasted,
			long sales,
			long discountGiven,
			int rescueRate,       // 구제율 % = 판매 ÷ 등록
			String co2Kg          // "5.5"
	) {}
}

package net.dsa.girigiri.domain.dto;

import java.util.List;

/**
 * 매출 리포트 화면·Excel·PDF 공용 집계 결과 (문창호).
 * SalesReportService.build() 하나에서 나온다 — 화면·파일 숫자가 항상 일치하도록.
 *
 * 데이터 소스는 Supabase(sales 테이블)다. 매출 성과(회수 매출·전주 대비)와
 * 구제 성과(구제율·폐기·CO₂·할인 제공액)를 한 리포트에서 함께 낸다.
 * 기간 pill(오늘/이번 주/지난 주) + 직접 선택. 주 경계는 정산 주(월~일)와 동일.
 */
public record SalesReportData(
		String storeName,
		String periodLabel,     // "2026-09-08 ~ 2026-09-14 (이번 주)" 또는 "2026-09-07 (오늘)"
		String periodKey,       // today / thisweek / lastweek / custom
		String fromDate,        // "yyyy-MM-dd" — 직접 선택 폼 프리필용 (custom일 때만)
		String toDate,
		boolean showGraph,      // 기간이 2일 이상이면 일별 막대그래프 표시
		boolean empty,

		// 매출
		long totalSales,        // 회수 매출 합 (Σ 판매수량 × 할인가)
		long discountGiven,     // 할인 제공액 합 (Σ 판매수량 × (정상가 − 할인가))
		Long prevTotalSales,    // 직전 주 회수 매출 (이번 주/지난 주일 때만, 아니면 null)
		Integer deltaPercent,   // 전주 대비 % (직전 주가 0이면 null)

		// 구제 · 폐기
		int registeredCount,    // 등록 수량 합
		int soldCount,          // 판매 수량 합
		int wastedCount,        // 폐기 수량 합 (등록 − 판매)
		int rescueRatePercent,  // 판매 ÷ 등록 × 100
		double co2Kg,           // CO₂ 절감(kg) — 카테고리별 계수 × 판매 수량

		List<DayLine> days,
		List<ProductLine> products
) {
	/** 일별 한 칸. 막대는 판매(초록)+폐기(빨강) 스택, 높이는 등록 수량 최대일 대비 %. */
	public record DayLine(String label, boolean showLabel, long amount,
	                      int soldQty, int wastedQty,
	                      int soldHeightPercent, int wastedHeightPercent) {}

	/** 상품별 한 줄. */
	public record ProductLine(String name, String category,
	                          int registeredQty, int soldQty, int wastedQty,
	                          long amount, int rescueRatePercent) {}
}

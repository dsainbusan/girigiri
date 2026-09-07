package net.dsa.girigiri.domain.dto;

import java.util.List;

/**
 * 매출 리포트 화면·Excel·PDF 공용 집계 결과 (문창호).
 * SalesReportService.build() 하나에서 나온다 — 화면·파일 숫자가 항상 일치하도록.
 *
 * 데이터 소스는 Supabase(sales 테이블)다. 판매·폐기 리포트(StoreReportData, MySQL)와는 별개.
 * 기간 pill(오늘/이번 주/지난 주) + 직접 선택 방식이고, periodKey / fromDate / toDate 로
 * 현재 선택 상태를 화면에 돌려준다. 주 경계는 정산 주(월~일)와 동일.
 */
public record SalesReportData(
		String storeName,
		String periodLabel,     // "2026-09-08 ~ 2026-09-14" 또는 "2026-09-07 (오늘)"
		String periodKey,       // today / thisweek / lastweek / custom  (pill 활성 표시용)
		String fromDate,        // "yyyy-MM-dd" — 직접 선택 폼 프리필용 (custom일 때만 채움)
		String toDate,
		boolean showGraph,      // 기간이 2일 이상이면 일별 막대그래프 표시
		boolean empty,
		long totalSales,
		int orderCount,         // 판매 건수 (sales 행 수)
		int itemCount,          // 판매 수량 합
		Long prevTotalSales,    // 직전 주 총매출 (이번 주/지난 주처럼 "한 주 전체"일 때만, 아니면 null)
		Integer deltaPercent,   // 전주 대비 % (직전 주가 0이면 null)
		List<DayLine> days,     // 일별 매출 (그래프용)
		List<ProductLine> products
) {
	/** 일별 매출 한 칸. barPercent = 최대 매출일 대비 높이 %. showLabel = 그래프에 날짜 라벨 표시 여부. */
	public record DayLine(String label, boolean showLabel, long amount, int count, int barPercent) {}

	/** 상품별 매출 한 줄. sharePercent = 총매출 대비 비중 %. */
	public record ProductLine(String name, String category, int qty, long amount, int sharePercent) {}
}

package net.dsa.girigiri.domain.dto;

/**
 * 통계 대시보드 "마감 상품 소진 현황"(최근 7일) 막대 1개 (2026-09-30).
 * SKU(상품 건수) 기준 — 수량(재고) 기준이 아니다(사용자 확인 완료). registeredHeightPercent/
 * soldHeightPercent는 같은 축(그 주 최댓값 registeredCount 대비 %)으로 계산해서, 막대 하나 안에
 * 판매(아래)/미판매(위) 두 구간을 flex-grow로 쌓을 수 있게 한다 — storeView 대시보드의
 * DailySalesBarDto와 동일한 패턴.
 */
public record SellThroughDailyDto(
		String dateLabel,
		boolean isToday,
		int registeredCount,
		int soldCount,
		// 등록 0건이면 null → 화면에서 "0%"가 아니라 "-"로 표시.
		Integer sellThroughPercent,
		int registeredHeightPercent,
		int soldHeightPercent
) {
}

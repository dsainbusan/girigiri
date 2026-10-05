package net.dsa.girigiri.domain.dto;

/**
 * 슈퍼어드민 "통계 대시보드" 상단 KPI 카드 5개용 (2026-09-30, 대시보드 리디자인).
 * 플랫폼 수수료 필드는 코드 어디에도 수수료 모델이 없어 뺐다 — 화면에서 그 보조 줄 자체를 생략한다.
 */
public record KpiSummaryDto(
		long totalMemberCount,
		long memberTodayCount,
		long memberWeekCount,
		long totalStoreCount,
		long todayStoreCount,
		long ownerCount,
		long pendingStoreCount,
		long totalTransactionCount,
		long todayTransactionCount,
		long totalRevenue,
		long todayRevenue,
		long totalRescuedQuantity,
		long todayRescuedQuantity,
		double totalCo2Kg,
		double todayCo2Kg
) {
}

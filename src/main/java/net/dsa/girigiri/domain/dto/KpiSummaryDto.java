package net.dsa.girigiri.domain.dto;

/**
 * 슈퍼어드민 "통계 대시보드" 상단 KPI 카드용 (2026-09-30, 대시보드 리디자인).
 * 플랫폼 수수료 필드는 코드 어디에도 수수료 모델이 없어 뺐다 — 화면에서 그 보조 줄 자체를 생략한다.
 *
 * 변경됨 (2026-10-06) — CO2 절감 필드 제거. "오늘 구해낸 음식"의 전체/오늘 값은 서로 다른 기준을
 * 참조해 "19개인데 CO2는 0.0kg"처럼 보이는 버그가 있었고, 이번에 CO2 표시 자체를 대시보드에서 빼고
 * (2단계 "통계 리포트"로 이동 예정) 정리했다. todayXxx는 이제 pickedAt(KST) 기준이다.
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
		long todayRescuedQuantity
) {
}

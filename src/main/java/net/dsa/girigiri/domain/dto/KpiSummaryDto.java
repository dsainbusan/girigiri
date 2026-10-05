package net.dsa.girigiri.domain.dto;

/**
 * 슈퍼어드민 "통계 대시보드" 상단 KPI 카드용 (2026-09-30, 대시보드 리디자인).
 * 플랫폼 수수료 필드는 코드 어디에도 수수료 모델이 없어 뺐다 — 화면에서 그 보조 줄 자체를 생략한다.
 *
 * 변경됨 (2026-10-06) — 5장에서 4장으로 축소(신규 회원/거래/거래액/구해낸 음식) — "입점 매장" 카드
 * 제거. CO2 절감 필드도 제거("19개인데 CO2는 0.0kg"처럼 보이던 버그; 2단계 "통계 리포트"로 이동
 * 예정). 각 카드에 "어제 대비" 증감률(xxxDeltaPercent, LedgerData와 동일 규칙 — 어제 값이 0이면
 * 비교 불가 → null)을 추가했다. todayXxx는 pickedAt(KST) 기준.
 */
public record KpiSummaryDto(
		long totalMemberCount,
		long memberTodayCount,
		long memberWeekCount,
		Integer memberDeltaPercent,
		long totalTransactionCount,
		long todayTransactionCount,
		Integer transactionDeltaPercent,
		long totalRevenue,
		long todayRevenue,
		Integer revenueDeltaPercent,
		long totalRescuedQuantity,
		long todayRescuedQuantity,
		Integer rescuedDeltaPercent
) {
}

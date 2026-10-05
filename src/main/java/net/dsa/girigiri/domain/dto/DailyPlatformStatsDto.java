package net.dsa.girigiri.domain.dto;

import java.time.LocalDate;

/**
 * 추가됨 (2026-10-05) — 통계 대시보드 달력에서 특정 날짜를 클릭했을 때 보여줄 "그 날 하루" 집계.
 * KpiSummaryDto의 todayXxx 필드들과 계산 방식은 같다(픽업 완료 건만 거래/매출/구제량으로 집계,
 * reservedAt 기준 하루), 대상 날짜만 "오늘"이 아니라 파라미터로 받은 임의의 과거 날짜라는 점이 다르다.
 *
 * 변경됨 (2026-10-05) — 지표마다 전월 같은 날짜 대비 증감률(xxxDeltaPercent)을 추가했다.
 * LedgerData.deltaPercent와 같은 규칙: 전월 값이 0이면 비교 자체가 의미 없어 null(화면/문서에서 "-"로 표시).
 */
public record DailyPlatformStatsDto(
		LocalDate date,
		String dateLabel,
		long newMemberCount,
		Integer memberDeltaPercent,
		long newStoreCount,
		Integer storeDeltaPercent,
		long transactionCount,
		Integer transactionDeltaPercent,
		long revenue,
		Integer revenueDeltaPercent,
		long rescuedQuantity,
		Integer rescuedDeltaPercent,
		double co2Kg,
		long cancelledCount,
		long noshowedCount
) {
}

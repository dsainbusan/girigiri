package net.dsa.girigiri.domain.dto;

/**
 * 대시보드 "최근 7일 절감" 요약 카드 — 2026-08-27 신규 (문창호, WBS "판매/폐기 절감 통계 그래프"
 * + "음식 구제 개수 · 환경 뱃지" 서브 지표).
 *
 * rescuedCount   : 최근 7일간 앱으로 팔려서 폐기를 면한 음식 개수 (예약 수량 합, 취소/미결제 제외)
 * recoveredAmount: 그 판매로 매장이 회수한 매출 (폐기했으면 0원이었을 금액)
 * co2Kg          : 절감한 CO₂ 환산량 = rescuedCount × CO2_KG_PER_ITEM
 */
public record WeeklySavingsDto(
		int rescuedCount,
		long recoveredAmount,
		double co2Kg
) {
}

package net.dsa.girigiri.domain.dto;

/**
 * 매장 신뢰도(취소율) 화면용 데이터.
 * cancelRatePercent는 "가게 사정으로 취소된 비율"이다 — 손님이 취소한 건 매장 잘못이 아니라서 분자에서 뺀다.
 */
public record StoreCancelStatsDto(
		long totalReservationCount,
		long storeCancelledCount,
		double cancelRatePercent
) {
}

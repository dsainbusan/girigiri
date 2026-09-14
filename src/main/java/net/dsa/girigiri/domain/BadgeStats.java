package net.dsa.girigiri.domain;

import java.util.Map;

/**
 * 뱃지 판별에 필요한 사용자의 구제·절약 통계 스냅샷.
 * LedgerService.build() 시점에 픽업 완료(status=picked) 내역을 모아 생성한다.
 */
public record BadgeStats(
		int rescuedCount,
		int totalSaved,
		double co2Kg,
		int goalPercent,
		Map<String, Integer> categoryCounts,
		int distinctStoreCount,
		boolean hasNightPickup
) {
	public int getCategoryCount(String category) {
		if (categoryCounts == null || category == null) {
			return 0;
		}
		return categoryCounts.getOrDefault(category, 0);
	}
}

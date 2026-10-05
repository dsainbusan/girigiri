package net.dsa.girigiri.domain.dto;

/**
 * 지역→매장 드릴다운(통계 대시보드 "지역별 현황" 행 클릭) 표의 매장 1행 — 2026-10-01 신규.
 */
public record RegionStoreRowDto(
		Long storeId,
		String storeName,
		String ownerNickname,
		int registeredCount,
		int soldCount,
		int expiredCount,
		Integer sellThroughPercent,
		String tileClass,
		String statusLabel,
		String pickupTimeLabel,
		String lastRegisteredAtLabel,
		String lastActionLabel,
		boolean needsAttention
) {
}

package net.dsa.girigiri.domain.dto;

/**
 * 슈퍼어드민 매장 상세 화면의 최근 7일 구제율 통계 — 2026-09-03, SuperAdminStoreService#getRecentStats
 * 이관 시 도입. StoreController.dashboard()(점주 본인용, "오늘" 기준)와 달리 등록/판매가 없는 날도
 * 의미 있게 보이도록 최근 7일 창으로 계산한다.
 */
public record StoreRecentStatsDto(
		int registeredCount7d,
		int soldCount7d,
		int totalQuantity7d,
		int rescueRate7d
) {
}

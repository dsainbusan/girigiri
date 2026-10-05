package net.dsa.girigiri.domain.dto;

/**
 * 통계 대시보드 "지역별 현황" 시도 1개 (2026-09-30).
 * tileClass/statusLabel은 소진율 구간(60%↑ 양호 / 40~59% 보통 / 40%미만 점검필요 / 매장없음 미진출)을
 * 서비스에서 한 번만 판정해서 넘긴다 — 템플릿(타일맵)과 표가 같은 기준을 따로 계산하다 어긋나는 걸 막는다.
 */
public record RegionStatDto(
		String sido,
		long storeCount,
		int registeredCount,
		int soldCount,
		// 매장은 있지만 최근 7일 등록이 0건이면 null("-" 표시). 매장 자체가 없어도 null.
		Integer sellThroughPercent,
		String tileClass,
		String statusLabel
) {
}

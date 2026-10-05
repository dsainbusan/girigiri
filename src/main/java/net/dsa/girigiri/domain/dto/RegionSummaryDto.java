package net.dsa.girigiri.domain.dto;

import java.util.List;
import java.util.Map;

/**
 * 통계 대시보드 "지역별 현황" 카드 전체 (2026-09-30).
 * tiles는 시도명 → RegionStatDto 맵이다 — 타일맵이 4열×6행 고정 배치(빈 칸 포함, 실제 한국 지도
 * 배치를 흉내낸 모양)라 순차 반복이 아니라 템플릿에서 이름으로 직접 찾아 꽂아야 한다.
 * tableRows = 소진율 낮은 순(미진출/데이터 없음은 맨 뒤) 정렬 리스트.
 */
public record RegionSummaryDto(
		Map<String, RegionStatDto> tiles,
		List<RegionStatDto> tableRows,
		long coveredRegionCount,
		long missingRegionCount,
		long totalStoreCount,
		int totalRegisteredCount,
		int totalSoldCount,
		Integer totalSellThroughPercent
) {
}

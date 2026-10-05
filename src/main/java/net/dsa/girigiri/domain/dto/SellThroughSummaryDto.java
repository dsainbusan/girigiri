package net.dsa.girigiri.domain.dto;

import java.util.List;

/**
 * 통계 대시보드 "마감 상품 소진 현황"(최근 7일) 카드 전체 (2026-09-30).
 * linePoints는 소진율 꺾은선의 SVG polyline points 속성값을 서비스에서 미리 만들어 넘긴다 —
 * viewBox="0 0 100 100" 기준 좌표(x: 0,16.67,...,100 / y: 100-소진율%)라 뷰박스 크기만 맞추면
 * 어떤 컨테이너 폭에도 그대로 늘어난다(preserveAspectRatio="none").
 */
public record SellThroughSummaryDto(
		List<SellThroughDailyDto> days,
		int weekRegisteredTotal,
		int weekSoldTotal,
		Integer weekSellThroughPercent,
		String linePoints
) {
}

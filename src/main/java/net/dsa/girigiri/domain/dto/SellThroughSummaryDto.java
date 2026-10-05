package net.dsa.girigiri.domain.dto;

import java.util.List;

/**
 * 통계 대시보드 "마감 상품 소진 현황"(최근 7일) 카드 전체 (2026-09-30).
 *
 * 변경됨 (2026-10-06) — 소진율 꺾은선(이중축 SVG polyline)을 뺐다. 막대 x좌표(flex 7칸, 중심
 * 기준)와 꺾은선 점 x좌표(viewBox 0~100 가장자리 기준, 6구간)가 서로 다른 좌표계라 구조적으로
 * 어긋나 있었고, 소진율은 이미 막대 아래 텍스트(sellThroughPercent)로도 보여주고 있어 중복이었다
 * — linePoints 필드 자체를 제거했다.
 */
public record SellThroughSummaryDto(
		List<SellThroughDailyDto> days,
		int weekRegisteredTotal,
		int weekSoldTotal,
		Integer weekSellThroughPercent
) {
}

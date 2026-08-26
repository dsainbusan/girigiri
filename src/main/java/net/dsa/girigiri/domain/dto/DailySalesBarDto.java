package net.dsa.girigiri.domain.dto;

/**
 * 추가됨 — 왜: 대시보드 "오늘 판매 현황" 도넛 카드 옆에 최근 7일 판매량을 세로 막대그래프로 보여주고
 * 싶다는 요청(WBS 3.0). heightPercent는 그 주 최댓값 대비 비율을 컨트롤러에서 미리 계산해서 넘긴다
 * (Thymeleaf에서 나눗셈/반올림을 직접 하면 실수하기 쉬움 — 도넛 % 계산 때와 같은 이유).
 */
public record DailySalesBarDto(
		String dateLabel,
		int count,
		int heightPercent,
		boolean isToday
) {
}

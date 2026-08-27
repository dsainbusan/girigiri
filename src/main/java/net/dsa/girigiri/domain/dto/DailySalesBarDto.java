package net.dsa.girigiri.domain.dto;

/**
 * 대시보드 "최근 7일 판매 / 폐기" 세로 막대 1개.
 *
 * 변경됨 (2026-08-27, 문창호) — 왜: WBS "판매/폐기 절감 통계 그래프" — 기존엔 판매량만 보여줬는데
 * 항목명대로 폐기량도 같이 봐야 해서, 막대를 판매(초록)/폐기(빨강) 2색 스택으로 바꿨다.
 * soldHeightPercent / wasteHeightPercent 는 그 주 최댓값(판매+폐기 합) 대비 비율을 컨트롤러에서
 * 미리 계산해서 넘긴다 (Thymeleaf에서 나눗셈/반올림 직접 하면 실수하기 쉬움).
 */
public record DailySalesBarDto(
		String dateLabel,
		int soldCount,
		int wasteCount,
		int soldHeightPercent,
		int wasteHeightPercent,
		boolean isToday
) {
}

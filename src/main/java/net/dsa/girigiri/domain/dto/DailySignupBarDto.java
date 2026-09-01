package net.dsa.girigiri.domain.dto;

/**
 * 슈퍼어드민 대시보드 "최근 7일 신규 가입" 세로 막대 1개. storeView의 DailySalesBarDto와 같은 패턴
 * (막대 높이는 그 주 최댓값 대비 %를 컨트롤러가 미리 계산해서 넘김 — Thymeleaf에서 나눗셈 직접 안 함).
 */
public record DailySignupBarDto(
		String dateLabel,
		int count,
		int heightPercent,
		boolean isToday
) {
}

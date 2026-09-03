package net.dsa.girigiri.domain.dto;

import java.util.List;

/**
 * 슈퍼어드민 대시보드(SuperAdminController#dashboard) 집계 결과 — 2026-09-03, 레이어 규칙 2단계로
 * SuperAdminDashboardService#getDashboardStats 이관 시 도입.
 */
public record SuperAdminDashboardStatsDto(
		long pendingInquiryCount,
		long pendingComplaintCount,
		List<DailySignupBarDto> weeklySignupBars,
		List<CalendarDayDto> calendarDays,
		String calendarMonthLabel
) {
}

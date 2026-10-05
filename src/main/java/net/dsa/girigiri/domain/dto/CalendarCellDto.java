package net.dsa.girigiri.domain.dto;

import java.time.LocalDate;

/** 추가됨 (2026-10-05) — 통계 대시보드 달력 칸 하나. DashboardCalendarDto가 42개(6주)를 들고 있다. */
public record CalendarCellDto(
		LocalDate date,
		int dayOfMonth,
		boolean currentMonth,
		boolean isToday,
		boolean isSelected,
		boolean isFuture
) {
}

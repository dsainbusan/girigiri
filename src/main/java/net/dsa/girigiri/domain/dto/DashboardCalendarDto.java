package net.dsa.girigiri.domain.dto;

import java.util.List;

/**
 * 추가됨 (2026-10-05) — 통계 대시보드 "일별 현황" 달력. 월 이동은 month 쿼리스트링(yyyy-MM)으로,
 * 날짜 클릭은 date 쿼리스트링(yyyy-MM-dd)으로 처리한다(서버 재렌더링, 별도 AJAX 없음).
 */
public record DashboardCalendarDto(
		String monthLabel,
		String prevMonthParam,
		String nextMonthParam,
		boolean nextDisabled,
		List<CalendarCellDto> cells
) {
}

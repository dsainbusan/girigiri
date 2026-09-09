package net.dsa.girigiri.domain.dto;

import java.util.List;

/**
 * 슈퍼어드민 대시보드(SuperAdminController#dashboard) 집계 결과 — 2026-09-03, 레이어 규칙 2단계로
 * SuperAdminDashboardService#getDashboardStats 이관 시 도입.
 *
 * 변경됨 (2026-09-09) — "1:1 문의"·"신고 접수" 카드가 둘 다 /superadmin/reports로만 가서 같은
 * 화면(신고 접수 탭)에 떨어지는 게 기능 중복처럼 보인다는 지적 — 원래 하나였던 pendingInquiryCount는
 * 매장 문의(storeId 있음)/유저 문의(storeId 없음)를 합친 값이라 애초에 tab 하나로 못 보냈다. reports.html
 * 탭 구분(report/store/user) 기준과 똑같이 둘로 쪼개서, 대시보드에서도 문의 카드를 매장/유저 둘로 나누고
 * 각각 자기 탭으로 보내게 했다.
 */
public record SuperAdminDashboardStatsDto(
		long pendingStoreInquiryCount,
		long pendingUserInquiryCount,
		long pendingComplaintCount,
		List<DailySignupBarDto> weeklySignupBars,
		List<CalendarDayDto> calendarDays,
		String calendarMonthLabel
) {
}

package net.dsa.girigiri.domain.dto;

import java.util.List;

/**
 * 추가됨 (2026-09-09) — 슈퍼어드민 "플랫폼 통계"(/superadmin/stats) 화면 집계 결과. 대시보드
 * "오늘 플랫폼 지표"의 거래량/구제량/매출 카드가 아직 데모 값이고(dashboard.html 상단 TODO 참고)
 * 이 셋을 따로 보여주는 화면도 없었다는 지적으로 신설 — 카드를 누르면 여기로 온다.
 * SuperAdminDashboardService#getPlatformStats가 계산한다.
 *
 * 변경됨 (2026-09-09) — 기간 필터(오늘/7일/30일/전체)와 일별 추이 그래프 요청으로 period/periodLabel/
 * dailyTransactionBars 추가. period는 요약 카드·매장별 표에 적용되고(주문 접수 시점 reservedAt 기준),
 * dailyTransactionBars는 기간 선택과 무관하게 항상 최근 14일 픽업완료 추이를 보여준다(dashboard.html의
 * "최근 7일 신규 가입" 막대그래프와 같은 이유 — "오늘"만 골랐을 때 막대가 1개뿐이면 추이를 볼 수
 * 없어서, 그래프는 선택한 기간과 분리했다).
 */
public record PlatformStatsDto(
		String period,
		String periodLabel,
		long totalTransactionCount,
		long totalRescuedQuantity,
		long totalRevenue,
		double totalCo2Kg,
		List<StoreStatsRowDto> storeRows,
		List<DailySignupBarDto> dailyTransactionBars
) {
}

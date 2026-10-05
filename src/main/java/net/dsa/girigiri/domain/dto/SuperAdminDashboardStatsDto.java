package net.dsa.girigiri.domain.dto;

import java.util.List;

/**
 * 슈퍼어드민 "통계 대시보드"(SuperAdminController#dashboard) 집계 결과.
 *
 * 2026-09-30 전면 개편 — 기존 "오늘 플랫폼 지표" 4카드 + 문의/신고/입점 개별 info-list 3개 +
 * 최근 7일 신규가입 막대그래프 + 이달 캘린더 구성을, KPI 5카드(KpiSummaryDto) + 처리 대기
 * 통합 리스트(PendingQueueRowDto, 순서 고정)로 재구성한다. 신규가입 막대그래프/캘린더는 새
 * 명세에 없어 제거(사용자 확인 완료) — 두 위젯만 쓰던 DailySignupBarDto/CalendarDayDto 중
 * CalendarDayDto는 이제 아무 데서도 안 써서 파일째 삭제했다(DailySignupBarDto는 /superadmin/stats
 * 거래량 추이 차트가 계속 쓰므로 유지).
 */
public record SuperAdminDashboardStatsDto(
		KpiSummaryDto kpi,
		List<PendingQueueRowDto> pendingQueue,
		SellThroughSummaryDto sellThrough,
		RegionSummaryDto regions,
		String asOfLabel,
		String updatedAtLabel,
		long updatedAtEpochMillis
) {
	// "처리 대기 총 N건" 소제목용 — 템플릿에서 직접 합산하지 않는다(OGNL 투영 문법은 Thymeleaf 기본
	// SpringEL에서 안 먹힌다).
	public long totalPendingCount() {
		return pendingQueue.stream().mapToLong(PendingQueueRowDto::count).sum();
	}
}

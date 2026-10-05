package net.dsa.girigiri.domain.dto;

/**
 * 슈퍼어드민 "통계 대시보드" 처리 대기 행 1개 (2026-09-30, 대시보드 리디자인).
 * 순서는 항상 신고 접수 → 입점 신청 → 매장 문의 → 유저 문의로 고정한다(건수와 무관) — 신고가
 * 가장 긴급도가 높은 항목이라는 운영 판단을 화면 순서로 그대로 드러낸다.
 */
public record PendingQueueRowDto(
		String label,
		String iconId,
		long count,
		// 대기 중인 것 중 가장 오래된 건까지 걸린 시간(예: "3시간 전"). count가 0이면 null.
		String oldestAgoLabel,
		String linkUrl,
		// 신고 접수에만 true — "우선 처리" danger 배지를 추가로 보여준다.
		boolean priority,
		// 추가됨 (2026-10-06) — 가장 오래된 건이 SLA(신고 24시간/나머지 72시간, DashboardPolicy)를
		// 넘겼으면 true. count가 0이면 항상 false.
		boolean overSla
) {
}

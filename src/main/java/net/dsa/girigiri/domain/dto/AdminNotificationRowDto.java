package net.dsa.girigiri.domain.dto;

public record AdminNotificationRowDto(
		Long id,
		String icon,           // type별 이모지 — NotificationService에서 매핑
		String message,
		String linkUrl,        // null이면 클릭해도 이동 없이 읽음 처리만
		boolean read,
		String category,       // "MEMBER" | "BOARD" | "RESERVATION" — 알림 패널 탭 필터용
		String dateLabel,       // "09월 17일 (토)" — 패널의 날짜 그룹 헤더용
		String timeLabel        // "11:59"
) {
}

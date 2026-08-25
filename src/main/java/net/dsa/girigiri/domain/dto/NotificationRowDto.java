package net.dsa.girigiri.domain.dto;

public record NotificationRowDto(
		Long id,
		String icon,           // type별 이모지 — NotificationService에서 매핑
		String message,
		String linkUrl,        // null이면 클릭해도 이동 없이 읽음 처리만
		boolean read,
		String createdAtLabel  // "오늘" | "어제" | "N일 전"
) {
}

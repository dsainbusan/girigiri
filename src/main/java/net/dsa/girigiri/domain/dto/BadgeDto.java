package net.dsa.girigiri.domain.dto;

/**
 * 뱃지 도감 화면 및 프로필 연동을 위한 DTO.
 */
public record BadgeDto(
		String code,
		String name,
		String icon,
		String category,
		String description,
		String conditionLabel,
		boolean unlocked,
		int progressPercent,
		String progressText,
		boolean isRepresentative,
		String earnedDateLabel  // "2026.09.12 달성" — 미해금이면 null
) {}

package net.dsa.girigiri.domain.dto;

/**
 * "오늘의 구제 자동 등록" 템플릿 목록(storeView/templates.html)용 화면 전용 데이터 — 2026-08-26 신규 (문창호).
 * weekdaysLabel/promptTimeLabel은 컨트롤러에서 사람이 읽기 좋은 형태로 미리 만들어 넘긴다.
 */
public record TemplateRowDto(
		Long id,
		String name,
		String imageUrl,
		int originalPrice,
		int defaultQuantity,
		String weekdaysLabel,
		String promptTimeLabel,
		boolean active
) {
}

package net.dsa.girigiri.domain.dto;

public record InquiryRowDto(
		Long id,
		String title,
		String authorName,
		String storeName,   // null이면 일반 문의
		int commentCount,
		String createdAtLabel,
		boolean canDelete    // 작성자 본인이거나 관리자일 때만 true — 목록에서 삭제 버튼 노출에 쓴다
) {
}

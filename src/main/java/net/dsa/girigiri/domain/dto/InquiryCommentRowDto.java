package net.dsa.girigiri.domain.dto;

public record InquiryCommentRowDto(
		Long id,
		String authorName,
		String content,
		String createdAtLabel,
		boolean canDelete   // 작성자 본인이거나 관리자일 때만 true — 삭제 버튼 노출에 쓴다
) {
}

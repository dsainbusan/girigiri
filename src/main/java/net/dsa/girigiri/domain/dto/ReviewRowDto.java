package net.dsa.girigiri.domain.dto;

public record ReviewRowDto(
		Long id,
		String reviewerName,
		int rating,
		String content,
		String imageUrl,         // 사진 리뷰. null/빈 값이면 사진 없음
		String createdAtLabel,   // "오늘" | "어제" | "N일 전"
		boolean mine,            // 지금 로그인한 사용자가 쓴 리뷰인지 — 목록에서 "수정" 버튼 노출에 쓴다
		boolean edited,          // 수정된 적 있는 리뷰인지 — "수정됨" 표시용
		boolean canDelete        // 작성자 본인이거나 관리자일 때만 true — 삭제 버튼 노출에 쓴다
) {
}

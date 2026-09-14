package net.dsa.girigiri.domain.dto;

public record InquiryRowDto(
		Long id,
		String title,
		String authorName,
		String storeName,   // null이면 일반 문의
		int commentCount,
		// 추가됨 (강노은, 2026-09-14, QA 발견) — 왜: "답변완료" 배지를 commentCount > 0으로만
		// 판단했더니, 작성자 본인이 후속 댓글을 남긴 것만으로도 사장님/운영자 답변 없이 "답변완료"로
		// 뜨는 문제가 있었다(직접 재현 확인). 작성자 본인이 아닌 사람(가게 사장님/운영자)이 남긴
		// 댓글이 하나라도 있어야 true — InquiryService.toRowDtos() 참고.
		boolean answered,
		String createdAtLabel,
		boolean canDelete    // 작성자 본인이거나 관리자일 때만 true — 목록에서 삭제 버튼 노출에 쓴다
) {
}

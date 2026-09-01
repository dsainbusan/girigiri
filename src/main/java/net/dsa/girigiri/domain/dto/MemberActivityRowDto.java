package net.dsa.girigiri.domain.dto;

import java.time.LocalDateTime;

/**
 * 회원 상세 화면 "문의·신고 내역" 한 줄. InquiryEntity(문의)와 ComplaintEntity(신고)를 한 리스트로
 * 합쳐서 최신순으로 보여주려고 만든 통합 DTO — label로 어느 쪽인지 구분한다.
 */
public record MemberActivityRowDto(
		String label,   // "문의" | "신고"
		String title,
		LocalDateTime createdAt,
		String linkUrl
) {
}

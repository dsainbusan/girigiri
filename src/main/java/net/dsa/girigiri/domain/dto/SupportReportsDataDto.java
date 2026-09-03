package net.dsa.girigiri.domain.dto;

import net.dsa.girigiri.domain.entity.ComplaintEntity;
import net.dsa.girigiri.domain.entity.InquiryEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 슈퍼어드민 "신고·문의" 화면(SuperAdminSupportController#reports)용 집계 결과 —
 * 2026-09-03, 레이어 규칙 2단계로 SuperAdminSupportService#getReportsData 이관 시 도입.
 * 페이지 슬라이스(paginate)는 컨트롤러가 하고, 여기엔 정렬까지 끝난 전체 목록을 담는다.
 */
public record SupportReportsDataDto(
		List<ComplaintEntity> allComplaints,
		List<InquiryEntity> storeInquiries,
		List<InquiryEntity> userInquiries,
		Map<Long, Long> commentCounts,
		Map<Long, String> storeNames,
		Map<Long, LocalDateTime> answeredAtByInquiryId,
		Map<Long, String> inquiryAuthorNames
) {
}

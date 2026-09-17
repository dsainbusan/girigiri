package net.dsa.girigiri.domain.dto;

/**
 * 사장님이 리뷰 작성자 닉네임을 눌렀을 때 보여주는 간단 정보 (WBS 3.0 "문의 답변/리뷰 답글",
 * 문창호, 2026-09-17). 전화번호·이메일·정확한 활동지역 같은 민감한 개인정보는 일부러 뺐다 —
 * 리뷰 하나 남겼다고 사장님한테 손님 연락처까지 노출되는 건 손님이 동의한 적 없는 개인정보
 * 공유이기 때문. 뱃지·가입 기간·이 매장 방문 횟수처럼 "리뷰를 해석하는 데 도움이 되는" 맥락 정보만.
 */
public record ReviewerInfoDto(
		String nickname,
		String daysJoinedLabel,   // "가입 47일째"
		long visitCount,          // 이 매장에서 픽업 완료한 횟수
		String badgeIcon,         // 대표 뱃지 아이콘, 없으면 null
		String badgeName          // 대표 뱃지 이름, 없으면 null
) {
}

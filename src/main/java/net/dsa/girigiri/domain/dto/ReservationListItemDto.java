package net.dsa.girigiri.domain.dto;

/**
 * 마이페이지 예약 목록(진행중/픽업완료/노쇼·취소 탭)에 뿌려줄 화면 전용 데이터.
 * Entity를 그대로 화면에 넘기지 않고, 화면에 필요한 값만 이 DTO로 뽑아서 넘긴다.
 *
 * statusBadge는 DB status 컬럼 값 그대로가 아니라, 화면에 보여줄 한글 배지 문구다.
 * (예: DB엔 confirmed 하나뿐이지만, 픽업 시간이 지났는지에 따라 "예약완료"/"픽업대기"로 갈린다.)
 */
// 수정됨 (2026-10-06, 신고 기반 리팩터링) — reportBlockedMessage 추가. null이면 [신고하기] 버튼을
// 보여주고, 아니면 그 문구(예: "픽업 완료된 주문만 신고할 수 있어요.")를 버튼 대신 보여준다 —
// ComplaintService.blockedReportMessage와 동일 판정(ReservationService#toListItemDto에서 계산).
public record ReservationListItemDto(
		Long reservationId,
		String storeName,
		String productName,
		int quantity,
		int totalPrice,
		String pickupTimeDisplay,
		String pickupCode,
		String statusBadge,
		String reportBlockedMessage
) {
}

package net.dsa.girigiri.domain.dto;

/**
 * 마이페이지 예약 목록(진행중/픽업완료/노쇼·취소 탭)에 뿌려줄 화면 전용 데이터.
 * Entity를 그대로 화면에 넘기지 않고, 화면에 필요한 값만 이 DTO로 뽑아서 넘긴다.
 *
 * statusBadge는 DB status 컬럼 값 그대로가 아니라, 화면에 보여줄 한글 배지 문구다.
 * (예: DB엔 confirmed 하나뿐이지만, 픽업 시간이 지났는지에 따라 "예약완료"/"픽업대기"로 갈린다.)
 */
public record ReservationListItemDto(
		Long reservationId,
		String storeName,
		String productName,
		int quantity,
		int totalPrice,
		String pickupTimeDisplay,
		String pickupCode,
		String statusBadge
) {
}

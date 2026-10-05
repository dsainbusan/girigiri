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
		// 추가됨 (2026-10-05) — 왜: 픽업완료 탭에 리뷰를 쓰러 갈 경로가 아예 없었다(구매내역에서
		// 리뷰 작성 진입점 부재, 사용자 리포트로 발견). 리뷰 작성 폼은 가게 상세(storeView/detail.html)
		// 안에만 있어서, 거기로 보내려면 storeId가 필요하다.
		Long storeId,
		String storeName,
		String productName,
		int quantity,
		int totalPrice,
		String pickupTimeDisplay,
		String pickupCode,
		String statusBadge,
		// 추가됨 (2026-10-05) — "주문당 리뷰 1개" 정책. 픽업완료 탭에서 이 주문에 이미 리뷰를 썼는지,
		// 아직 안 썼다면 지금 쓸 수 있는 자격(픽업 후 72시간 이내)이 있는지를 구분해서 보여준다
		// (ReservationService#toListItemDto, ReviewService.canWriteReviewForReservation).
		boolean reviewed,
		boolean reviewEligible
) {
}

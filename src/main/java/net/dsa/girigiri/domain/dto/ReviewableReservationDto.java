package net.dsa.girigiri.domain.dto;

/**
 * 강노은 (2026-10-01) — 가게 상세의 "리뷰 작성" 대상 선택용. 로그인한 유저가 이 가게에서
 * 픽업완료했는데 아직 리뷰를 안 쓴 예약 1건을 나타낸다(ReviewService#getReviewableReservations).
 */
public record ReviewableReservationDto(
		Long id,               // ReservationEntity.id — 리뷰 작성 시 reservationId로 그대로 제출된다
		String productName,    // 예약 당시 상품명 스냅샷
		String pickedAtLabel   // "오늘" | "어제" | "N일 전"
) {
}

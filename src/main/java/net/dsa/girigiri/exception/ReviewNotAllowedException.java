package net.dsa.girigiri.exception;

/**
 * 추가됨 (강노은) — 왜: 리뷰는 그 가게에서 픽업완료한 예약(구매)당 1건만 쓸 수 있다
 * (ReviewService#getReviewableReservations). 화면에서는 자격 있는 예약만 "리뷰 작성" 버튼으로
 * 보여주지만, 폼을 직접 조작해 남의 예약 id·이미 리뷰 쓴 예약 id로 우회 제출하는 경우까지 막으려면
 * 서버(ReviewService#createReview/updateReview)에서도 반드시 다시 검증해야 한다 — 그 검증 실패 시
 * 던지는 예외.
 */
public class ReviewNotAllowedException extends RuntimeException {
	public ReviewNotAllowedException(String message) {
		super(message);
	}
}

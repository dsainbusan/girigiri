package net.dsa.girigiri.exception;

/**
 * 추가됨 (강노은) — 왜: 리뷰는 그 가게에서 예약·픽업까지 완료한 사용자만 쓸 수 있다
 * (ReviewService#canWriteReview). 화면에서는 버튼 자체를 숨기지만, 폼을 직접 조작해 우회 제출하는
 * 경우까지 막으려면 서버(ReviewService#submitReview)에서도 반드시 다시 검증해야 한다 — 그 검증
 * 실패 시 던지는 예외.
 */
public class ReviewNotAllowedException extends RuntimeException {
	public ReviewNotAllowedException(String message) {
		super(message);
	}
}

package net.dsa.girigiri.exception;

/**
 * 로그인은 했지만, 본인 소유가 아닌 예약(취소 / 완료 화면 / QR / 영수증 등)에
 * URL의 예약 번호(id)만 바꿔서 접근하려고 할 때 던진다.
 */
public class ReservationAccessDeniedException extends RuntimeException {

	public ReservationAccessDeniedException(String message) {
		super(message);
	}
}

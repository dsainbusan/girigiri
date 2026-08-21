package net.dsa.girigiri.exception;

/** 이미 픽업 완료됐거나, 이미 취소/노쇼 처리된 예약을 또 취소하려고 할 때 던진다. */
public class CancellationNotAllowedException extends RuntimeException {

	public CancellationNotAllowedException(String message) {
		super(message);
	}
}

package net.dsa.girigiri.exception;

/** 이미 수락(ready)됐거나, 픽업/취소/노쇼 처리된 예약을 또 수락 처리하려고 할 때 던진다. */
public class AcceptNotAllowedException extends RuntimeException {

	public AcceptNotAllowedException(String message) {
		super(message);
	}
}

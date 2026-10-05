package net.dsa.girigiri.exception;

/** 픽업 대기(ready) 상태가 아니거나, 아직 픽업 예정 시각이 지나지 않은 예약을 노쇼 처리하려고 할 때 던진다. */
public class NoShowNotAllowedException extends RuntimeException {

	public NoShowNotAllowedException(String message) {
		super(message);
	}
}

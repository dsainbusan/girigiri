package net.dsa.girigiri.exception;

/** 이미 픽업했거나, 취소/노쇼 처리됐거나, 아직 결제가 안 된 예약을 픽업 처리하려고 할 때 던진다. */
public class PickupNotAllowedException extends RuntimeException {

	public PickupNotAllowedException(String message) {
		super(message);
	}
}

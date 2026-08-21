package net.dsa.girigiri.exception;

/** 매장 마지막 픽업시간을 넘겨서(현재시간 + 준비시간 > 마지막 픽업시간) 오늘은 더 이상 주문을 받을 수 없을 때 던진다. */
public class OrderNotAllowedException extends RuntimeException {

	public OrderNotAllowedException(String message) {
		super(message);
	}
}

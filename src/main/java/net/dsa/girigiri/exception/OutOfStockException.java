package net.dsa.girigiri.exception;

/** 마감세일 상품 재고가 요청 수량보다 부족할 때 던진다 (동시 예약으로 매진된 경우 포함). */
public class OutOfStockException extends RuntimeException {

	public OutOfStockException(String message) {
		super(message);
	}
}

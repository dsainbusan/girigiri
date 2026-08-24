package net.dsa.girigiri.exception;

/**
 * PortOne 결제 검증에 실패했을 때 던진다 — 결제 미완료(status != PAID), 결제 금액 불일치,
 * 이미 처리된(pending이 아닌) 예약에 대한 중복 검증 요청 등이 여기 해당한다.
 * ReservationController의 confirm-payment API에서 이 예외를 잡아 JSON 에러 응답으로 변환한다.
 */
public class PaymentVerificationException extends RuntimeException {

	public PaymentVerificationException(String message) {
		super(message);
	}
}

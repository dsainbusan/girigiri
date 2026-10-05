package net.dsa.girigiri.util;

/**
 * 결제 검증/환불을 수행하는 PG 연동 추상화. 2026-10-06, 신고 기반 리팩터링(관리자 환불) 요청으로 신설.
 *
 * 지금까지 ReservationService는 PortOneClient를 직접 주입받아 썼다 — 이 인터페이스는 기존 경로를
 * 건드리지 않고(리스크 최소화), 이번에 새로 생기는 AdminRefundService(신고 처리 환불)만 이 추상화를
 * 통해 PG를 호출하게 한다. 구현체는 FakePaymentGateway 하나뿐이다 — 실제로는 PortOneClient로 나이스
 * 테스트 채널을 호출하는 "진짜 HTTP 연동"이지만, 프로덕션 PG 계약(실 거래대금)이 아니라 테스트
 * 채널이라는 의미에서 "Fake"라고 부른다(PortOneClient 클래스 주석 참고). 나중에 실 PG로 바뀌면 이
 * 인터페이스의 새 구현체(예: RealPaymentGateway)만 추가하고 주입만 바꾸면 된다.
 */
public interface PaymentGateway {

	PaymentVerifyResult verifyPayment(String paymentId, int expectedAmount);

	PaymentCancelResult cancelPayment(String paymentId, String reason);

	record PaymentVerifyResult(boolean paid, String transactionId, Integer amount, String failReason) {
	}

	record PaymentCancelResult(boolean cancelled, String transactionId, String failReason) {
	}
}

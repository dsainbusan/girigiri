package net.dsa.girigiri.util;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * PaymentGateway의 유일한 구현체. 실제로는 PortOneClient로 나이스(NICE) 테스트 채널을 호출하는
 * 진짜 HTTP 연동이다 — "Fake"는 "아무 일도 안 하는 목(mock)"이 아니라 "테스트 채널이라 진짜
 * 프로덕션 결제가 아니다"라는 뜻이다(PortOneClient 클래스 주석 참고). AdminRefundService 전용.
 *
 * PortOne 환불(cancel) 응답 바디는 지금 PG사 고유 취소 거래ID를 파싱하지 않는다(PortOneClient
 * 참고) — pg_refund_tid엔 그 대신 이 환불 호출에 쓴 merchantUid(=paymentId)를 담아서, 최소한
 * "어떤 결제 건을 취소했는지"는 추적 가능하게 한다.
 */
@Component
@RequiredArgsConstructor
public class FakePaymentGateway implements PaymentGateway {

	private final PortOneClient portOneClient;

	@Override
	public PaymentVerifyResult verifyPayment(String paymentId, int expectedAmount) {
		PortOneClient.PortOneVerifyResult result = portOneClient.verifyPayment(paymentId, expectedAmount);
		return new PaymentVerifyResult(result.paid(), result.transactionId(), result.amount(), result.failReason());
	}

	@Override
	public PaymentCancelResult cancelPayment(String paymentId, String reason) {
		PortOneClient.PortOneCancelResult result = portOneClient.cancelPayment(paymentId, reason);
		return new PaymentCancelResult(result.cancelled(), result.cancelled() ? paymentId : null, result.failReason());
	}
}

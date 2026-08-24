package net.dsa.girigiri.domain.dto;

/**
 * 결제창을 띄우기 직전, "예약 준비(prepare)" API의 응답.
 * 프론트(checkout.html)가 이 값들을 그대로 PortOne.requestPayment()에 넘긴다 —
 * paymentId는 서버가 생성한 값(PaymentEntity.merchantUid와 동일)이라 위조할 수 없고,
 * 나중에 confirm-payment 단계에서 서버가 이 값 그대로 PortOne에 재조회해서 검증한다.
 */
public record ReservationPrepareResponseDto(
		Long reservationId,
		String paymentId,
		int amount,
		String orderName
) {
}

package net.dsa.girigiri.domain.dto;

/**
 * 결제창을 띄우기 직전, "예약 준비(prepare)" API의 응답.
 * 프론트(checkout.html)가 이 값들을 그대로 PortOne.requestPayment()에 넘긴다 —
 * paymentId는 서버가 생성한 값(PaymentEntity.merchantUid와 동일)이라 위조할 수 없고,
 * 나중에 confirm-payment 단계에서 서버가 이 값 그대로 PortOne에 재조회해서 검증한다.
 *
 * 변경됨 (2026-09-07, 채채 확인 — 체크아웃 쿠폰 적용 연동) — 왜: prepareReservation()에 쿠폰을
 * 같이 넘기면서, 이미 쓴 쿠폰/기한 지난 쿠폰처럼 검증에 실패하는 경우가 생겼다. 이 API는 AJAX라
 * 예외가 그대로 올라가면 GlobalExceptionHandler가 HTML 에러 페이지 이름을 돌려주게 되고, 프론트가
 * 그걸 JSON으로 파싱하려다 깨진다 — PaymentConfirmResponseDto와 동일한 success/message 패턴으로
 * 바꿔서, 컨트롤러가 예외를 직접 잡아 실패 메시지를 JSON으로 돌려주게 했다.
 */
public record ReservationPrepareResponseDto(
		boolean success,
		Long reservationId,
		String paymentId,
		int amount,
		String orderName,
		String message
) {
	public static ReservationPrepareResponseDto success(Long reservationId, String paymentId, int amount, String orderName) {
		return new ReservationPrepareResponseDto(true, reservationId, paymentId, amount, orderName, null);
	}

	public static ReservationPrepareResponseDto failure(String message) {
		return new ReservationPrepareResponseDto(false, null, null, 0, null, message);
	}
}

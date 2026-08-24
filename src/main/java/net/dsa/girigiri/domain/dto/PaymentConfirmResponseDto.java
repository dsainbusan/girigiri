package net.dsa.girigiri.domain.dto;

/**
 * 결제 검증(confirm-payment) API의 응답. success=false면 message에 실패 사유(결제 미완료/금액 불일치 등)가
 * 담기고, 이때 예약은 이미 서버에서 "cancelled"로 자동 처리되고 재고도 복구된 상태다
 * (ReservationService.confirmPayment 참고) — 프론트는 message만 그대로 사용자에게 보여주면 된다.
 */
public record PaymentConfirmResponseDto(
		boolean success,
		String redirectUrl,
		String message
) {
	public static PaymentConfirmResponseDto success(String redirectUrl) {
		return new PaymentConfirmResponseDto(true, redirectUrl, null);
	}

	public static PaymentConfirmResponseDto failure(String message) {
		return new PaymentConfirmResponseDto(false, null, message);
	}
}

package net.dsa.girigiri.domain.dto;

/**
 * 매장 취소 화면에서 "취소 가능한 예약" 목록(드롭다운)에 뿌려줄 화면 전용 데이터.
 * 픽업 코드를 직접 타이핑하지 않고 이 목록에서 골라 취소할 수 있게 한다.
 */
public record CancellableReservationDto(
		Long reservationId,
		String pickupCode,
		String productName,
		int quantity,
		int totalPrice,
		String storeName,
		String statusBadge   // "주문 확인중" / "픽업 가능" 등 — 손님이 이미 픽업 가능 상태인지 매장이 알 수 있게
) {
}

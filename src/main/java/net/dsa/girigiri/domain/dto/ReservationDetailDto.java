package net.dsa.girigiri.domain.dto;

/**
 * 추가됨 (2026-09-09) — 슈퍼어드민 "매장 주문 내역"(storeOrders.html)·"회원 예약 내역"
 * (memberReservations.html) 표에서 한 건을 클릭하면 보이는 예약 상세. "고객이 무슨 카드로,
 * 언제 결제했는지, 픽업은 언제 했는지 자세히 보고 싶다"는 요청으로 추가했다.
 *
 * ReservationOrderItemDto/ReservationUserOrderItemDto(목록용, 요약 정보)와 달리 결제(PaymentEntity)
 * 정보까지 조인해서 보여준다 — payMethodLabel/paymentStatusLabel/paidAtDisplay/paymentAmount.
 * 결제 레코드가 아직 없거나(READY 전) 못 찾으면 이 네 필드는 "-"/null로 채워진다
 * (ReservationService#toDetailDto 참고).
 */
public record ReservationDetailDto(
		Long reservationId,
		Long storeId,
		String storeName,
		Long buyerId,
		String buyerName,
		String buyerEmail,
		String productName,
		int quantity,
		int totalPrice,
		String pickupCode,
		String statusLabel,
		String statusVariant,
		String reservedAtDisplay,
		String acceptedAtDisplay,
		String pickupTimeDisplay,
		String pickedAtDisplay,
		String cancelInfo,
		String payMethodLabel,
		String paymentStatusLabel,
		String paidAtDisplay,
		Integer paymentAmount
) {
}

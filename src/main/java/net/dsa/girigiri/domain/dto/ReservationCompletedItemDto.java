package net.dsa.girigiri.domain.dto;

/**
 * 추가됨 — 왜: 점주가 "손님이 픽업해서 거래 완료한" 내역을 볼 화면이 없었다(WBS 3.0).
 * ReservationIncomingItemDto와 필드가 거의 같지만, 여기는 주문 시각뿐 아니라
 * "언제 픽업 완료됐는지"(pickedAtDisplay)가 핵심 정보라 별도 DTO로 뺐다.
 */
public record ReservationCompletedItemDto(
		Long reservationId,
		String productName,
		int quantity,
		int totalPrice,
		String pickupCode,
		String reservedAtDisplay,
		String pickedAtDisplay
) {
}

package net.dsa.girigiri.domain.dto;

/**
 * 픽업 화면에서 QR을 스캔한 직후, 실제로 "픽업 완료 처리" 버튼을 누르기 전에 어떤 예약인지
 * (매장/상품명/수량/금액) 미리 보여주기 위한 조회 전용 응답 DTO.
 *
 * ReservationService.confirmPickup()과 달리 상태를 실제로 바꾸지 않고 조회만 하기 때문에,
 * 몇 번을 호출해도 안전하다 (스캔이 여러 번 잡혀도 문제없음).
 */
public record PickupLookupResponseDto(
		boolean ok,
		String message,
		String storeName,
		String productName,
		Integer quantity,
		Integer totalPrice
) {
	public static PickupLookupResponseDto notFound() {
		return new PickupLookupResponseDto(false, "픽업 코드를 찾을 수 없어요.", null, null, null, null);
	}

	public static PickupLookupResponseDto blocked(String message) {
		return new PickupLookupResponseDto(false, message, null, null, null, null);
	}

	public static PickupLookupResponseDto success(String storeName, String productName, int quantity, int totalPrice) {
		return new PickupLookupResponseDto(true, null, storeName, productName, quantity, totalPrice);
	}
}

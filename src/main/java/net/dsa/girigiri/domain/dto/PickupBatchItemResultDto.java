package net.dsa.girigiri.domain.dto;

/**
 * 픽업 화면에서 여러 예약을 한 번에 처리("담아뒀다가 일괄 처리")할 때, 각 코드별 처리 결과.
 * 코드 하나가 실패(이미 픽업됨/취소됨 등)해도 나머지는 계속 처리되도록, 컨트롤러가 코드마다
 * 개별로 예외를 잡아서 이 DTO로 감싸 돌려준다 — 배치 전체가 하나의 실패로 막히지 않게 하기 위함.
 */
public record PickupBatchItemResultDto(
		String pickupCode,
		boolean ok,
		String message,
		String productName,
		Integer quantity
) {
	public static PickupBatchItemResultDto success(String pickupCode, String productName, int quantity) {
		return new PickupBatchItemResultDto(pickupCode, true, null, productName, quantity);
	}

	public static PickupBatchItemResultDto failure(String pickupCode, String message) {
		return new PickupBatchItemResultDto(pickupCode, false, message, null, null);
	}
}

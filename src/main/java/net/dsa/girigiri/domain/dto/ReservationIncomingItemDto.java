package net.dsa.girigiri.domain.dto;

/**
 * 사장님용 "들어온 예약 확인/수락" 목록 화면에 뿌려줄 화면 전용 데이터.
 * 아직 매장이 수락(ready로 전환)하지 않은 confirmed 상태 예약들만 여기 보여준다.
 */
public record ReservationIncomingItemDto(
		Long reservationId,
		String productName,
		int quantity,
		int totalPrice,
		String pickupCode,
		String reservedAtDisplay,
		// 추가됨 (2026-09-30) — 픽업 대기중(ready) 목록에서 "노쇼 처리" 버튼을 픽업 예정 시각이
		// 지난 건에만 보여주기 위해. incoming(confirmed) 목록에도 같은 DTO를 재사용하지만 거기선 안 쓴다.
		String pickupTimeDisplay,
		boolean pickupTimePassed
) {
}

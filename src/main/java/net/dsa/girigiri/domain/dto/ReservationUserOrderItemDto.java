package net.dsa.girigiri.domain.dto;

/**
 * 추가됨 (2026-09-09) — 슈퍼어드민 "회원 상세 → 예약 내역" 화면용. ReservationOrderItemDto(매장
 * 기준, 구매자 표시)의 반대 방향 — 이 유저가 어느 매장에서 뭘 주문했는지 최신순으로 보여준다.
 *
 * cancellable: 지금 상태가 취소 가능한 상태(pending/confirmed/ready, ReservationService의
 * CANCELLABLE_STATUSES와 동일 기준)인지 — true일 때만 화면에 취소 버튼을 보여준다. 취소는
 * ReservationService#cancelByAdmin을 그대로 재사용(환불·쿠폰복구까지 처리, 신고 처리 화면과 동일 경로).
 *
 * statusVariant: ReservationOrderItemDto와 같은 값("waiting"/"ready"/"done"/"cancelled"/"noshow") —
 * "취소"와 "노쇼"를 서로 다른 배지 색(빨강/주황)으로 구분해서 보여준다(2026-09-09).
 */
public record ReservationUserOrderItemDto(
		Long reservationId,
		Long storeId,
		String storeName,
		String productName,
		int quantity,
		int totalPrice,
		String statusLabel,
		String statusVariant,
		String reservedAtDisplay,
		String cancelInfo,
		boolean cancellable
) {
}

package net.dsa.girigiri.domain.dto;

/**
 * 추가됨 (2026-09-09) — 슈퍼어드민 "전체 주문 내역"(/superadmin/orders) 화면용. 매장 상세의 "주문
 * 내역"(ReservationOrderItemDto, 한 매장 기준이라 storeName이 필요 없음)·회원 상세의 "예약
 * 내역"(ReservationUserOrderItemDto, 한 회원 기준이라 buyerName이 필요 없음)과 달리, 이 화면은
 * 플랫폼 전체를 훑는 목록이라 매장명·주문자를 한 행에 같이 보여줘야 해서 둘을 합친 DTO를 새로 뒀다.
 * 상태 라벨/배지 계산(resolveStatusBadge/orderStatusVariant/orderCancelInfo)은 그 둘과 동일한
 * ReservationService 헬퍼를 그대로 재사용한다 — "상태 라벨이 화면마다 갈린다"는 지적을 다시 반복하지
 * 않기 위해서.
 */
public record ReservationAllOrderRowDto(
		Long reservationId,
		Long storeId,
		String storeName,
		String buyerName,
		String productName,
		int quantity,
		int totalPrice,
		String statusLabel,
		String statusVariant,
		String reservedAtDisplay,
		String cancelInfo
) {
}

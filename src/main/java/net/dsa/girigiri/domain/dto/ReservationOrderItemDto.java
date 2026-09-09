package net.dsa.girigiri.domain.dto;

/**
 * 추가됨 (2026-09-09) — 슈퍼어드민 "매장 주문 내역" 화면(매장 상세 → 주문 내역)에 뿌려줄 데이터.
 * 점주/손님용 화면들(ReservationIncomingItemDto/ReservationCompletedItemDto)과 달리 상태별로
 * 목록을 나누지 않고 한 목록 안에 전부(대기/확정/픽업가능/픽업완료/취소/노쇼) 담는다 — 운영자가
 * "유저와 매장 사이에 어떤 주문이 오갔는지" 한눈에 보는 용도라 buyerName(구매자)도 같이 담는다.
 *
 * cancelInfo: 취소/노쇼가 아니면 null. 취소면 "매장 취소 · 재고 부족" 처럼 누가·왜 취소했는지,
 * 노쇼면 "노쇼 처리됨"만 담는다.
 *
 * statusVariant: "waiting"(결제대기/확인중) / "ready"(픽업가능) / "done"(픽업완료) / "cancelled"(취소) /
 * "noshow"(노쇼) — 화면의 badge 색상과 매핑하는 값. cancelled/noshow는 둘 다 "안 좋은 결과"지만
 * 같은 빨간 배지면 구분이 안 된다는 피드백으로(2026-09-09) 서로 다른 색(빨강/주황)을 쓴다.
 * StockItemDto.statusVariant와 같은 패턴.
 */
public record ReservationOrderItemDto(
		Long reservationId,
		String buyerName,
		String productName,
		int quantity,
		int totalPrice,
		String pickupCode,
		String statusLabel,
		String statusVariant,
		String reservedAtDisplay,
		String cancelInfo
) {
}

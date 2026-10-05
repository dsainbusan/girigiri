package net.dsa.girigiri.domain.entity;

/** 신고 처리 환불(RefundEntity) 처리 상태. PayStatus와 동일한 이유로 enum으로 둔다(오타 방지). */
public enum RefundStatus {
	REQUESTED,  // PG 호출 전/호출 중 — 저장은 됐지만 아직 성공/실패가 확정되지 않음
	DONE,       // PG 환불 성공, 주문 refunded + 신고 처리완료까지 끝남
	FAILED      // PG 환불 실패 — 주문/신고 상태는 그대로 유지(재시도 가능)
}

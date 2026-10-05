package net.dsa.girigiri.domain.entity;

/** 관리자 환불 1건의 진행 상태 (RefundEntity, 대문자로 저장). */
public enum RefundStatus {
	REQUESTED,   // PG 환불 요청 직전
	DONE,        // 환불 성공
	FAILED       // PG 환불 실패 — 같은 주문으로 재시도 가능
}

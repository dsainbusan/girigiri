package net.dsa.girigiri.exception;

/** 추가됨 (2026-10-06, 신고 기반 리팩터링) — 관리자 환불 조건(픽업완료·미환불·신고와 연결된 주문)을 못 채웠을 때 던진다. */
public class AdminRefundNotAllowedException extends RuntimeException {
	public AdminRefundNotAllowedException(String message) {
		super(message);
	}
}

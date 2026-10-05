package net.dsa.girigiri.exception;

/**
 * 슈퍼어드민 "신고 처리 환불"이 불가능한 상태일 때 던진다 — 유저 취소(CancellationNotAllowedException)와
 * 별개 규칙이다: 관리자 환불은 픽업완료(picked) 상태만 대상으로 하고, 이미 환불됐거나(refunded) 그 외
 * 상태(픽업 전/취소/노쇼)는 전부 막는다.
 */
public class AdminRefundNotAllowedException extends RuntimeException {

	public AdminRefundNotAllowedException(String message) {
		super(message);
	}
}

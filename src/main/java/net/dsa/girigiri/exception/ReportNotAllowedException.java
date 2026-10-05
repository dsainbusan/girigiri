package net.dsa.girigiri.exception;

/** 신고 접수 자격(픽업완료 상태·48시간 이내·중복 신고 없음·본인 주문)을 만족하지 못할 때 던진다. */
public class ReportNotAllowedException extends RuntimeException {

	public ReportNotAllowedException(String message) {
		super(message);
	}
}

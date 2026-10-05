package net.dsa.girigiri.exception;

/**
 * 추가됨 (2026-10-06, 신고 기반 리팩터링) — 신고 접수 조건(픽업완료 / 픽업 후 48시간 이내 / 처리 중인
 * 신고 없음)을 못 채운 주문에 신고를 접수하려 할 때 던진다. 화면에서 버튼을 숨겨도 URL 직접 입력·폼
 * 지연 제출로 우회될 수 있어 컨트롤러가 서버에서 한 번 더 막는다(ComplaintService#blockedReportMessage).
 */
public class ReportNotAllowedException extends RuntimeException {
	public ReportNotAllowedException(String message) {
		super(message);
	}
}

package net.dsa.girigiri.domain.entity;

/**
 * 결제 상태. 예전엔 payStatus를 그냥 String("ready"/"paid"/"failed"/"cancelled")으로 뒀는데,
 * 오타로 잘못된 문자열이 들어가도 컴파일 시점엔 안 걸러지는 문제가 있어서 enum으로 바꿨다
 * (담당: 송보미 제안, 2026-08-25).
 *
 * PaymentEntity에 @Enumerated(EnumType.STRING)으로 매핑되어 있어서, DB 컬럼(pay_status)엔
 * 이 상수 이름 그대로("READY", "PAID", ...) 대문자로 저장된다 — 예전 String 방식 때 저장된
 * 소문자 값("ready" 등)과는 다르므로, 로컬 DB에 이미 들어있던 payment 테스트 데이터가 있다면
 * 이 변경을 반영하기 전에 비우고(TRUNCATE) 다시 테스트해야 한다.
 */
public enum PayStatus {
	READY,      // 결제창을 띄우기 전, 결제 대기 중
	PAID,       // PortOne 서버 재검증까지 완료된 실제 결제 성공
	FAILED,     // 결제 실패(카드 승인 거절 등) 또는 결제창 이탈
	CANCELLED   // 취소/환불 처리됨 (PAID였던 건이 취소된 경우와, 애초에 결제 전에 취소된 경우 둘 다 포함)
}

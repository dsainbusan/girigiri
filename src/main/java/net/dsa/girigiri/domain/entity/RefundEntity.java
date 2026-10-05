package net.dsa.girigiri.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 신고 처리로 발생한 "관리자 환불" 1건. 담당: 문창호 (2026-10-06, 신고 기반 리팩터링).
 *
 * 유저 본인 취소(PaymentEntity/PaymentCancelEntity 경로, cancelReservation/cancelByStore)와는
 * 완전히 분리된 테이블이다 — 이건 "신고(report) 처리 과정에서 운영자가 이미 픽업 완료된 주문을
 * 환불했다"는 사실 자체를 남기는 기록이고(환불 완료 배지의 "일시·처리자" 표시용), PaymentEntity는
 * 여전히 실제 PG 상태(PAID/CANCELLED)의 단일 진실 소스로 그대로 쓴다 — 정산(SettlementService)이
 * PaymentEntity.payStatus만 보고 계산하므로, 이 테이블을 건드리지 않아도 정산엔 자동 반영된다.
 *
 * orderId는 UNIQUE — 같은 주문에 환불 레코드가 두 번 생기는 걸 DB 레벨에서 막는다(버튼 연타/이중
 * 제출 방어, AdminRefundService가 이미 REQUESTED/FAILED 행이 있으면 새로 만들지 않고 재사용한다).
 */
@Entity
@Table(name = "refund", uniqueConstraints = {
		@UniqueConstraint(name = "uk_refund_order_id", columnNames = "order_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefundEntity extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "order_id", nullable = false)
	private Long orderId;   // = ReservationEntity.id ("주문")

	@Column(name = "report_id", nullable = false)
	private Long reportId;  // = ComplaintEntity.id (이 환불의 근거가 된 신고)

	@Column(name = "amount", nullable = false)
	private Integer amount;

	@Column(name = "reason", length = 255, nullable = false)
	private String reason;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private RefundStatus status;

	@Column(name = "pg_refund_tid", length = 50)
	private String pgRefundTid;

	@Column(name = "requested_by", nullable = false)
	private Long requestedBy;   // 처리한 슈퍼어드민 userId

	private RefundEntity(Long orderId, Long reportId, Integer amount, String reason, Long requestedBy) {
		this.orderId = orderId;
		this.reportId = reportId;
		this.amount = amount;
		this.reason = reason;
		this.requestedBy = requestedBy;
		this.status = RefundStatus.REQUESTED;
	}

	/** PG 호출 전 REQUESTED 상태로 1건을 만든다. */
	public static RefundEntity requested(Long orderId, Long reportId, Integer amount, String reason, Long requestedBy) {
		return new RefundEntity(orderId, reportId, amount, reason, requestedBy);
	}

	/** 이미 있던 행(과거 FAILED 등)을 이번 시도 내용으로 덮어써서 REQUESTED로 되돌린다 — order_id UNIQUE라 재시도는 새 행이 아니라 같은 행을 갱신한다. */
	public void retry(Integer amount, String reason, Long requestedBy) {
		this.amount = amount;
		this.reason = reason;
		this.requestedBy = requestedBy;
		this.status = RefundStatus.REQUESTED;
		this.pgRefundTid = null;
	}

	/** PG 환불 성공. */
	public void markDone(String pgRefundTid) {
		this.status = RefundStatus.DONE;
		this.pgRefundTid = pgRefundTid;
	}

	/** PG 환불 실패 — 주문/신고 상태는 호출부에서 그대로 둔다(이 메서드는 이 행의 상태만 바꾼다). */
	public void markFailed() {
		this.status = RefundStatus.FAILED;
	}
}

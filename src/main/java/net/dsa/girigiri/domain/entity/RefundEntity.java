package net.dsa.girigiri.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 신고 처리로 발생한 관리자 환불 1건 (2026-10-06, 신고 기반 리팩터링). 유저 본인 취소
 * (payment/payment_cancel)와 분리된 테이블이며, "환불 완료(일시·처리자)" 표시와 주문당 1건 보장
 * (orderId UNIQUE)이 목적이다. PG 상태의 단일 진실 소스는 여전히 payment.pay_status다.
 */
@Entity
@Table(name = "refund", uniqueConstraints = @UniqueConstraint(name = "uk_refund_order_id", columnNames = "order_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefundEntity extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "order_id", nullable = false)
	private Long orderId;   // = reservation.id

	@Column(name = "report_id", nullable = false)
	private Long reportId;  // = complaint.id

	@Column(name = "amount", nullable = false)
	private Integer amount;

	@Column(name = "reason", nullable = false, length = 255)
	private String reason;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private RefundStatus status;

	@Column(name = "pg_refund_tid", length = 50)
	private String pgRefundTid;

	@Column(name = "requested_by", nullable = false)
	private Long requestedBy;   // 처리한 슈퍼어드민 userId

	public static RefundEntity requested(Long orderId, Long reportId, int amount, String reason, Long requestedBy) {
		RefundEntity e = new RefundEntity();
		e.orderId = orderId;
		e.reportId = reportId;
		e.amount = amount;
		e.reason = reason.length() > 255 ? reason.substring(0, 255) : reason;
		e.requestedBy = requestedBy;
		e.status = RefundStatus.REQUESTED;
		return e;
	}

	/** FAILED였던 건을 다시 시도할 때 사유·처리자·신고를 새 값으로 갱신한다. */
	public void retry(Long reportId, String reason, Long requestedBy) {
		this.reportId = reportId;
		this.reason = reason.length() > 255 ? reason.substring(0, 255) : reason;
		this.requestedBy = requestedBy;
		this.status = RefundStatus.REQUESTED;
	}

	public void done(String pgRefundTid) {
		this.status = RefundStatus.DONE;
		this.pgRefundTid = pgRefundTid;
	}

	public void fail() {
		this.status = RefundStatus.FAILED;
	}
}

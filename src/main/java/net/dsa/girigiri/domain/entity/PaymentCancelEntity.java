package net.dsa.girigiri.domain.entity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 결제 취소/환불 "시도" 1건을 기록하는 감사(audit) 로그성 엔티티.
 * 담당: 송채현 (WBS 5.0 예약·결제), 송보미 제안으로 추가 (2026-08-25).
 *
 * 예전엔 PaymentEntity의 payStatus를 그냥 "cancelled"로 덮어써서, 같은 결제를 두 번 이상
 * 취소/환불 시도한 이력(예: 1차 환불 API 실패 -> 수동 확인 후 재시도)이 남지 않았다. 이제는
 * 취소를 시도할 때마다 이 테이블에 한 행씩 쌓아서, "언제 얼마를 어떤 사유로 취소 시도했고
 * 성공했는지"를 나중에도 그대로 추적할 수 있게 한다 — PaymentEntity 자체는 여전히 "현재 상태"만
 * 갖고 있고, 이 엔티티가 그 상태에 이르기까지의 이력을 담당한다.
 *
 * 생성된 뒤로는 내용이 안 바뀌는(불변) 로그라서 BaseCreatedEntity를 상속한다 — 다만 이 테이블에서
 * "생성 시각"은 곧 "취소/환불을 요청한 시각"이라는 의미라, PaymentEntity의 requestedAt과 같은
 * 이유로 컬럼명을 requested_at으로 오버라이드했다.
 */
@Entity
@Table(name = "payment_cancel")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AttributeOverride(name = "createdAt", column = @Column(name = "requested_at", updatable = false))
public class PaymentCancelEntity extends BaseCreatedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "payment_id", nullable = false)
	private Long paymentId;

	@Column(name = "amount", nullable = false)
	private Integer amount;

	@Column(name = "reason", length = 255)
	private String reason;

	@Column(name = "succeeded", nullable = false)
	private Boolean succeeded;

	private PaymentCancelEntity(Long paymentId, Integer amount, String reason, Boolean succeeded) {
		this.paymentId = paymentId;
		this.amount = amount;
		this.reason = reason;
		this.succeeded = succeeded;
	}

	/** 취소/환불 시도 1건을 기록한다. succeeded=true면 실제로 환불 성공(또는 애초에 결제 전 취소), false면 PortOne 환불 API 실패. */
	public static PaymentCancelEntity of(Long paymentId, Integer amount, String reason, boolean succeeded) {
		return new PaymentCancelEntity(paymentId, amount, reason, succeeded);
	}
}

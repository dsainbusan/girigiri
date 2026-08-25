package net.dsa.girigiri.domain.entity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import net.dsa.girigiri.exception.PaymentVerificationException;

import java.time.LocalDateTime;

/**
 * 결제 1건을 나타내는 엔티티. 담당: 송채현 (WBS 5.0 예약·결제).
 *
 * 리팩터링됨 (2026-08-25, 송보미 제안) — 왜: 예전엔 @Setter/@Builder로 아무 코드에서나
 * payStatus를 원하는 문자열로 바로 바꿀 수 있었는데, 그러다 보니 "READY 상태인데 paidAt만
 * 채워진" 것처럼 필드끼리 앞뒤가 안 맞는 상태가 만들어질 위험이 있었다. 이제 생성은 ready()
 * 정적 팩토리로만, 상태 전이는 approve()/fail()/cancel()/applyCancel() 메서드로만 하도록
 * 좁혀서 "이 결제가 지금 어떤 상태에서 어떤 상태로만 넘어갈 수 있는지"를 엔티티 스스로 지킨다.
 *
 * payStatus는 String("ready"/"paid"/...)에서 PayStatus enum으로 바꿨다 — 대문자로 저장되니
 * (@Enumerated(EnumType.STRING)), 로컬 DB에 예전 방식(소문자 문자열)으로 이미 들어있던 payment
 * 테스트 데이터가 있다면 이 변경 반영 전에 TRUNCATE로 비우고 다시 테스트해야 한다.
 *
 * createdAt/updatedAt은 BaseTimeEntity에서 상속받는다 — 이 결제는 생성된 뒤에도 payStatus 등이
 * 계속 바뀌는(가변) 엔티티라서 BaseCreatedEntity가 아니라 BaseTimeEntity를 쓴다. 예전부터 쓰던
 * requestedAt이라는 이름/컬럼(requested_at)은 그대로 유지하고 싶어서 @AttributeOverride로
 * 상속받은 createdAt의 컬럼명만 바꿨다.
 */
@Entity
@Table(name = "payment", uniqueConstraints = {
		@UniqueConstraint(name = "uk_payment_reservation_id", columnNames = "reservation_id"),
		@UniqueConstraint(name = "uk_payment_merchant_uid", columnNames = "merchant_uid"),
		@UniqueConstraint(name = "uk_payment_imp_uid", columnNames = "imp_uid")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AttributeOverride(name = "createdAt", column = @Column(name = "requested_at", updatable = false))
public class PaymentEntity extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "reservation_id", nullable = false)
	private Long reservationId;

	@Column(name = "merchant_uid", nullable = false, length = 50)
	private String merchantUid;   // 가맹점 주문번호 (우리 서버가 생성해서 PortOne에 넘김)

	@Column(name = "imp_uid", length = 50)
	private String impUid;        // PortOne(아임포트) 결제 고유번호 (결제 승인 후 채워짐)

	@Column(name = "amount", nullable = false)
	private Integer amount;

	@Column(name = "pay_method", length = 20)
	private String payMethod;     // card / kakaopay / naverpay 등 (PortOne 응답의 method.type)

	@Enumerated(EnumType.STRING)
	@Column(name = "pay_status", nullable = false, length = 20)
	private PayStatus payStatus;

	@Column(name = "fail_reason", length = 255)
	private String failReason;

	@Column(name = "paid_at")
	private LocalDateTime paidAt;

	private PaymentEntity(Long reservationId, String merchantUid, Integer amount) {
		this.reservationId = reservationId;
		this.merchantUid = merchantUid;
		this.amount = amount;
		this.payStatus = PayStatus.READY;
	}

	/** 결제창을 띄우기 직전, 결제 대기(READY) 레코드를 새로 만든다. (ReservationService.prepareReservation) */
	public static PaymentEntity ready(Long reservationId, String merchantUid, int amount) {
		return new PaymentEntity(reservationId, merchantUid, amount);
	}

	/**
	 * PortOne 서버 재검증까지 끝난 실제 결제 승인 처리. 이미 PAID인 결제에 또 승인이 들어오면
	 * (예: 프론트 재시도 등으로 confirmPayment가 중복 호출된 경우) 조용히 무시한다(idempotent) —
	 * 두 번째 승인 요청이 왔다고 예외를 던지면 오히려 정상적인 재시도까지 실패로 보이게 되기 때문이다.
	 * 다만 금액이 이 결제 레코드에 기록된 amount와 다르면, 이미 PAID였든 아니든 무조건 예외를 던진다
	 * (금액 불일치는 재시도로 넘길 수 있는 문제가 아니라 즉시 확인이 필요한 이상 상황이기 때문).
	 */
	public void approve(String impUid, int paidAmount, String payMethod, LocalDateTime pgPaidAt) {
		if (paidAmount != this.amount) {
			throw new PaymentVerificationException(
					"결제 금액이 일치하지 않아요 (기록된 금액=" + this.amount + "원, 승인된 금액=" + paidAmount + "원)");
		}
		if (this.payStatus == PayStatus.PAID) {
			return;
		}

		this.impUid = impUid;
		this.payMethod = payMethod;
		this.paidAt = pgPaidAt != null ? pgPaidAt : LocalDateTime.now();
		this.payStatus = PayStatus.PAID;
		this.failReason = null;
	}

	/** 결제 실패(카드 승인 거절 등) 또는 결제창 이탈 처리. 이미 PAID로 확정된 건을 실패로 되돌리면 안 되므로 막는다. */
	public void fail(String reason) {
		if (this.payStatus == PayStatus.PAID) {
			throw new IllegalStateException("이미 결제 완료(PAID)된 건은 실패 처리할 수 없어요. paymentId=" + this.id);
		}
		this.payStatus = PayStatus.FAILED;
		this.failReason = reason;
	}

	/**
	 * 아직 결제 전(READY)이거나 이미 실패(FAILED)한 건을 취소한다 — 실제로 오간 돈이 없는 경우다.
	 * 이미 PAID인 건은 여기가 아니라 applyCancel()로 처리해야 한다(환불이 필요하기 때문).
	 * 이미 CANCELLED인 건에 또 호출되면 조용히 무시한다(idempotent).
	 */
	public void cancel(String reason) {
		if (this.payStatus == PayStatus.PAID) {
			throw new IllegalStateException(
					"이미 결제 완료(PAID)된 건은 cancel()이 아니라 applyCancel()로 환불 처리해야 해요. paymentId=" + this.id);
		}
		if (this.payStatus == PayStatus.CANCELLED) {
			return;
		}
		this.payStatus = PayStatus.CANCELLED;
		this.failReason = reason;
	}

	/** 이미 PAID였던 건을 환불 처리한다(PortOne 실제 환불 요청은 호출부에서 별도 처리). PAID가 아니면 예외. */
	public void applyCancel(String note) {
		if (this.payStatus != PayStatus.PAID) {
			throw new IllegalStateException("결제 완료(PAID) 상태가 아니면 환불 처리할 수 없어요. 현재 상태=" + this.payStatus);
		}
		this.payStatus = PayStatus.CANCELLED;
		this.failReason = note;
	}
}

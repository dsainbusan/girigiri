package net.dsa.girigiri.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

// 수정됨 (2026-10-06, 코드 리뷰 #6) — 왜: 블랭킷 @Setter라 어디서든 reservation.setStatus(...)로
// 상태를 바꿀 수 있었다(PaymentEntity/RefundEntity가 같은 이유로 이미 겪고 고친 문제 — 그쪽 클래스
// 주석 참고). status/cancelledBy/cancelReason/pickedAt/acceptedAt은 전부 "상태 전이" 성격이라
// 아래 confirm()/markReady()/markPicked()/cancel()/markNoShowed()/refund() 메서드로만 바꾸게
// 좁혔다. 나머지 필드(수량/가격/쿠폰 등)는 생성 후 안 바뀌어서 그대로 @Getter만 둔다.
@Entity
@Table(name = "reservation")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class ReservationEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "product_id", nullable = false)
	private Long productId;

	// 추가됨 — 왜: 주문 당시 상품명 스냅샷. product_id로 매번 product 테이블을 JOIN해서 이름을
	// 가져오면, 나중에 상품명이 바뀌거나 상품이 삭제될 때 과거 거래 기록까지 덩달아 바뀌거나 깨진다.
	// 실제 커머스의 "주문 내역"이 다 이렇게 스냅샷을 남기는 것과 같은 이유.
	@Column(name = "product_name", length = 100)
	private String productName;

	@Column(name = "store_id", nullable = false)
	private Long storeId;

	@Column(name = "reserved_quantity", nullable = false)
	private Integer reservedQuantity;

	@Column(name = "total_price", nullable = false)
	private Integer totalPrice;

	// 추가됨 (2026-09-07, 송채현, WBS "쿠폰 발급/관리") — 이 예약에 쿠폰을 썼으면 CouponEntity.id,
	// 안 썼으면 null. 체크아웃에서 실제로 쿠폰을 골라 쓰는 화면 연결은 다음 작업("할인코드 적용/검증")에서
	// 붙인다 — 지금은 취소/노쇼 시 CouponService.restore()가 참조할 수 있도록 컬럼만 먼저 추가해둔다.
	@Column(name = "coupon_id")
	private Long couponId;

	@Column(name = "pickup_time")
	private LocalDateTime pickupTime;

	// unique 추가됨 (2026-09-08, 코드 감사) — QrCodeUtil.generatePickupCode()의 코드 공간을 늘려서
	// 충돌 가능성은 사실상 0에 가깝게 낮췄지만(주석 참고), DB 차원의 마지막 안전장치로 유니크 제약도 건다.
	@Column(name = "pickup_code", length = 30, unique = true)
	private String pickupCode;   // QR/픽업 확인 코드

	@Column(name = "status", nullable = false, length = 20)
	private String status;
	// pending   : 결제 전 임시 상태. 결제 성공 시 confirmed로 전환, 결제 실패/이탈 시 자동 취소(레코드 정리)
	// confirmed : 결제 완료, 매장 확인(수락) 대기중. 손님은 아직 픽업하러 오면 안 되는 상태 —
	//             (2026-08-21 변경) 매장이 "예약 확인" 화면에서 수락하기 전까지는 픽업 처리(QR 스캔)가
	//             막힌다. 취소는 이 상태에서도 그대로 가능하다.
	// ready     : 매장이 확인/수락함 → 이제 손님이 와서 픽업해도 되는 상태. acceptedAt에 수락 시각 기록.
	// picked    : 픽업완료 (pickedAt에 픽업 시각 기록)
	// cancelled : 취소
	// noshowed  : 노쇼 (매장 마지막 픽업시간 경과 후 확정 처리)
	// refunded  : 신고 처리로 운영자가 환불 (2026-10-06 추가, AdminRefundService — picked 상태에서만 전이)
	//
	// 목록 탭 필터 매핑: 진행중 = status IN (confirmed, ready) · 픽업완료 = status = picked
	//                   노쇼·취소 = status IN (cancelled, noshowed, refunded)

	@CreatedDate
	@Column(name = "reserved_at", updatable = false)
	private LocalDateTime reservedAt;

	// 매장이 "예약 확인" 화면에서 수락한 시각 (status가 confirmed -> ready로 바뀐 시점)
	@Column(name = "accepted_at")
	private LocalDateTime acceptedAt;

	@Column(name = "picked_at")
	private LocalDateTime pickedAt;

	// status가 "cancelled" 또는 "refunded"일 때만 값이 채워진다. 누가/왜 취소했는지 구분해서 남겨서
	// 나중에 매장 신뢰도(취소율) 계산 등에 쓸 수 있게 한다.
	@Column(name = "cancelled_by", length = 10)
	private String cancelledBy;   // "USER"(손님이 취소) / "STORE"(매장이 취소, 재고 문제 등) / "ADMIN"(운영자가 신고 처리로 취소·환불) / "SYSTEM"(결제 실패·시간초과 자동 취소)

	@Column(name = "cancel_reason", length = 255)
	private String cancelReason;  // SYSTEM/STORE/ADMIN 취소·환불일 때 사유 텍스트 (예: "재고 부족")

	// ── 상태 전이 메서드 (2026-10-06, 코드 리뷰 #6) ────────────────────────────
	// ReservationService/AdminRefundService가 이 메서드들로만 상태를 바꾼다. 각 메서드는 그 전이가
	// 실제로 어떤 필드를 같이 채우는지(예: picked면 pickedAt) 한곳에 묶어서, "상태만 바꾸고 시각 필드를
	// 깜빡하는" 실수를 구조적으로 막는다. 전이 가능 여부(지금 상태에서 이 전이가 허용되는지) 판정은
	// 여전히 ReservationService(blockedCancelMessage 등)가 맡는다 — 여기는 "바뀐 뒤의 필드 일관성"만 책임진다.

	/** pending → confirmed (결제 승인). */
	public void confirm() {
		this.status = "confirmed";
	}

	/** confirmed → ready (매장이 예약을 확인/수락). */
	public void markReady(LocalDateTime acceptedAt) {
		this.status = "ready";
		this.acceptedAt = acceptedAt;
	}

	/** ready → picked (픽업 완료). */
	public void markPicked(LocalDateTime pickedAt) {
		this.status = "picked";
		this.pickedAt = pickedAt;
	}

	/** 어느 상태에서든 → cancelled. cancelledBy: "SYSTEM"/"USER"/"STORE"/"ADMIN". reason은 USER 취소처럼 없을 수 있다. */
	public void cancel(String cancelledBy, String reason) {
		this.status = "cancelled";
		this.cancelledBy = cancelledBy;
		this.cancelReason = reason;
	}

	/** ready(픽업 대기중) → noshowed. */
	public void markNoShowed() {
		this.status = "noshowed";
	}

	/** picked → refunded (신고 처리 환불, AdminRefundService 전용). cancelledBy는 항상 "ADMIN". */
	public void refund(String cancelledBy, String reason) {
		this.status = "refunded";
		this.cancelledBy = cancelledBy;
		this.cancelReason = reason;
	}
}

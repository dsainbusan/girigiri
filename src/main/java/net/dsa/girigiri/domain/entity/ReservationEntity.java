package net.dsa.girigiri.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "reservation")
@Getter
@Setter
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

	@Column(name = "store_id", nullable = false)
	private Long storeId;

	@Column(name = "reserved_quantity", nullable = false)
	private Integer reservedQuantity;

	@Column(name = "total_price", nullable = false)
	private Integer totalPrice;

	@Column(name = "pickup_time")
	private LocalDateTime pickupTime;

	@Column(name = "pickup_code", length = 30)
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
	//
	// 목록 탭 필터 매핑: 진행중 = status IN (confirmed, ready) · 픽업완료 = status = picked
	//                   노쇼·취소 = status IN (cancelled, noshowed)

	@CreatedDate
	@Column(name = "reserved_at", updatable = false)
	private LocalDateTime reservedAt;

	// 매장이 "예약 확인" 화면에서 수락한 시각 (status가 confirmed -> ready로 바뀐 시점)
	@Column(name = "accepted_at")
	private LocalDateTime acceptedAt;

	@Column(name = "picked_at")
	private LocalDateTime pickedAt;

	// status가 "cancelled"일 때만 값이 채워진다. 누가/왜 취소했는지 구분해서 남겨서
	// 나중에 매장 신뢰도(취소율) 계산 등에 쓸 수 있게 한다.
	@Column(name = "cancelled_by", length = 10)
	private String cancelledBy;   // "USER"(손님이 취소) / "STORE"(매장이 취소, 재고 문제 등)

	@Column(name = "cancel_reason", length = 255)
	private String cancelReason;  // STORE 취소일 때 사유 텍스트 (예: "재고 부족")
}

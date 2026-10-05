package net.dsa.girigiri.domain.entity;

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
 * 예약 상태 변경 이력 — 2026-10-06, 신고 기반 리팩터링(관리자 환불) 요청으로 신설.
 *
 * 범위를 일부러 좁게 잡았다: 기존 취소(cancelReservation/cancelByStore)·노쇼·픽업 전이는 전부 지금처럼
 * 이력 없이 그대로 두고(요청받지 않음, 되짚어 고치면 영향 범위가 너무 커진다), 이번에 새로 생기는
 * "picked → refunded"(AdminRefundService) 전이에만 이 테이블에 한 행을 남긴다 — "누가/언제/왜" 바꿨는지
 * 신고 처리 결과를 나중에 감사(audit)할 수 있어야 한다는 요구(스펙 C.10)에 대응.
 */
@Entity
@Table(name = "reservation_status_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReservationStatusHistoryEntity extends BaseCreatedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "reservation_id", nullable = false)
	private Long reservationId;

	@Column(name = "from_status", nullable = false, length = 20)
	private String fromStatus;

	@Column(name = "to_status", nullable = false, length = 20)
	private String toStatus;

	@Column(name = "changed_by", nullable = false)
	private Long changedBy;   // 처리한 관리자 userId

	@Column(name = "reason", length = 255)
	private String reason;

	private ReservationStatusHistoryEntity(Long reservationId, String fromStatus, String toStatus,
	                                        Long changedBy, String reason) {
		this.reservationId = reservationId;
		this.fromStatus = fromStatus;
		this.toStatus = toStatus;
		this.changedBy = changedBy;
		this.reason = reason;
	}

	public static ReservationStatusHistoryEntity of(Long reservationId, String fromStatus, String toStatus,
	                                                  Long changedBy, String reason) {
		return new ReservationStatusHistoryEntity(reservationId, fromStatus, toStatus, changedBy, reason);
	}
}

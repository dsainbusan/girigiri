package net.dsa.girigiri.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 예약 상태 변경 이력 — 현재는 AdminRefundService의 picked → refunded 전이 1건만 기록한다. */
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
	private Long changedBy;

	@Column(name = "reason", length = 255)
	private String reason;

	public static ReservationStatusHistoryEntity of(Long reservationId, String from, String to, Long changedBy, String reason) {
		ReservationStatusHistoryEntity e = new ReservationStatusHistoryEntity();
		e.reservationId = reservationId;
		e.fromStatus = from;
		e.toStatus = to;
		e.changedBy = changedBy;
		e.reason = reason != null && reason.length() > 255 ? reason.substring(0, 255) : reason;
		return e;
	}
}

package net.dsa.girigiri.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 주간 정산 1건 — WBS 2.0 "매장 정산 페이지" (문창호, 2026-09-01).
 *
 * 흐름: 매주 월 00:00 스케줄러(SettlementScheduler)가 지난 주(월~일) 확정분을 계산해 이 레코드를 만든다
 * (status=PENDING). 슈퍼어드민이 "정산 지급" 화면에서 이체 목록(Excel)을 받아 은행 대량이체로 송금한 뒤
 * 선택 지급 완료 처리하면 status=PAID. 지급액이 최소 정산액(SettlementService.MIN_PAYOUT) 미만이면
 * status=CARRIED로 두고 다음 주 정산에 합산한다(합산되면 그 CARRIED 건은 ROLLED로 바뀐다).
 *
 * "정산 확정"(계산)과 "지급"(실제 송금)을 분리해서, 매장이 늘어도 슈퍼어드민은 주 1회 배치만 하면 되게 한다.
 */
@Entity
@Table(name = "settlement", uniqueConstraints = {
		@UniqueConstraint(name = "uk_settlement_store_period", columnNames = {"store_id", "period_start"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class SettlementEntity {

	public static final String STATUS_PENDING = "PENDING";   // 지급 대기 (확정됨, 송금 전)
	public static final String STATUS_PAID = "PAID";          // 지급 완료
	public static final String STATUS_CARRIED = "CARRIED";    // 이월 (최소 정산액 미달 → 다음 주에 합산)
	public static final String STATUS_ROLLED = "ROLLED";      // 이월분이 이후 정산에 합산 처리됨

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "store_id", nullable = false)
	private Long storeId;

	@Column(name = "period_start", nullable = false)
	private LocalDate periodStart;   // 정산 대상 주간 시작 (월요일)

	@Column(name = "period_end", nullable = false)
	private LocalDate periodEnd;     // 정산 대상 주간 끝 (일요일)

	// --- 이 주간의 집계 (SettlementService 계산과 동일 기준) ---
	@Column(name = "gross", nullable = false)
	private long gross;              // 총 결제액 (PAID + 결제 후 취소분)

	@Column(name = "refund", nullable = false)
	private long refund;             // 환불 차감

	@Column(name = "net_amount", nullable = false)
	private long netAmount;          // 순 결제액 = gross - refund

	@Column(name = "commission_rate", nullable = false)
	private int commissionRate;      // 적용 수수료율 % (스냅샷 — 정책 바뀌어도 과거 기록 유지)

	@Column(name = "commission", nullable = false)
	private long commission;         // 플랫폼 수수료

	@Column(name = "week_amount", nullable = false)
	private long weekAmount;         // 이번 주 순수 정산분 = net - commission

	@Column(name = "carried_in", nullable = false)
	private long carriedIn;          // 이전 이월분 합산액 (CARRIED 건들의 weekAmount 합)

	@Column(name = "payout", nullable = false)
	private long payout;             // 이번에 실제 지급되는 금액 (CARRIED/ROLLED면 0)

	@Column(name = "status", nullable = false, length = 20)
	private String status;

	// 이월분이 어느 정산에 합산됐는지 (status=ROLLED일 때만)
	@Column(name = "merged_into_id")
	private Long mergedIntoId;

	@Column(name = "confirmed_at", nullable = false)
	private LocalDateTime confirmedAt;          // 정산 확정 시각 (스케줄러 실행 시각)

	@Column(name = "scheduled_payout_date", nullable = false)
	private LocalDate scheduledPayoutDate;      // 지급 예정일 (확정일 + 영업일 2일)

	@Column(name = "paid_at")
	private LocalDateTime paidAt;               // 실제 지급 완료 시각

	@Column(name = "transfer_memo", length = 200)
	private String transferMemo;                // 이체 확인 메모 (슈퍼어드민 입력)

	@CreatedDate
	@Column(name = "created_at", updatable = false)
	private LocalDateTime createdAt;
}

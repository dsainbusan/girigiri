package net.dsa.girigiri.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import net.dsa.girigiri.util.AesStringConverter;
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

	// 추가됨 (2026-10-07, 계좌 보안) — "확정된 정산 건은 기존 계좌 유지, 다음 정산부터 새 계좌 적용"
	// 요구사항 때문에 추가. 지금까지는 SettlementEntity가 계좌를 따로 안 들고 있고 지급 시점(Excel
	// 다운로드)에 StoreEntity를 그때그때 조회해서 썼는데, 그러면 정산이 확정(PENDING, 송금 전)된
	// 뒤에 점주가 계좌를 바꾸면 다음 Excel 다운로드 때 새 계좌로 나가버린다 — 그래서
	// SettlementBatchService.confirmWeek()가 확정 시점의 계좌를 이 컬럼들에 스냅샷으로 복사해두고,
	// 이후 Excel/화면은 전부 StoreEntity가 아니라 이 스냅샷을 쓴다(SuperAdminSettlementService 수정).
	@Column(name = "bank_name", length = 30)
	private String bankName;

	@Convert(converter = AesStringConverter.class)
	@Column(name = "bank_account", length = 255)
	private String bankAccount;

	@Column(name = "account_holder", length = 40)
	private String accountHolder;

	// 추가됨 (2026-10-07) — 슈퍼어드민이 실제 은행 이체를 마친 뒤 "지급 완료" 처리할 때 같이 올리는
	// 이체 확인증(스크린샷). 변경됨(같은 날) — 슈퍼어드민 내부 증빙 전용으로 바꿔서 FileStorageUtil
	// .storePrivate()의 상대 경로("settlement-receipts/파일명")를 저장한다(점주는 대신 지급 명세서를 봄).
	// 변경 전에 올라간 건은 "/upload/..." 공개 경로가 남아있을 수 있다 — SuperAdminSettlementService
	// #transferReceipt가 두 형태 모두 연다. 컬럼명은 스키마 변경을 피하려고 그대로 뒀다.
	@Column(name = "transfer_receipt_url")
	private String transferReceiptUrl;

	@CreatedDate
	@Column(name = "created_at", updatable = false)
	private LocalDateTime createdAt;

	/**
	 * 추가됨 (2026-10-07, 계좌 보안) — 지급 가능 여부. 확정 시점에 계좌 스냅샷이 찍혔으면(=그때 정상
	 * 계좌였음) 그 뒤로 매장 계좌가 어떻게 바뀌든 항상 지급 가능 — 스냅샷 자체가 "그때 쓸 계좌가
	 * 확정됐다"는 뜻이라 다시 흔들리지 않는다. 스냅샷이 없으면(확정 당시 계좌가 없었던 경우) 지금
	 * 시점의 매장 계좌 상태를 그대로 따른다 — 그 사이 등록·승인됐으면 지급 가능해진다.
	 * SettlementBatchService.markPaid()(지급 처리 서버 재검증)와 SuperAdminSettlementService
	 * (목록 표시) 양쪽에서 같은 기준으로 쓴다.
	 */
	public boolean isPayable(StoreEntity store) {
		return bankAccount != null || (store != null && store.isAccountPayable());
	}
}

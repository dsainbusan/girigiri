package net.dsa.girigiri.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import net.dsa.girigiri.util.AesStringConverter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 추가됨 (2026-10-07, 계좌 보안) — 승인된 매장의 정산 계좌 "변경 신청" 1건.
 *
 * 입점 승인 전엔 계좌가 입점 신청 폼(필수)으로 들어가고, 승인 후엔 더 이상 점주가 StoreEntity의
 * bankAccount 등을 직접 수정할 수 없다 — 변경하고 싶으면 이 테이블에 신청(PENDING)만 쌓이고,
 * 슈퍼어드민이 승인해야 실제 StoreEntity에 반영된다(BankAccountChangeService 참고). 승인 전까지
 * 해당 매장은 StoreEntity.accountStatus=UNDER_REVIEW로 바뀌어 정산 지급이 보류된다.
 *
 * old_ 접두사/new_ 접두사 컬럼 둘 다 마스킹값(계좌번호는 BankAccountMaskUtil.mask 결과)을 남겨서
 * 감사 이력으로 쓴다 — 그와 별개로 new_bank_account는 승인 시 실제 StoreEntity에 그대로 복사해야
 * 하므로 암호화된 원본 값도 같이 들고 있는다(new_bank_account_masked는 화면 표시 전용 스냅샷).
 */
@Entity
@Table(name = "bank_account_change_request")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class BankAccountChangeRequestEntity {

	public static final String STATUS_PENDING = "PENDING";
	public static final String STATUS_APPROVED = "APPROVED";
	public static final String STATUS_REJECTED = "REJECTED";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "store_id", nullable = false)
	private Long storeId;

	@Column(name = "requested_by", nullable = false)
	private Long requestedBy;   // 신청한 점주의 userId

	@CreatedDate
	@Column(name = "requested_at", updatable = false)
	private LocalDateTime requestedAt;

	@Builder.Default
	@Column(name = "status", nullable = false, length = 20)
	private String status = STATUS_PENDING;

	@Column(name = "reviewed_by")
	private Long reviewedBy;   // 승인/반려 처리한 슈퍼어드민 userId

	@Column(name = "reviewed_at")
	private LocalDateTime reviewedAt;

	@Column(name = "reject_reason", length = 255)
	private String rejectReason;

	// --- 신청 시점의 "변경 전" 스냅샷 (감사 이력용, 전부 마스킹값) ---
	@Column(name = "old_bank_name", length = 30)
	private String oldBankName;

	@Column(name = "old_bank_account_masked", length = 40)
	private String oldBankAccountMasked;

	@Column(name = "old_account_holder", length = 40)
	private String oldAccountHolder;

	// --- 신청 내용 ("변경 후") ---
	@Column(name = "new_bank_name", length = 30)
	private String newBankName;

	@Convert(converter = AesStringConverter.class)
	@Column(name = "new_bank_account", length = 255)
	private String newBankAccount;   // 승인 시 StoreEntity.bankAccount로 그대로 복사

	@Column(name = "new_bank_account_masked", length = 40)
	private String newBankAccountMasked;   // 화면 표시 전용 — 매번 복호화 안 해도 되게 스냅샷

	@Column(name = "new_account_holder", length = 40)
	private String newAccountHolder;

	@Column(name = "new_passbook_image_url", length = 255)
	private String newPassbookImageUrl;

	/** 승인 — StoreEntity 반영은 BankAccountChangeService가 같은 트랜잭션에서 같이 처리한다. */
	public void approve(Long adminId) {
		this.status = STATUS_APPROVED;
		this.reviewedBy = adminId;
		this.reviewedAt = LocalDateTime.now();
	}

	public void reject(Long adminId, String reason) {
		this.status = STATUS_REJECTED;
		this.reviewedBy = adminId;
		this.reviewedAt = LocalDateTime.now();
		this.rejectReason = reason;
	}
}

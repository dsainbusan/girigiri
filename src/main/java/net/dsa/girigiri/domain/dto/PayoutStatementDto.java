package net.dsa.girigiri.domain.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 추가됨 (2026-10-07) — 점주용 "지급 명세서" 한 장(지급 완료된 주간 정산 1건). 은행 이체확인증 캡처를
 * 점주에게 그대로 보여주던 방식 대신, 플랫폼이 DB 기록(SettlementEntity 확정 스냅샷)으로 직접 만든
 * 명세서를 화면(payoutStatement.html)과 PDF(PayoutStatementPdfGenerator)에 같은 값으로 쓴다.
 * 계좌번호는 항상 마스킹된 값만 담는다(BankAccountMaskUtil).
 */
public record PayoutStatementDto(
		Long settlementId,
		String statementNo,
		String storeName,
		String businessNumber,
		LocalDate periodStart,
		LocalDate periodEnd,
		long gross,
		long refund,
		long netAmount,
		int commissionRate,
		long commission,
		long weekAmount,
		long carriedIn,
		long payout,
		LocalDate scheduledPayoutDate,
		LocalDateTime paidAt,
		String bankName,
		String maskedAccount,
		String accountHolder,
		LocalDate issuedDate
) {
}

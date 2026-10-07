package net.dsa.girigiri.domain.dto;

import java.time.LocalDateTime;

/**
 * 추가됨 (2026-10-07, 계좌 보안) — 슈퍼어드민 "계좌 변경 심사" 화면 한 행.
 * BankAccountChangeRequestEntity에 매장명만 얹은 뷰 모델 (SettlementRowDto와 동일 패턴).
 */
public record BankAccountChangeRequestRowDto(
		Long id,
		Long storeId,
		String storeName,
		LocalDateTime requestedAt,
		String status,
		String statusLabel,
		String newBankName,
		String newBankAccountMasked,
		String newAccountHolder,
		boolean hasPassbook,
		String rejectReason
) {
}

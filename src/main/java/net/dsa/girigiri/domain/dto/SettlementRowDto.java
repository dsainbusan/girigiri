package net.dsa.girigiri.domain.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 추가됨 (2026-09-29, 담당: 송보미) — 슈퍼어드민 정산 화면(/superadmin/settlements) 한 행.
 * SettlementEntity에 매장명·계좌 등록 여부만 얹어서 화면에 바로 뿌릴 수 있게 한 뷰 모델.
 * SuperAdminSettlementService#toRows 참고.
 */
public record SettlementRowDto(
		Long id,
		Long storeId,
		String storeName,
		String periodLabel,
		long payout,
		String status,
		String statusLabel,
		LocalDate scheduledPayoutDate,
		LocalDateTime paidAt,
		String transferMemo,
		boolean bankInfoRegistered,
		String transferReceiptUrl
) {
}

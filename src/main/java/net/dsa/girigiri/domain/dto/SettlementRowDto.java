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
		// 수정됨 (2026-10-07, 계좌 보안) — bankInfoRegistered(등록됨/미등록 2단)를 payable +
		// accountStatusLabel(정상/변경 심사 중/미등록 3단)로 교체. payable=false인 행은 화면에서
		// 체크박스를 비활성화한다(settlements.html).
		boolean payable,
		String accountStatusLabel,
		String transferReceiptUrl
) {
}

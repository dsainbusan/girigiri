package net.dsa.girigiri.domain.dto;

import java.time.LocalDateTime;

/**
 * 추가됨 (2026-09-09, 2026-09-29 완료영수증 통합) — 슈퍼어드민 및 점주 "전체 주문 내역" 화면용 DTO.
 * dateGroupLabel, pickedAtDisplay, reservedAt, pickedAt이 추가되어 일자별 묶기 및 영수증 조회가 가능하다.
 */
public record ReservationOrderItemDto(
		Long reservationId,
		String buyerName,
		String productName,
		int quantity,
		int totalPrice,
		String pickupCode,
		String statusLabel,
		String statusVariant,
		String reservedAtDisplay,
		String cancelInfo,
		String dateGroupLabel,
		String pickedAtDisplay,
		LocalDateTime reservedAt,
		LocalDateTime pickedAt
) {
	// 기존 10개 인자 생성자와 호환 유지
	public ReservationOrderItemDto(Long reservationId, String buyerName, String productName, int quantity,
	                               int totalPrice, String pickupCode, String statusLabel, String statusVariant,
	                               String reservedAtDisplay, String cancelInfo) {
		this(reservationId, buyerName, productName, quantity, totalPrice, pickupCode,
				statusLabel, statusVariant, reservedAtDisplay, cancelInfo, "-", null, null, null);
	}
}

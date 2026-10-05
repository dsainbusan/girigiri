package net.dsa.girigiri.domain.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 손님용 매장 상세 페이지 "쿠폰 받기" 카드 1장 — 2026-10-01 신규(지역별 현황 드릴다운, 매장 지정 쿠폰).
 */
@Getter
@Builder
public class StoreCouponOfferDto {
	private Long campaignId;
	private String discountLabel; // "10% 할인" / "1,000원 할인"
	private String expiresAtLabel;
	private boolean claimed;
}

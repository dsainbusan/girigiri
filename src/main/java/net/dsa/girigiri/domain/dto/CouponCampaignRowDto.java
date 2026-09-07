package net.dsa.girigiri.domain.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 슈퍼어드민 "프로모션 쿠폰 캠페인 관리" 목록 화면용 행 — 2026-09-07 신규 (송채현).
 * claimedCount(지금까지 발급된 수)는 CouponRepository 조회로 SuperAdminCouponService에서 계산한다.
 */
@Getter
@Builder
public class CouponCampaignRowDto {

    private Long id;
    private String name;
    private String code;
    private Integer discountRate;
    private String expiresAtLabel;
    private boolean active;
    private boolean expired;
    private long claimedCount;
    private String statusLabel;
}

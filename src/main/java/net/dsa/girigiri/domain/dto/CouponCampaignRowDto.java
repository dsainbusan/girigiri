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
    // 정률이면 "10%", 정액이면 "3,000원"(2026-10-06 — 정액 쿠폰이 "null%"로 보이던 것 정리). 코드형이 아니면 scopeLabel이 "지역 · 서울·경기" 등.
    private String discountLabel;
    private String scopeLabel;
    private String expiresAtLabel;
    private boolean active;
    private boolean expired;
    private long claimedCount;
    private String statusLabel;
}

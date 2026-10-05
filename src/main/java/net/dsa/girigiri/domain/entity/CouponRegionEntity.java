package net.dsa.girigiri.domain.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * 지역 지정 쿠폰 캠페인(CouponCampaignEntity.scope=REGION)의 대상 시도 N:M 연결 — 2026-10-06 신규
 * (슈퍼어드민 /superadmin/coupons "지역 지정 쿠폰 발행"). CouponStoreEntity의 시도 버전이다.
 * 체크아웃에서 "이 쿠폰을 이 매장(의 시도)에서 쓸 수 있는지"를 campaignId+sido로 확인한다
 * (CouponService#validateForRedeem 참고). sido는 SidoParser.SIDO_LIST의 표준 명칭("서울", "경기" 등).
 */
@Entity
@Table(name = "coupon_region")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponRegionEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "campaign_id", nullable = false)
	private Long campaignId;

	@Column(name = "sido", nullable = false, length = 10)
	private String sido;
}

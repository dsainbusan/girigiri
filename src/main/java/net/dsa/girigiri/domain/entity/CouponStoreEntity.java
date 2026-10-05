package net.dsa.girigiri.domain.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * 매장 지정 쿠폰 캠페인(CouponCampaignEntity.scope=STORE)의 대상 매장 N:M 연결 — 2026-10-01 신규
 * (통계 대시보드 "지역별 현황" 드릴다운, 매장 지정 쿠폰 발행).
 * 체크아웃에서 "이 매장에서 쓸 수 있는 쿠폰인지"를 campaignId+storeId로 조회한다
 * (CouponService#validateForRedeem 참고).
 */
@Entity
@Table(name = "coupon_store")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponStoreEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "campaign_id", nullable = false)
	private Long campaignId;

	@Column(name = "store_id", nullable = false)
	private Long storeId;
}

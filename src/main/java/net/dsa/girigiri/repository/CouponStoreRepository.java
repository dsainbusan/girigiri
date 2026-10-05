package net.dsa.girigiri.repository;

import net.dsa.girigiri.domain.entity.CouponStoreEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CouponStoreRepository extends JpaRepository<CouponStoreEntity, Long> {

	// 체크아웃에서 "이 캠페인 쿠폰이 이 매장에서 쓸 수 있는지" 확인 (CouponService#validateForRedeem).
	boolean existsByCampaignIdAndStoreId(Long campaignId, Long storeId);

	// "최근 조치"(매장 상세/지역 드릴다운) — 이 매장을 대상으로 한 캠페인 id 목록을 찾을 때 사용.
	List<CouponStoreEntity> findByStoreId(Long storeId);

	List<CouponStoreEntity> findByCampaignId(Long campaignId);
}

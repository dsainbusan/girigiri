package net.dsa.girigiri.repository;

import net.dsa.girigiri.domain.entity.CouponRegionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CouponRegionRepository extends JpaRepository<CouponRegionEntity, Long> {

	// 체크아웃에서 "이 캠페인 쿠폰이 이 시도의 매장에서 쓸 수 있는지" 확인 (CouponService#validateForRedeem).
	boolean existsByCampaignIdAndSido(Long campaignId, String sido);

	// 캠페인 목록/내 쿠폰함에서 "서울·경기" 같은 대상 지역 라벨을 만들 때 사용.
	List<CouponRegionEntity> findByCampaignId(Long campaignId);
}

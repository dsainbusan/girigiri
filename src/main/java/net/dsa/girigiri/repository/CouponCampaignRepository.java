package net.dsa.girigiri.repository;

import net.dsa.girigiri.domain.entity.CouponCampaignEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CouponCampaignRepository extends JpaRepository<CouponCampaignEntity, Long> {

    Optional<CouponCampaignEntity> findByCode(String code);

    boolean existsByCode(String code);
}

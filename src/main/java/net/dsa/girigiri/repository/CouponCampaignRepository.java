package net.dsa.girigiri.repository;

import jakarta.persistence.LockModeType;
import net.dsa.girigiri.domain.entity.CouponCampaignEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CouponCampaignRepository extends JpaRepository<CouponCampaignEntity, Long> {

    Optional<CouponCampaignEntity> findByCode(String code);

    boolean existsByCode(String code);

    // 추가됨 (2026-10-01, 매장 지정 쿠폰) — 발급 수량 상한(issueLimit) 동시성 처리용. 두 손님이
    // 동시에 "쿠폰 받기"를 눌러도 한쪽이 끝날 때까지 다른 쪽을 기다리게 해서, 상한을 넘겨 발급되는
    // 걸 막는다(ProductRepository.findByIdForUpdate와 동일 패턴).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CouponCampaignEntity c where c.id = :id")
    Optional<CouponCampaignEntity> findByIdForUpdate(@Param("id") Long id);
}

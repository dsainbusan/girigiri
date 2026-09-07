package net.dsa.girigiri.repository;

import net.dsa.girigiri.domain.entity.CouponEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CouponRepository extends JpaRepository<CouponEntity, Long> {

    // "내 쿠폰함" 목록용.
    List<CouponEntity> findByIssuedToUserIdOrderByCreatedAtDesc(Long userId);

    Optional<CouponEntity> findByIdAndIssuedToUserId(Long id, Long userId);

    // 웰컴 쿠폰 중복 발급 방지 — WelcomeCouponScheduler가 매 스캔마다 확인한다.
    boolean existsByIssuedToUserIdAndSource(Long userId, String source);

    // 프로모션 캠페인 1개당 회원 1명에게 1장만 — CouponService.claimCampaignCoupon()에서 확인한다.
    boolean existsByCampaignIdAndIssuedToUserId(Long campaignId, Long userId);

    // 슈퍼어드민 캠페인 목록 화면에 "지금까지 몇 명이 받았는지" 보여줄 때 쓴다.
    long countByCampaignId(Long campaignId);
}

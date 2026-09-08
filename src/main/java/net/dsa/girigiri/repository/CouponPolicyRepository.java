package net.dsa.girigiri.repository;

import net.dsa.girigiri.domain.entity.CouponPolicyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/** 쿠폰 정책(웰컴/매장취소보상 할인율) — 항상 1행(id=1)만 존재. CouponService#getOrCreatePolicy 참고. */
public interface CouponPolicyRepository extends JpaRepository<CouponPolicyEntity, Long> {
}

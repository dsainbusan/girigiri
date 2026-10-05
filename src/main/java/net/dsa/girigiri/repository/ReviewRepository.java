package net.dsa.girigiri.repository;

import net.dsa.girigiri.domain.entity.ReviewEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReviewRepository extends JpaRepository<ReviewEntity, Long> {

	// 추가됨 (2026-09-08, 코드 감사) — 매장 삭제 시 같이 지운다(SuperAdminStoreService#delete).
	void deleteByStoreId(Long storeId);

	// 추가됨 (강노은, 2026-10-01) — 이 예약(구매)이 이미 리뷰로 쓰였는지 확인 — "예약당 리뷰 1건" 검증용.
	boolean existsByReservationId(Long reservationId);
}

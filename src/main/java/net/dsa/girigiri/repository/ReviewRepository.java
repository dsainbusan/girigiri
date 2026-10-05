package net.dsa.girigiri.repository;

import net.dsa.girigiri.domain.entity.ReviewEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<ReviewEntity, Long> {

	// 추가됨 (2026-09-08, 코드 감사) — 매장 삭제 시 같이 지운다(SuperAdminStoreService#delete).
	void deleteByStoreId(Long storeId);

	// 추가됨 (2026-10-05) — 왜: "주문당 리뷰 1개" 정책의 핵심 체크. 구매내역(픽업완료 탭)에서
	// "이 주문엔 이미 리뷰를 썼나"를 판단하는 데 쓴다(ReservationService#toListItemDto).
	boolean existsByReservationId(Long reservationId);

	Optional<ReviewEntity> findByReservationId(Long reservationId);
}

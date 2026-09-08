package net.dsa.girigiri.repository;

import jakarta.persistence.LockModeType;
import net.dsa.girigiri.domain.entity.ProductEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<ProductEntity, Long> {

	// 동시 예약 시 재고 초과 방지용: 이 상품 row를 잠그고 조회한다 (같은 상품에 대한 다른 트랜잭션은 대기)
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from ProductEntity p where p.id = :id")
	Optional<ProductEntity> findByIdForUpdate(@Param("id") Long id);

	// 점주 대시보드용 (StoreController, WBS 3.0 문창호 담당)
	List<ProductEntity> findByStoreId(Long storeId);

	// 점주 재고 관리 목록용 (StoreProductController) — 최근 등록순
	List<ProductEntity> findByStoreIdOrderByRegisteredAtDesc(Long storeId);

	// 발행 안 된 초안 정리용 (ListingDraftScheduler.expireStaleDrafts)
	List<ProductEntity> findByStatus(String status);

	// 추가됨 (2026-09-08, 코드 감사) — 홈 화면 카드/추천 목록용. HomeService#getActiveStoreCards와
	// RecommendationService#getRecommendations가 각각 findAll() 후 자바에서 "active + 재고 있음"만
	// 걸러내던 걸 DB 쿼리로 옮긴다(둘이 완전히 같은 필터라 여기 하나로 통합).
	List<ProductEntity> findByStatusAndRemainingQuantityGreaterThan(String status, int remainingQuantity);
}

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
}

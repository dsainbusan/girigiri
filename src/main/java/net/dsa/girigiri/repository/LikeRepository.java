package net.dsa.girigiri.repository;

import net.dsa.girigiri.domain.entity.LikeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LikeRepository extends JpaRepository<LikeEntity, Long> {

	// 추가됨 (2026-09-08, 코드 감사) — 매장 삭제 시 같이 지운다(SuperAdminStoreService#delete).
	void deleteByStoreId(Long storeId);

	// 추가됨 (2026-09-08, 코드 감사) — LikeService#getLikedStoreIds가 findAll() 후 자바에서
	// userId로 거르던 걸 DB 쿼리로 옮긴다.
	List<LikeEntity> findByUserId(Long userId);

	// 추가됨 (2026-09-08, 코드 감사) — LikeService#toggle이 findAll() 후 자바에서 userId+storeId로
	// 거르던 걸 DB 쿼리로 옮긴다.
	List<LikeEntity> findByUserIdAndStoreId(Long userId, Long storeId);
}

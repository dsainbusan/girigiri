package net.dsa.girigiri.repository;

import net.dsa.girigiri.domain.entity.LikeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LikeRepository extends JpaRepository<LikeEntity, Long> {

	// 추가됨 (2026-09-08, 코드 감사) — 매장 삭제 시 같이 지운다(SuperAdminStoreService#delete).
	void deleteByStoreId(Long storeId);
}

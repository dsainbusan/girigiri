package net.dsa.girigiri.repository;

import net.dsa.girigiri.domain.entity.MenuItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * POS 카탈로그 메뉴 저장소 — 2026-08-27 신규 (문창호).
 */
@Repository
public interface MenuItemRepository extends JpaRepository<MenuItemEntity, Long> {

	List<MenuItemEntity> findByStoreIdOrderByNameAsc(Long storeId);

	Optional<MenuItemEntity> findByStoreIdAndPosSku(Long storeId, String posSku);

	void deleteByStoreId(Long storeId);

	long countByStoreId(Long storeId);
}

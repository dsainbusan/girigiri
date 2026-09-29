package net.dsa.girigiri.repository;

import net.dsa.girigiri.domain.entity.StoreAnnouncementEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StoreAnnouncementRepository extends JpaRepository<StoreAnnouncementEntity, Long> {

	List<StoreAnnouncementEntity> findByStoreIdOrderByCreatedAtDesc(Long storeId);

	// 다른 매장 소유자가 남의 공지 id를 URL에 넣어 수정/삭제 못 하게 storeId를 같이 확인한다.
	Optional<StoreAnnouncementEntity> findByIdAndStoreId(Long id, Long storeId);

	long countByStoreId(Long storeId);
}

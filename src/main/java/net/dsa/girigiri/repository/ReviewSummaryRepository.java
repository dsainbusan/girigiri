package net.dsa.girigiri.repository;

import net.dsa.girigiri.domain.entity.ReviewSummaryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReviewSummaryRepository extends JpaRepository<ReviewSummaryEntity, Long> {

	Optional<ReviewSummaryEntity> findByStoreId(Long storeId);
}

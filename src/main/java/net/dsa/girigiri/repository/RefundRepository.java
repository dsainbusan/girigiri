package net.dsa.girigiri.repository;

import net.dsa.girigiri.domain.entity.RefundEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefundRepository extends JpaRepository<RefundEntity, Long> {
	Optional<RefundEntity> findByOrderId(Long orderId);
}

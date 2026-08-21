package net.dsa.girigiri.repository;

import net.dsa.girigiri.domain.entity.ReceiptEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReceiptRepository extends JpaRepository<ReceiptEntity, Long> {

	Optional<ReceiptEntity> findByReservationId(Long reservationId);
}

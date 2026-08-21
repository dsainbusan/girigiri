package net.dsa.girigiri.repository;

import net.dsa.girigiri.domain.entity.PaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<PaymentEntity, Long> {

	Optional<PaymentEntity> findByMerchantUid(String merchantUid);

	Optional<PaymentEntity> findByReservationId(Long reservationId);
}

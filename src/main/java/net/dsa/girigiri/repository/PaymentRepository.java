package net.dsa.girigiri.repository;

import net.dsa.girigiri.domain.entity.PaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<PaymentEntity, Long> {

	Optional<PaymentEntity> findByMerchantUid(String merchantUid);

	Optional<PaymentEntity> findByReservationId(Long reservationId);

	// 매장 정산 집계용 (SettlementService, 문창호 2026-08-31)
	List<PaymentEntity> findByReservationIdIn(List<Long> reservationIds);
}

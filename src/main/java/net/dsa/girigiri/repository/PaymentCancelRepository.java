package net.dsa.girigiri.repository;

import net.dsa.girigiri.domain.entity.PaymentCancelEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 담당: 송채현 (WBS 5.0 예약·결제). 결제 하나(paymentId)에 쌓인 취소/환불 시도 이력을 시각 역순으로 조회한다. */
public interface PaymentCancelRepository extends JpaRepository<PaymentCancelEntity, Long> {

	List<PaymentCancelEntity> findByPaymentIdOrderByCreatedAtDesc(Long paymentId);
}

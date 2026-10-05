package net.dsa.girigiri.repository;

import net.dsa.girigiri.domain.entity.RefundEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefundRepository extends JpaRepository<RefundEntity, Long> {
	// order_id가 UNIQUE라 주문 1건당 환불 레코드는 많아야 1개 — 신고 상세의 "환불 완료" 배지,
	// AdminRefundService의 중복/재시도 판단 둘 다 이 조회 하나로 처리한다.
	Optional<RefundEntity> findByOrderId(Long orderId);
}

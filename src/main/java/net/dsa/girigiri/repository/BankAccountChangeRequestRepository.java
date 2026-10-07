package net.dsa.girigiri.repository;

import net.dsa.girigiri.domain.entity.BankAccountChangeRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

@Repository
public interface BankAccountChangeRequestRepository extends JpaRepository<BankAccountChangeRequestEntity, Long> {

	List<BankAccountChangeRequestEntity> findByStoreIdOrderByRequestedAtDesc(Long storeId);

	Optional<BankAccountChangeRequestEntity> findByStoreIdAndStatus(Long storeId, String status);

	boolean existsByStoreIdAndStatus(Long storeId, String status);

	List<BankAccountChangeRequestEntity> findByStatusOrderByRequestedAtAsc(String status);

	// 추가됨 (2026-10-07) — 승인 처리 중 동시에 다른 관리자가 같은 건을 승인/반려하는 걸 막는다
	// (AdminRefundService.findByIdForUpdate와 동일한 이유의 비관적 락).
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from BankAccountChangeRequestEntity r where r.id = :id")
	Optional<BankAccountChangeRequestEntity> findByIdForUpdate(@Param("id") Long id);
}

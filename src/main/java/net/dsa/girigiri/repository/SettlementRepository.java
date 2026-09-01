package net.dsa.girigiri.repository;

import net.dsa.girigiri.domain.entity.SettlementEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * 주간 정산 레코드 저장소 — WBS 2.0 (문창호, 2026-09-01).
 * 정산 확정(SettlementScheduler)·매장 정산 내역·슈퍼어드민 정산 지급 화면에서 사용.
 */
@Repository
public interface SettlementRepository extends JpaRepository<SettlementEntity, Long> {

	// 매장 "정산 내역" — 최근 주간이 위로
	List<SettlementEntity> findByStoreIdOrderByPeriodStartDesc(Long storeId);

	// 스케줄러 중복 생성 방지
	boolean existsByStoreIdAndPeriodStart(Long storeId, LocalDate periodStart);

	// 이월분 합산용: 그 매장의 아직 안 합쳐진 CARRIED 건들
	List<SettlementEntity> findByStoreIdAndStatus(Long storeId, String status);

	// 슈퍼어드민 정산 지급 화면 — 지급 대기 목록 (지급 예정일 빠른 순)
	List<SettlementEntity> findByStatusOrderByScheduledPayoutDateAsc(String status);

	// 슈퍼어드민 최근 정산 이력
	List<SettlementEntity> findTop100ByOrderByConfirmedAtDesc();
}

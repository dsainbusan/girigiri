package net.dsa.girigiri.repository;

import net.dsa.girigiri.domain.entity.StoreEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StoreRepository extends JpaRepository<StoreEntity, Long> {

	// 추가됨 (2026-08-21) — 왜: 점주(OWNER) 유저가 신청/등록한 매장 조회
	Optional<StoreEntity> findByOwnerId(Long ownerId);

	// 추가됨 (2026-08-21) — 왜: 슈퍼어드민(WBS 7.2 송보미) 입점 심사 대기 목록 조회용
	List<StoreEntity> findByApprovalStatus(String approvalStatus);

	// 추가됨 (2026-08-27) — 왜: ListingDraftScheduler가 POS 연동 + 재고 스냅샷 시각이 설정된 매장을 훑는다.
	List<StoreEntity> findByPosProviderIsNotNullAndPosDraftPromptTimeIsNotNull();

	// 추가됨 (2026-09-01, 문창호) — 왜: 주간 정산 스케줄러가 승인된 매장 전체를 훑는다.
	List<StoreEntity> findByApprovalStatusAndRole(String approvalStatus, String role);
}

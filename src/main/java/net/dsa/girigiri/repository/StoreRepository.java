package net.dsa.girigiri.repository;

import net.dsa.girigiri.domain.entity.StoreEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StoreRepository extends JpaRepository<StoreEntity, Long> {

	// 추가됨 (2026-08-21) — 왜: 점주(OWNER) 유저가 신청/등록한 매장 조회
	Optional<StoreEntity> findByOwnerId(Long ownerId);

	// 추가됨 (2026-09-08, 코드 감사) — 홈 화면 지도 마커용. HomeService#getMapStores가 findAll() 후
	// 자바에서 좌표 없음/정지 매장을 걸러내던 걸 DB 쿼리로 옮긴다. status는 null이면 ACTIVE와 동일하게
	// 취급하는 컬럼이라(StoreEntity.status 주석 참고), 파생 쿼리로 "StatusNot"을 쓰면 SQL에서
	// status IS NULL 행이 <> 비교에서 자동으로 빠져버려(NULL 비교는 NULL) 원래 로직과 결과가 달라진다
	// — 그래서 null 허용을 명시한 @Query로 직접 쓴다.
	@Query("select s from StoreEntity s where s.latitude is not null and s.longitude is not null "
			+ "and (s.status is null or s.status <> :suspendedStatus)")
	List<StoreEntity> findMapMarkerCandidates(@Param("suspendedStatus") String suspendedStatus);

	// 추가됨 (2026-08-21) — 왜: 슈퍼어드민(WBS 7.2 송보미) 입점 심사 대기 목록 조회용
	List<StoreEntity> findByApprovalStatus(String approvalStatus);

	// 추가됨 (2026-08-27) — 왜: ListingDraftScheduler가 POS 연동 + 재고 스냅샷 시각이 설정된 매장을 훑는다.
	List<StoreEntity> findByPosProviderIsNotNullAndPosDraftPromptTimeIsNotNull();

	// 추가됨 (2026-09-01, 문창호) — 왜: 주간 정산 스케줄러가 승인된 매장 전체를 훑는다.
	List<StoreEntity> findByApprovalStatusAndRole(String approvalStatus, String role);
}

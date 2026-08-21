package net.dsa.girigiri.repository;

import net.dsa.girigiri.domain.entity.ReservationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReservationRepository extends JpaRepository<ReservationEntity, Long> {

	// 픽업 현장에서 QR/픽업코드로 예약을 찾을 때 사용
	Optional<ReservationEntity> findByPickupCode(String pickupCode);

	// 마이페이지 예약 목록(진행중/픽업완료/노쇼·취소 탭)에서 사용. 최근 예약이 위로 오게 정렬.
	List<ReservationEntity> findByUserIdAndStatusInOrderByReservedAtDesc(Long userId, List<String> statuses);

	// 매장 신뢰도(취소율) 계산용: 그 매장의 전체 예약 수 / 그중 특정 주체(USER, STORE)가 취소한 수
	long countByStoreId(Long storeId);

	long countByStoreIdAndCancelledBy(Long storeId, String cancelledBy);

	// 노쇼 자동 처리용: 아직 픽업 안 됐는데(confirmed) 픽업 마감시간이 이미 지난 예약들
	List<ReservationEntity> findByStatusAndPickupTimeBefore(String status, java.time.LocalDateTime time);

	// 추가됨 (2026-08-21) — 왜: 노쇼 자동 처리가 이제 confirmed/ready 두 상태를 다 봐야 해서
	// (매장이 아직 안 왔거나, 수락은 했는데 손님이 안 온 경우 둘 다 노쇼 후보) 상태 여러 개로 조회.
	List<ReservationEntity> findByStatusIn(List<String> statuses);

	// 추가됨 (2026-08-21) — 왜: 사장님용 "들어온 예약 확인/수락" 목록 화면에서 사용.
	// 오래된 주문부터 보여줘서 먼저 들어온 걸 먼저 처리하게 한다.
	List<ReservationEntity> findByStatusOrderByReservedAtAsc(String status);

	// 추가됨 (2026-08-21) — 왜: 매장 취소 화면에서 픽업 코드를 직접 타이핑하는 대신, 지금 취소 가능한
	// (아직 픽업/취소/노쇼 안 된) 예약들을 목록으로 보여주고 그중에서 고르게 하려고 추가.
	List<ReservationEntity> findByStatusInOrderByReservedAtAsc(List<String> statuses);
}

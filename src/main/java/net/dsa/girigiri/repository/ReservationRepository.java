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
}

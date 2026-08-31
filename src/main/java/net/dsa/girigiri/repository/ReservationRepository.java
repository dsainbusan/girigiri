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

	// 변경됨 — 왜: storeId 조건이 없어서 로그인한 매장이 아니라 전체 매장의 예약이 다 섞여서 나오는
	// 버그가 있었다(다른 매장 주문을 수락/취소까지 눌러버릴 수 있는 상태). 사장님용 "들어온 예약
	// 확인/수락" 목록 화면에서 사용 — 오래된 주문부터 보여줘서 먼저 들어온 걸 먼저 처리하게 한다.
	List<ReservationEntity> findByStoreIdAndStatusOrderByReservedAtAsc(Long storeId, String status);

	// 변경됨 — 왜: 위와 같은 이유(storeId 누락). 매장 취소 화면에서 픽업 코드를 직접 타이핑하는 대신,
	// 지금 취소 가능한(아직 픽업/취소/노쇼 안 된) 그 매장의 예약들을 목록으로 보여주고 고르게 한다.
	List<ReservationEntity> findByStoreIdAndStatusInOrderByReservedAtAsc(Long storeId, List<String> statuses);

	// 점주 대시보드용 (StoreController, WBS 3.0 문창호 담당): 오늘 픽업 예정인 예약 목록
	List<ReservationEntity> findByStoreIdAndPickupTimeBetween(
			Long storeId, java.time.LocalDateTime start, java.time.LocalDateTime end);

	// 회원 탈퇴 시 미완료 예약(결제대기/진행중) 있으면 차단하기 위한 체크 (MypageController)
	boolean existsByUserIdAndStatusIn(Long userId, List<String> statuses);

	boolean existsByStoreIdAndStatusIn(Long storeId, List<String> statuses);

	// 점주용 "완료된 거래 내역" 화면에서 사용 — 최근 픽업 완료 건이 위로 오게 정렬.
	List<ReservationEntity> findByStoreIdAndStatusOrderByPickedAtDesc(Long storeId, String status);

	// 대시보드 "오늘 판매 현황" 도넛 카드용 — 오늘 등록된 상품들의 예약을 한 번에 조회해서
	// "판매(픽업완료)"와 "예약됨(픽업대기)"을 구분하는 데 쓴다.
	List<ReservationEntity> findByProductIdIn(List<Long> productIds);

	// 추가됨 (강노은) — 왜: 리뷰는 그 가게에서 실제로 예약·픽업까지 완료한 사용자만 쓸 수 있게 제한한다
	// (ReviewService#canWriteReview). status="picked"로 호출.
	boolean existsByUserIdAndStoreIdAndStatus(Long userId, Long storeId, String status);

	// 매장 정산 집계용 (SettlementService, 문창호 2026-08-31) — 그 매장의 전체 예약 → 결제 조인
	List<ReservationEntity> findByStoreId(Long storeId);
}
package net.dsa.girigiri.repository;

import jakarta.persistence.LockModeType;
import net.dsa.girigiri.domain.entity.ReservationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReservationRepository extends JpaRepository<ReservationEntity, Long> {

	// 추가됨 (2026-09-08, 코드 감사) — ProductRepository.findByIdForUpdate와 동일한 패턴.
	// 예약 취소/확정처럼 "읽고 상태 체크 후 쓰는" 흐름 전부가 이 락 없는 findById를 쓰고 있어서
	// 더블클릭이나 스케줄러와의 동시 실행이 재고 이중복구·이중환불로 이어질 수 있었다.
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from ReservationEntity r where r.id = :id")
	Optional<ReservationEntity> findByIdForUpdate(@Param("id") Long id);

	// 픽업 현장에서 QR/픽업코드로 예약을 찾을 때 사용
	Optional<ReservationEntity> findByPickupCode(String pickupCode);

	// 추가됨 (2026-09-08, 코드 감사) — ReservationService.generateUniquePickupCode()가 새로 생성한
	// 픽업 코드가 이미 쓰이고 있는지 확인할 때 사용. pickup_code에 걸린 DB unique 제약이 마지막
	// 안전망이라면, 이건 그 전에 미리 걸러서 저장 단계에서 DataIntegrityViolationException으로
	// 예약 생성 자체가 실패하는 걸 막는 1차 방어선.
	boolean existsByPickupCode(String pickupCode);

	// 마이페이지 예약 목록(진행중/픽업완료/노쇼·취소 탭)에서 사용. 최근 예약이 위로 오게 정렬.
	List<ReservationEntity> findByUserIdAndStatusInOrderByReservedAtDesc(Long userId, List<String> statuses);

	// 추가됨 (2026-09-09) — 슈퍼어드민 "회원 상세 → 예약 내역"용. 마이페이지와 달리 상태로 안 거르고
	// (탭 없이) 전부 최신순으로 한 번에 보여준다.
	List<ReservationEntity> findByUserIdOrderByReservedAtDesc(Long userId);

	// 매장 신뢰도(취소율) 계산용: 그 매장의 전체 예약 수 / 그중 특정 주체(USER, STORE)가 취소한 수
	long countByStoreId(Long storeId);

	long countByStoreIdAndCancelledBy(Long storeId, String cancelledBy);

	// 추가됨 (2026-09-08, 코드 감사) — 왜: getStoreCancelStats의 분모(total)를 countByStoreId로
	// 구하면 결제까지 안 가고 포기한(pending) 예약까지 다 세어버려서, 트래픽만 많고 결제 전환이
	// 낮은 매장일수록 분모가 부풀어 취소율이 실제보다 좋게(희석되어) 나온다. "결제까지 갔던"
	// 예약(pending 제외)만 분모로 삼기 위한 카운트.
	long countByStoreIdAndStatusNot(Long storeId, String status);

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

	// 추가됨 (2026-09-08, 코드 감사) — 홈 화면 "오늘 N명이 마감 음식을 구했어요" 배너용
	// (HomeService#getTodayRescueCount). 예전엔 findAll()로 예약 테이블 전체 이력을 매 홈 화면
	// 요청마다 훑었다 — 데이터가 쌓일수록 계속 느려지는 구조라 DB에서 바로 세도록 바꾼다.
	long countByStatusAndPickedAtBetween(String status, java.time.LocalDateTime start, java.time.LocalDateTime end);

	// 추가됨 (2026-09-08, 코드 감사) — ReservationService#expireStalePendingReservations가
	// findByStatusIn(pending) 전체를 끌고 온 다음 자바에서 reservedAt < cutoff로 거르던 걸 DB로 민다
	// (1분마다 도는 스케줄러라 pending 전체 테이블 스캔이 반복되는 구조였다).
	List<ReservationEntity> findByStatusAndReservedAtBefore(String status, java.time.LocalDateTime cutoff);

	// 추가됨 (2026-09-08, 코드 감사) — ReservationService#processNoShows도 같은 이유로 DB 필터로 민다.
	// isPastPickupDeadline(주문일 다음날 자정 지남)은 "reservedAt < 오늘 자정"과 수학적으로 동치라
	// 그대로 파생 쿼리로 옮길 수 있다(ReservationService 쪽 주석 참고).
	List<ReservationEntity> findByStatusInAndReservedAtBefore(List<String> statuses, java.time.LocalDateTime cutoff);

	// 추가됨 (2026-09-08, 코드 감사) — NotificationTriggerScheduler#scanReservationPickupSoon이
	// findByStatusIn(ready) 전체를 끌고 온 다음 자바에서 pickupTime 30분 창으로 거르던 걸 DB로 민다.
	List<ReservationEntity> findByStatusAndPickupTimeBetween(String status, java.time.LocalDateTime start, java.time.LocalDateTime end);

	// 추가됨 (2026-09-09) — 슈퍼어드민 "플랫폼 통계"(/superadmin/stats)의 기간 필터(오늘/7일/30일)용.
	// 예약이 실제로 접수된 시점(reservedAt) 기준으로 기간을 자른다 — "이 기간에 들어온 주문 중 몇 %가
	// 취소/노쇼됐는지"까지 같이 보여주려면 픽업완료(picked)만이 아니라 그 기간의 주문 전체가 필요하다.
	List<ReservationEntity> findByReservedAtBetween(java.time.LocalDateTime start, java.time.LocalDateTime end);
}
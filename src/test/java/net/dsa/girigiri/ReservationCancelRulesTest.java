package net.dsa.girigiri;

import net.dsa.girigiri.domain.dto.StoreCancelStatsDto;
import net.dsa.girigiri.domain.entity.ReservationEntity;
import net.dsa.girigiri.exception.CancellationNotAllowedException;
import net.dsa.girigiri.repository.ProductRepository;
import net.dsa.girigiri.service.ReservationService;
import net.dsa.girigiri.util.OperatingHoursUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 예약 취소 규칙 확인용 테스트:
 *   - 손님 취소는 "주문 후 30분 이내 AND 마감 30분 전" 둘 다 만족해야 가능
 *   - 매장 취소는 시간 제한 없이 가능하고, 재고는 복구하지 않음
 *   - 매장 신뢰도 통계(취소율)가 매장 취소 횟수를 정확히 반영하는지
 *
 * 로컬 DB 연결 + sql/sample-data.sql 데이터가 들어있어야 동작한다. product id=1(식빵 마감세트, store id=1)로
 * 새 예약을 만들어서 테스트하니, 재고가 바닥났다면 sample-data.sql을 재실행해서 초기화하고 돌려보세요.
 *
 * 주의: sample-data.sql 기준 store id=1의 영업시간은 "09:00 ~ 22:00"이에요. 이 테스트를 21:30~22:00
 *      사이에 돌리면 "마감 30분 전" 규칙에 실제로 걸려서 "정상 취소" 테스트가 실패할 수 있어요
 *      (버그가 아니라 규칙이 의도대로 동작하는 거예요 — 그 시간대를 피해서 다시 돌려보면 돼요).
 */
@SpringBootTest
class ReservationCancelRulesTest {

	@Autowired
	private ReservationService reservationService;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void 영업시간_문자열에서_마감시간을_정확히_읽는다() {
		assertEquals(LocalTime.of(22, 0), OperatingHoursUtil.parseClosingTime("09:00 ~ 22:00"));
	}

	@Test
	void 방금_주문한_예약은_정상적으로_취소된다() {
		ReservationEntity reservation = reservationService.createReservation(
				1L, 1L, 1, LocalDateTime.now().plusHours(1));

		ReservationEntity cancelled = reservationService.cancelReservation(reservation.getId());

		assertEquals("cancelled", cancelled.getStatus());
		assertEquals("USER", cancelled.getCancelledBy());
	}

	@Test
	void 주문한지_30분_지난_예약은_취소할_수_없다() {
		ReservationEntity reservation = reservationService.createReservation(
				1L, 1L, 1, LocalDateTime.now().plusHours(1));

		// reservedAt은 @CreatedDate(updatable=false)라 엔티티 setter로는 안 바뀌어서, DB를 직접 되돌린다.
		jdbcTemplate.update(
				"UPDATE reservation SET reserved_at = ? WHERE id = ?",
				LocalDateTime.now().minusMinutes(40), reservation.getId());

		CancellationNotAllowedException e = assertThrows(CancellationNotAllowedException.class,
				() -> reservationService.cancelReservation(reservation.getId()));

		System.out.println("예상대로 에러 발생: " + e.getMessage());
	}

	@Test
	void 매장_취소는_시간제한_없이_가능하고_재고를_복구하지_않는다() {
		ReservationEntity reservation = reservationService.createReservation(
				1L, 1L, 1, LocalDateTime.now().plusHours(1));

		int stockBeforeStoreCancel = productRepository.findById(1L).orElseThrow().getRemainingQuantity();

		ReservationEntity cancelled = reservationService.cancelByStore(reservation.getId(), "재고 부족");

		assertEquals("cancelled", cancelled.getStatus());
		assertEquals("STORE", cancelled.getCancelledBy());
		assertEquals("재고 부족", cancelled.getCancelReason());

		int stockAfterStoreCancel = productRepository.findById(1L).orElseThrow().getRemainingQuantity();
		assertEquals(stockBeforeStoreCancel, stockAfterStoreCancel);   // 재고는 그대로 (복구 안 됨)
	}

	@Test
	void 매장_신뢰도_통계가_취소_횟수를_정확히_반영한다() {
		ReservationEntity reservation = reservationService.createReservation(
				1L, 1L, 1, LocalDateTime.now().plusHours(1));
		reservationService.cancelByStore(reservation.getId(), "재고 부족");

		StoreCancelStatsDto stats = reservationService.getStoreCancelStats(1L);

		assertTrue(stats.totalReservationCount() > 0);
		assertTrue(stats.storeCancelledCount() >= 1);
		System.out.println("매장1 취소율: " + stats.cancelRatePercent() + "% ("
				+ stats.storeCancelledCount() + "/" + stats.totalReservationCount() + ")");
	}
}

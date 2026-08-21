package net.dsa.girigiri;

import net.dsa.girigiri.domain.entity.ReservationEntity;
import net.dsa.girigiri.repository.ReservationRepository;
import net.dsa.girigiri.service.ReservationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 노쇼 자동 처리(processNoShows) 확인용 테스트.
 *
 * pickupTime은 reservedAt(@CreatedDate)과 다르게 updatable 제한이 없는 평범한 컬럼이라,
 * ReservationCancelRulesTest처럼 JdbcTemplate로 DB를 직접 건드릴 필요 없이
 * 엔티티 setter + save()로 그냥 "픽업 시간이 이미 지난" 예약을 만들 수 있다.
 *
 * 로컬 DB 연결 + sql/sample-data.sql 데이터가 들어있어야 동작한다.
 */
@SpringBootTest
class ReservationNoShowTest {

	@Autowired
	private ReservationService reservationService;

	@Autowired
	private ReservationRepository reservationRepository;

	@Test
	void 픽업시간이_지난_confirmed_예약은_노쇼로_처리된다() {
		// 1. 정상적으로 예약을 하나 만든다 (지금은 픽업 시간이 미래).
		ReservationEntity reservation = reservationService.createReservation(
				1L, 1L, 1, LocalDateTime.now().plusHours(1));
		assertEquals("confirmed", reservation.getStatus());

		// 2. 픽업 시간을 강제로 과거로 되돌려서 "마감이 지났는데 안 픽업한" 상황을 만든다.
		reservation.setPickupTime(LocalDateTime.now().minusMinutes(10));
		reservationRepository.save(reservation);

		// 3. 스케줄러가 하는 일을 직접 호출해본다.
		int noShowCount = reservationService.processNoShows();
		assertTrue(noShowCount >= 1);

		// 4. 상태가 noshowed로 바뀌었는지 확인.
		ReservationEntity afterProcessing = reservationRepository.findById(reservation.getId()).orElseThrow();
		assertEquals("noshowed", afterProcessing.getStatus());
	}

	@Test
	void 픽업시간이_아직_안_지난_예약은_노쇼_처리되지_않는다() {
		ReservationEntity reservation = reservationService.createReservation(
				1L, 1L, 1, LocalDateTime.now().plusHours(2));

		reservationService.processNoShows();

		ReservationEntity stillConfirmed = reservationRepository.findById(reservation.getId()).orElseThrow();
		assertEquals("confirmed", stillConfirmed.getStatus());
	}
}

package net.dsa.girigiri;

import net.dsa.girigiri.domain.entity.ReservationEntity;
import net.dsa.girigiri.exception.PickupNotAllowedException;
import net.dsa.girigiri.service.ReservationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 사장님이 현장에서 QR/픽업코드를 확인했을 때 예약이 "픽업완료"로 잘 바뀌는지 확인하는 테스트.
 *
 * 로컬 DB 연결 + sql/sample-data.sql 데이터가 들어있어야 동작한다.
 * sample-data.sql 기준: 예약 id=1(pickup_code='PICK-1001')은 아직 픽업 전(confirmed),
 *                       예약 id=2(pickup_code='PICK-1002')는 이미 픽업 완료(picked) 상태다.
 *
 * 변경됨 (2026-08-21) — 왜: "매장이 수락(ready)하기 전에는 픽업 처리가 안 되게" 바뀌어서,
 * confirmed 상태인 예약을 바로 confirmPickup 하면 이제는 실패한다(PickupNotAllowedException).
 * 그래서 픽업이 성공하는 케이스는 먼저 acceptReservation으로 confirmed -> ready를 만든 뒤에
 * confirmPickup을 부르도록 바꾸고, "아직 매장이 수락 안 한 예약은 픽업이 막힌다"는 케이스를
 * 새로 추가했다.
 *
 * 주의: 테스트를 한 번 돌리면 예약 id=1이 진짜로 "picked"로 바뀌어요.
 *      그 상태에서 같은 테스트를 또 돌리면 "이미 수락됨/픽업됨" 에러가 나서 실패할 수 있어요.
 *      다시 처음부터 테스트하고 싶으면 sample-data.sql을 재실행해서 초기화하면 돼요.
 */
@SpringBootTest
class ReservationPickupTest {

	@Autowired
	private ReservationService reservationService;

	@Test
	void 매장이_수락한_뒤_픽업코드를_확인하면_픽업완료로_바뀐다() {
		// 매장이 먼저 확인(수락)해야 confirmed -> ready로 바뀌고, 그래야 픽업이 허용된다.
		ReservationEntity accepted = reservationService.acceptReservation(1L);
		assertEquals("ready", accepted.getStatus());
		assertNotNull(accepted.getAcceptedAt());

		ReservationEntity result = reservationService.confirmPickup("PICK-1001");

		assertEquals("picked", result.getStatus());
		assertNotNull(result.getPickedAt());

		System.out.println("예약 id=" + result.getId() + " 상태: " + result.getStatus());
		System.out.println("픽업 시각: " + result.getPickedAt());
	}

	@Test
	void 매장이_아직_수락하지_않은_예약은_픽업처리할_수_없다() {
		// sample-data.sql 기준 예약 id=1(PICK-1001)은 아직 confirmed(매장 미수락) 상태다.
		PickupNotAllowedException e = assertThrows(PickupNotAllowedException.class,
				() -> reservationService.confirmPickup("PICK-1001"));

		System.out.println("예상대로 에러 발생: " + e.getMessage());
	}

	@Test
	void 이미_픽업된_예약을_또_픽업처리하면_에러가_난다() {
		// 예약 id=2는 sample-data.sql에서 이미 picked 상태로 들어있다.
		PickupNotAllowedException e = assertThrows(PickupNotAllowedException.class,
				() -> reservationService.confirmPickup("PICK-1002"));

		System.out.println("예상대로 에러 발생: " + e.getMessage());
	}

	@Test
	void 존재하지_않는_픽업코드는_찾을_수_없다는_에러가_난다() {
		assertThrows(jakarta.persistence.EntityNotFoundException.class,
				() -> reservationService.confirmPickup("PICK-없는코드"));
	}
}

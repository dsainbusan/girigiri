package net.dsa.girigiri;

import net.dsa.girigiri.domain.dto.ReservationListItemDto;
import net.dsa.girigiri.service.ReservationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 마이페이지 예약 목록(진행중/픽업완료/노쇼·취소 탭)이 탭별로 올바른 예약만 보여주는지 확인하는 테스트.
 *
 * 로컬 DB 연결 + sql/sample-data.sql 데이터가 들어있어야 동작한다.
 * sample-data.sql 기준 user id=1의 예약: id=1(confirmed, 픽업 전) / id=3(pending).
 * user id=2의 예약(id=2, picked)은 다른 사람 거라 user id=1 목록엔 안 보여야 정상이다.
 *
 * 주의: ReservationPickupTest를 먼저 돌려서 예약 id=1이 이미 picked로 바뀐 상태라면
 *      이 테스트의 "진행중" 결과가 달라질 수 있어요. 그럴 땐 sample-data.sql을 재실행해서
 *      초기화한 다음 이 테스트를 돌려보세요.
 */
@SpringBootTest
class ReservationListTest {

	@Autowired
	private ReservationService reservationService;

	@Test
	void 진행중_탭에는_confirmed_상태_예약만_보인다() {
		List<ReservationListItemDto> result =
				reservationService.getMyReservations(1L, ReservationService.TAB_PROGRESS);

		assertEquals(1, result.size());
		ReservationListItemDto item = result.get(0);
		assertEquals(1L, item.reservationId());
		assertEquals("예약완료", item.statusBadge());   // 픽업 시간이 아직 안 지났으니 "예약완료"

		System.out.println("진행중 탭: " + result);
	}

	@Test
	void 픽업완료_탭에는_다른_사람_예약이_섞이지_않는다() {
		// user id=1의 예약 중엔 picked 상태가 없다 (id=2는 user id=2 소유라서 안 보여야 함)
		List<ReservationListItemDto> result =
				reservationService.getMyReservations(1L, ReservationService.TAB_PICKED);

		assertTrue(result.isEmpty());
		System.out.println("픽업완료 탭(user1): " + result);
	}

	@Test
	void pending_상태_예약은_어느_탭에도_안_보인다() {
		// id=3(user id=1, pending)이 progress/picked/cancelled 어디에도 안 걸리는지 확인
		List<ReservationListItemDto> progress = reservationService.getMyReservations(1L, ReservationService.TAB_PROGRESS);
		List<ReservationListItemDto> cancelled = reservationService.getMyReservations(1L, ReservationService.TAB_CANCELLED);

		assertTrue(progress.stream().noneMatch(r -> r.reservationId() == 3L));
		assertTrue(cancelled.stream().noneMatch(r -> r.reservationId() == 3L));
	}
}

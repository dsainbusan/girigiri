package net.dsa.girigiri;

import net.dsa.girigiri.domain.entity.PaymentEntity;
import net.dsa.girigiri.domain.entity.ReservationEntity;
import net.dsa.girigiri.repository.PaymentRepository;
import net.dsa.girigiri.repository.ReservationRepository;
import net.dsa.girigiri.service.ReservationService;
import net.dsa.girigiri.util.PortOneClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 노쇼 자동 처리(processNoShows) 확인용 테스트.
 *
 * 변경됨 (2026-08-24) — 왜: "마감시간 기준"(또는 그 뒤 24시간 유예)이 아니라 "주문일 다음날
 * 자정(00:00)이 지나면 노쇼"로 규칙이 바뀌면서(ReservationService.isPastPickupDeadline 참고),
 * 판단 기준이 pickupTime이 아니라 reservedAt(주문 시각)의 "날짜"가 됐다. reservedAt은
 * @CreatedDate(updatable=false)라 엔티티 setter로는 안 바뀌어서, ReservationCancelRulesTest처럼
 * JdbcTemplate로 DB를 직접 되돌려서 "어제 주문한 예약"을 만든다.
 *
 * 변경됨 (2026-08-24, PortOne 연동) — 왜: createReservation() 하나로 바로 confirmed 예약을 만들던
 * 헬퍼가 prepareReservation()+confirmPayment() 두 단계로 나뉘면서, "노쇼가 되려면 일단 confirmed
 * 상태여야 한다"는 이 테스트의 전제도 그 두 단계를 거쳐야 만들 수 있게 됐다. 팀에 아직 PortOne
 * 테스트 계정이 없어서 PortOneClient를 목(mock)으로 바꿔 결제 성공을 가정한다
 * (ReservationCancelRulesTest.createConfirmedReservation과 동일한 이유/방식).
 *
 * 로컬 DB 연결 + sql/sample-data.sql 데이터가 들어있어야 동작한다.
 * 주의: 이 테스트를 자정 근처(23:59~00:01)에 돌리면 "오늘 주문한 예약"이 테스트 실행 도중 날짜가
 *      바뀌어버려 실패할 수 있어요 — 그 시간대를 피해서 돌리면 됩니다(버그 아니라 규칙이 의도대로
 *      동작하는 거예요, ReservationCancelRulesTest의 21:30~22:00 주의사항과 같은 종류).
 */
@SpringBootTest
class ReservationNoShowTest {

	@Autowired
	private ReservationService reservationService;

	@Autowired
	private ReservationRepository reservationRepository;

	@Autowired
	private PaymentRepository paymentRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@MockitoBean
	private PortOneClient portOneClient;

	/** prepareReservation()+confirmPayment()를 목(mock) PortOne 결제 성공으로 이어붙여 confirmed 예약을 만든다. */
	private ReservationEntity createConfirmedReservation(Long userId, Long productId, int quantity, LocalDateTime pickupTime) {
		ReservationEntity prepared = reservationService.prepareReservation(userId, productId, quantity, pickupTime);
		PaymentEntity payment = paymentRepository.findByReservationId(prepared.getId()).orElseThrow();
		when(portOneClient.verifyPayment(eq(payment.getMerchantUid()), anyInt()))
				.thenReturn(PortOneClient.PortOneVerifyResult.success("test-tx-" + prepared.getId(), prepared.getTotalPrice()));
		return reservationService.confirmPayment(prepared.getId(), payment.getMerchantUid());
	}

	@Test
	void 어제_주문해서_자정이_지난_confirmed_예약은_노쇼로_처리된다() {
		// 1. 정상적으로 예약을 하나 만들고 결제까지 확인시킨다.
		ReservationEntity reservation = createConfirmedReservation(
				1L, 1L, 1, LocalDateTime.now().plusHours(1));
		assertEquals("confirmed", reservation.getStatus());

		// 2. 주문 시각을 어제로 되돌려서 "다음날(=오늘) 자정이 이미 지난" 상황을 만든다.
		jdbcTemplate.update(
				"UPDATE reservation SET reserved_at = ? WHERE id = ?",
				LocalDateTime.now().minusDays(1), reservation.getId());

		// 3. 스케줄러가 하는 일을 직접 호출해본다.
		int noShowCount = reservationService.processNoShows();
		assertTrue(noShowCount >= 1);

		// 4. 상태가 noshowed로 바뀌었는지 확인.
		ReservationEntity afterProcessing = reservationRepository.findById(reservation.getId()).orElseThrow();
		assertEquals("noshowed", afterProcessing.getStatus());
	}

	@Test
	void 오늘_주문한_예약은_아직_자정_전이라_노쇼_처리되지_않는다() {
		ReservationEntity reservation = createConfirmedReservation(
				1L, 1L, 1, LocalDateTime.now().plusHours(2));

		reservationService.processNoShows();

		ReservationEntity stillConfirmed = reservationRepository.findById(reservation.getId()).orElseThrow();
		assertEquals("confirmed", stillConfirmed.getStatus());
	}
}

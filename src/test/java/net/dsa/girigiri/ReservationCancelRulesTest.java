package net.dsa.girigiri;

import net.dsa.girigiri.domain.dto.StoreCancelStatsDto;
import net.dsa.girigiri.domain.entity.PaymentEntity;
import net.dsa.girigiri.domain.entity.ReservationEntity;
import net.dsa.girigiri.exception.CancellationNotAllowedException;
import net.dsa.girigiri.repository.PaymentRepository;
import net.dsa.girigiri.repository.ProductRepository;
import net.dsa.girigiri.service.ReservationService;
import net.dsa.girigiri.util.OperatingHoursUtil;
import net.dsa.girigiri.util.PortOneClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

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
 *
 * 변경됨 (2026-08-24, PortOne 연동) — 왜: createReservation() 하나로 바로 confirmed 예약을 만들던
 * 이전 헬퍼가 prepareReservation()+confirmPayment() 두 단계로 나뉘면서, 취소 규칙을 테스트하려면
 * 먼저 confirmed 상태를 만들어야 하는 이 테스트도 같이 바뀌어야 했다. 팀에 아직 PortOne 테스트
 * 계정이 없어서 PortOneClient를 목(mock)으로 바꿔 "결제 성공"을 가정하는 createConfirmedReservation()
 * 헬퍼를 새로 만들었다 — 취소 규칙 자체(이 테스트의 진짜 관심사)는 confirmed 예약이기만 하면 되고
 * 어떻게 confirmed가 됐는지는 상관없으므로, 이렇게 바꿔도 테스트 의도는 그대로 유지된다.
 *
 * 변경됨 (2026-08-24, 취소/환불 로직 추가) — 왜: 예약 취소(cancelReservation/cancelByStore)가 이제
 * 결제가 paid였던 건에 한해 portOneClient.cancelPayment()를 실제로 호출한다
 * (ReservationService.markPaymentCancelled 참고). 이 테스트들이 만드는 예약은 전부 createConfirmedReservation()을
 * 거쳐 이미 paid 상태이므로, cancelPayment()도 미리 성공으로 스텁해두지 않으면 Mockito가 기본값(null)을
 * 돌려줘서 NullPointerException이 난다.
 */
@SpringBootTest
class ReservationCancelRulesTest {

	@Autowired
	private ReservationService reservationService;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private PaymentRepository paymentRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@MockitoBean
	private PortOneClient portOneClient;

	/**
	 * prepareReservation()+confirmPayment()를 목(mock) PortOne 결제 성공으로 이어붙여 confirmed 예약을 만든다.
	 * 이 테스트 파일의 각 테스트가 그 뒤 cancelReservation/cancelByStore로 취소까지 하므로, 실제 환불
	 * 요청(portOneClient.cancelPayment)도 성공으로 미리 스텁해둔다 — 그래야 Mockito 기본값(null) 때문에
	 * NullPointerException이 나지 않는다.
	 */
	private ReservationEntity createConfirmedReservation(Long userId, Long productId, int quantity, LocalDateTime pickupTime) {
		ReservationEntity prepared = reservationService.prepareReservation(userId, productId, quantity, pickupTime);
		PaymentEntity payment = paymentRepository.findByReservationId(prepared.getId()).orElseThrow();
		when(portOneClient.verifyPayment(eq(payment.getMerchantUid()), anyInt()))
				.thenReturn(PortOneClient.PortOneVerifyResult.success("test-tx-" + prepared.getId(), prepared.getTotalPrice()));
		when(portOneClient.cancelPayment(anyString(), anyString()))
				.thenReturn(PortOneClient.PortOneCancelResult.success());
		return reservationService.confirmPayment(prepared.getId(), payment.getMerchantUid());
	}

	@Test
	void 영업시간_문자열에서_마감시간을_정확히_읽는다() {
		assertEquals(LocalTime.of(22, 0), OperatingHoursUtil.parseClosingTime("09:00 ~ 22:00"));
	}

	@Test
	void 방금_주문한_예약은_정상적으로_취소된다() {
		ReservationEntity reservation = createConfirmedReservation(
				1L, 1L, 1, LocalDateTime.now().plusHours(1));

		ReservationEntity cancelled = reservationService.cancelReservation(reservation.getId());

		assertEquals("cancelled", cancelled.getStatus());
		assertEquals("USER", cancelled.getCancelledBy());
	}

	@Test
	void PortOne_환불_요청이_실패해도_예약_취소_자체는_그대로_처리된다() {
		ReservationEntity prepared = reservationService.prepareReservation(
				1L, 1L, 1, LocalDateTime.now().plusHours(1));
		PaymentEntity readyPayment = paymentRepository.findByReservationId(prepared.getId()).orElseThrow();
		when(portOneClient.verifyPayment(eq(readyPayment.getMerchantUid()), anyInt()))
				.thenReturn(PortOneClient.PortOneVerifyResult.success("test-tx-" + prepared.getId(), prepared.getTotalPrice()));
		ReservationEntity reservation = reservationService.confirmPayment(prepared.getId(), readyPayment.getMerchantUid());

		// 환불 API 자체가 실패하는 상황(네트워크 오류, PortOne 쪽 일시적 문제 등)을 가정한다.
		// 이것 때문에 cancelReservation() 전체가 실패해버리면(=재고 복구까지 롤백되면) "환불 API 한 번
		// 실패했다고 취소 자체가 안 되는" 더 이상한 상황이 되므로, 취소는 그대로 처리되고 실패 사유만
		// 결제 기록(failReason)에 남아야 한다 — ReservationService.markPaymentCancelled 참고.
		when(portOneClient.cancelPayment(eq(readyPayment.getMerchantUid()), anyString()))
				.thenReturn(PortOneClient.PortOneCancelResult.failed("PortOne 서버 응답 없음"));

		ReservationEntity cancelled = reservationService.cancelReservation(reservation.getId());
		assertEquals("cancelled", cancelled.getStatus());

		PaymentEntity paymentAfter = paymentRepository.findByReservationId(reservation.getId()).orElseThrow();
		assertEquals("cancelled", paymentAfter.getPayStatus());
		assertTrue(paymentAfter.getFailReason() != null && paymentAfter.getFailReason().contains("환불 실패"));

		System.out.println("환불 API가 실패해도 예약은 정상적으로 취소 처리됨 — failReason: " + paymentAfter.getFailReason());
	}

	@Test
	void 주문한지_30분_지난_예약은_취소할_수_없다() {
		ReservationEntity reservation = createConfirmedReservation(
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
		ReservationEntity reservation = createConfirmedReservation(
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
		ReservationEntity reservation = createConfirmedReservation(
				1L, 1L, 1, LocalDateTime.now().plusHours(1));
		reservationService.cancelByStore(reservation.getId(), "재고 부족");

		StoreCancelStatsDto stats = reservationService.getStoreCancelStats(1L);

		assertTrue(stats.totalReservationCount() > 0);
		assertTrue(stats.storeCancelledCount() >= 1);
		System.out.println("매장1 취소율: " + stats.cancelRatePercent() + "% ("
				+ stats.storeCancelledCount() + "/" + stats.totalReservationCount() + ")");
	}
}

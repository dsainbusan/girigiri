package net.dsa.girigiri;

import net.dsa.girigiri.domain.entity.ProductEntity;
import net.dsa.girigiri.domain.entity.ReservationEntity;
import net.dsa.girigiri.exception.CancellationNotAllowedException;
import net.dsa.girigiri.repository.PaymentRepository;
import net.dsa.girigiri.repository.ProductRepository;
import net.dsa.girigiri.repository.ReservationRepository;
import net.dsa.girigiri.service.ReservationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 예약 취소가 재고 복구 + 결제 취소 표시 + 상태 변경까지 제대로 처리하는지 확인하는 테스트.
 *
 * 로컬 DB 연결 + sql/sample-data.sql 데이터가 들어있어야 동작한다.
 * sample-data.sql 기준: 예약 id=1(user1, product1, reserved_quantity=2, confirmed),
 *                       예약 id=2(user2, picked — 이미 픽업된 예약, 취소 불가 테스트용).
 *
 * 참고: sample-data.sql로 직접 넣은 예약이라 payment 테이블엔 연결된 기록이 없어요.
 *      (payment는 우리 코드의 createReservation()을 거쳐야만 같이 생기는 데이터라서요.)
 *      그래서 결제 취소 표시 검증은 "있으면 cancelled로 바뀌었는지"만 확인해요.
 *
 * 주의: 이 테스트를 한 번 돌리면 예약1이 진짜로 cancelled로 바뀌고 재고도 복구돼요.
 *      다시 테스트하려면 sample-data.sql을 재실행해서 초기화하세요.
 */
@SpringBootTest
class ReservationCancelTest {

	@Autowired
	private ReservationService reservationService;

	@Autowired
	private ReservationRepository reservationRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private PaymentRepository paymentRepository;

	@Test
	void 예약을_취소하면_재고가_복구되고_상태가_바뀐다() {
		ReservationEntity before = reservationRepository.findById(1L).orElseThrow();
		ProductEntity productBefore = productRepository.findById(before.getProductId()).orElseThrow();
		int stockBeforeCancel = productBefore.getRemainingQuantity();

		ReservationEntity cancelled = reservationService.cancelReservation(1L);
		assertEquals("cancelled", cancelled.getStatus());

		ProductEntity productAfter = productRepository.findById(before.getProductId()).orElseThrow();
		assertEquals(stockBeforeCancel + before.getReservedQuantity(), productAfter.getRemainingQuantity());

		paymentRepository.findByReservationId(1L).ifPresentOrElse(
				payment -> assertEquals("cancelled", payment.getPayStatus()),
				() -> System.out.println("이 예약은 결제 기록이 없어서(=sample-data로 직접 넣은 데이터) 건너뜀 — 정상이에요.")
		);

		System.out.println("취소 전 재고: " + stockBeforeCancel + " -> 취소 후 재고: " + productAfter.getRemainingQuantity());
	}

	@Test
	void 이미_픽업된_예약은_취소할_수_없다() {
		CancellationNotAllowedException e = assertThrows(CancellationNotAllowedException.class,
				() -> reservationService.cancelReservation(2L));

		System.out.println("예상대로 에러 발생: " + e.getMessage());
	}
}

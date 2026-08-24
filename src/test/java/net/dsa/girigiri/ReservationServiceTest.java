package net.dsa.girigiri;

import net.dsa.girigiri.domain.entity.PaymentEntity;
import net.dsa.girigiri.domain.entity.ProductEntity;
import net.dsa.girigiri.domain.entity.ReceiptEntity;
import net.dsa.girigiri.domain.entity.ReservationEntity;
import net.dsa.girigiri.exception.PaymentVerificationException;
import net.dsa.girigiri.repository.PaymentRepository;
import net.dsa.girigiri.repository.ProductRepository;
import net.dsa.girigiri.repository.ReceiptRepository;
import net.dsa.girigiri.repository.ReservationRepository;
import net.dsa.girigiri.service.ReservationService;
import net.dsa.girigiri.util.PortOneClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.File;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 지금까지 따로 만든 부품(재고 차감, QR 코드, 결제 기록)이 실제 "예약하기" 흐름 안에서
 * 제대로 이어붙는지 확인하는 테스트.
 *
 * 변경됨 (2026-08-24, PortOne 연동) — 왜: createReservation() 하나로 끝나던 흐름이
 * prepareReservation()(재고차감+pending 저장) -> confirmPayment()(PortOne 검증 후 confirmed 전환)
 * 두 단계로 나뉘면서, 이 테스트도 두 단계를 각각 확인하도록 나눴다. 팀에 아직 PortOne 테스트 계정이
 * 없어서(README.md 참고) 진짜 PortOne 서버에 물어볼 수는 없으니, PortOneClient를 목(mock)으로 바꿔서
 * "PortOne이 결제 완료라고 답했다"고 가정한 상태로 confirmPayment()의 나머지 로직(상태 전환, 결제
 * 기록 갱신, 영수증 생성)만 검증한다. 실제 PortOne 계정이 생기면 이 목 없이 진짜 결제로 한 번은
 * 끝까지(결제창 -> confirm-payment) 수동으로 확인해봐야 한다 — 이 테스트가 그 대체가 될 수는 없다.
 *
 * 로컬 DB 연결 + sql/sample-data.sql 데이터가 들어있어야 동작한다
 * (user id=1 "user01", product id=1 "식빵 마감세트", 재고 4개 기준).
 * 실행할 때마다 실제로 재고가 줄고 예약/결제 row가 새로 생기니, 여러 번 돌리면
 * 재고가 바닥나서 실패할 수 있다 — 그럴 땐 sample-data.sql을 다시 실행해서 초기화하면 된다.
 */
@SpringBootTest
class ReservationServiceTest {

	@Autowired
	private ReservationService reservationService;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private PaymentRepository paymentRepository;

	@Autowired
	private ReceiptRepository receiptRepository;

	@Autowired
	private ReservationRepository reservationRepository;

	// PortOne 테스트 계정이 아직 없어서, 실제 PortOne 서버 호출 대신 "결제 성공"을 가정하는 목으로 대체.
	@MockitoBean
	private PortOneClient portOneClient;

	@Test
	void 예약을_준비하면_재고차감_QR발급_결제ready기록까지_처리된다() {
		Long userId = 1L;      // sample-data.sql 기준 "user01"
		Long productId = 1L;   // sample-data.sql 기준 "식빵 마감세트", 재고 4개
		int quantity = 1;

		ProductEntity before = productRepository.findById(productId).orElseThrow();
		int stockBefore = before.getRemainingQuantity();

		ReservationEntity reservation = reservationService.prepareReservation(
				userId, productId, quantity, LocalDateTime.now().plusHours(2));

		// 1. 예약이 실제로 저장됐는지 (id가 생겼는지), 아직 결제 전이라 pending인지
		assertNotNull(reservation.getId());
		assertEquals("pending", reservation.getStatus());

		// 2. 가격이 상품의 할인가 * 수량으로 정확히 계산됐는지 (식빵 마감세트 3000원 * 1개)
		assertEquals(before.getDiscountedPrice() * quantity, reservation.getTotalPrice());

		// 3. 픽업용 QR 코드 문자열이 결제 전에도 미리 생성돼있는지
		assertNotNull(reservation.getPickupCode());
		assertTrue(reservation.getPickupCode().startsWith("PICK-"));

		// 4. 재고가 정확히 quantity만큼 이미 줄었는지 (결제 전이지만 결제창이 떠 있는 동안 다른 손님이
		//    같은 재고를 또 살 수 없어야 하기 때문)
		ProductEntity after = productRepository.findById(productId).orElseThrow();
		assertEquals(stockBefore - quantity, after.getRemainingQuantity());

		// 5. 결제 기록이 이 예약에 연결돼서 아직 "ready"(결제 대기) 상태로 저장됐는지
		PaymentEntity payment = paymentRepository.findByReservationId(reservation.getId()).orElseThrow();
		assertEquals("ready", payment.getPayStatus());
		assertEquals(reservation.getTotalPrice(), payment.getAmount());
		assertNotNull(payment.getMerchantUid());

		// 6. 아직 결제 확인 전이니 영수증은 만들어지지 않아야 한다
		assertTrue(receiptRepository.findByReservationId(reservation.getId()).isEmpty());

		System.out.println("prepare된 예약 id: " + reservation.getId() + " / paymentId(merchantUid): " + payment.getMerchantUid());
	}

	@Test
	void PortOne_결제검증에_성공하면_예약이_confirmed로_바뀌고_영수증까지_만들어진다() {
		Long userId = 1L;
		Long productId = 1L;
		int quantity = 1;

		ReservationEntity prepared = reservationService.prepareReservation(
				userId, productId, quantity, LocalDateTime.now().plusHours(2));
		PaymentEntity readyPayment = paymentRepository.findByReservationId(prepared.getId()).orElseThrow();

		// PortOne 서버에 실제로 물어보는 대신, "결제 완료(PAID)였다"고 답한 것으로 가정한다.
		when(portOneClient.verifyPayment(eq(readyPayment.getMerchantUid()), anyInt()))
				.thenReturn(PortOneClient.PortOneVerifyResult.success("test-tx-" + prepared.getId(), prepared.getTotalPrice()));

		ReservationEntity confirmed = reservationService.confirmPayment(prepared.getId(), readyPayment.getMerchantUid());

		// 1. 예약이 confirmed로 바뀌었는지
		assertEquals("confirmed", confirmed.getStatus());

		// 2. 결제 기록이 "paid"로 갱신되고 PortOne 거래번호(impUid)가 채워졌는지
		PaymentEntity paidPayment = paymentRepository.findByReservationId(confirmed.getId()).orElseThrow();
		assertEquals("paid", paidPayment.getPayStatus());
		assertNotNull(paidPayment.getPaidAt());
		assertEquals("test-tx-" + prepared.getId(), paidPayment.getImpUid());

		// 3. 결제가 확인된 이 시점에야 영수증이 실제로 만들어져서 DB에 기록됐는지. .env에 Supabase가
		//    설정돼 있으면 pdfUrl이 실제 http(s) URL이고, 아직 설정 전이면 ReceiptService가 로컬
		//    receipts/ 폴더에 저장한 파일 경로가 그대로 들어있다 — 둘 다 정상이라 어느 쪽인지 보고
		//    맞는 방식으로 확인한다.
		ReceiptEntity receipt = receiptRepository.findByReservationId(confirmed.getId()).orElseThrow();
		assertNotNull(receipt.getPdfUrl());
		String pdfUrl = receipt.getPdfUrl();
		if (pdfUrl.startsWith("http://") || pdfUrl.startsWith("https://")) {
			System.out.println("영수증이 Supabase Storage에 업로드됐어요: " + pdfUrl);
		} else {
			assertTrue(new File(pdfUrl).exists());
			System.out.println("Supabase가 아직 설정 안 돼있어서 로컬에 저장됐어요: " + new File(pdfUrl).getAbsolutePath());
			System.out.println("-> 저 파일 더블클릭해서 실제 예약 정보로 영수증이 잘 만들어졌는지 확인해보세요.");
		}

		System.out.println("confirmed된 예약 id: " + confirmed.getId());
		System.out.println("결제 상태: " + paidPayment.getPayStatus() + " / 금액: " + paidPayment.getAmount());
	}

	@Test
	void PortOne_결제검증에_실패하면_예약이_취소되고_재고가_복구된다() {
		Long userId = 1L;
		Long productId = 1L;
		int quantity = 1;

		ProductEntity before = productRepository.findById(productId).orElseThrow();
		int stockBeforePrepare = before.getRemainingQuantity();

		ReservationEntity prepared = reservationService.prepareReservation(
				userId, productId, quantity, LocalDateTime.now().plusHours(2));
		PaymentEntity readyPayment = paymentRepository.findByReservationId(prepared.getId()).orElseThrow();

		// 재고가 이미 줄어든 상태(결제 전)인 걸 먼저 확인
		assertEquals(stockBeforePrepare - quantity,
				productRepository.findById(productId).orElseThrow().getRemainingQuantity());

		// 결제가 실제로는 안 됐다고(예: 카드 승인 거절) PortOne이 답한 상황을 가정
		when(portOneClient.verifyPayment(eq(readyPayment.getMerchantUid()), anyInt()))
				.thenReturn(PortOneClient.PortOneVerifyResult.failed("카드 승인이 거절됐어요"));

		PaymentVerificationException e = assertThrows(PaymentVerificationException.class,
				() -> reservationService.confirmPayment(prepared.getId(), readyPayment.getMerchantUid()));
		System.out.println("예상대로 에러 발생: " + e.getMessage());

		// 예약은 cancelled로, 재고는 원래대로 복구됐는지
		ReservationEntity afterFail = reservationRepository.findById(prepared.getId()).orElseThrow();
		assertEquals("cancelled", afterFail.getStatus());
		assertEquals("SYSTEM", afterFail.getCancelledBy());
		assertEquals(stockBeforePrepare,
				productRepository.findById(productId).orElseThrow().getRemainingQuantity());
	}
}

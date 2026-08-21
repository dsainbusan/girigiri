package net.dsa.girigiri;

import net.dsa.girigiri.domain.entity.PaymentEntity;
import net.dsa.girigiri.domain.entity.ProductEntity;
import net.dsa.girigiri.domain.entity.ReceiptEntity;
import net.dsa.girigiri.domain.entity.ReservationEntity;
import net.dsa.girigiri.repository.PaymentRepository;
import net.dsa.girigiri.repository.ProductRepository;
import net.dsa.girigiri.repository.ReceiptRepository;
import net.dsa.girigiri.service.ReservationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 지금까지 따로 만든 부품(재고 차감, QR 코드, 결제 기록)이 실제 "예약 생성" 흐름 안에서
 * 제대로 이어붙는지 확인하는 테스트.
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

	@Test
	void 예약하면_재고차감_QR발급_결제기록까지_한번에_처리된다() {
		Long userId = 1L;      // sample-data.sql 기준 "user01"
		Long productId = 1L;   // sample-data.sql 기준 "식빵 마감세트", 재고 4개
		int quantity = 1;

		ProductEntity before = productRepository.findById(productId).orElseThrow();
		int stockBefore = before.getRemainingQuantity();

		ReservationEntity reservation = reservationService.createReservation(
				userId, productId, quantity, LocalDateTime.now().plusHours(2));

		// 1. 예약이 실제로 저장됐는지 (id가 생겼는지)
		assertNotNull(reservation.getId());
		assertEquals("confirmed", reservation.getStatus());

		// 2. 가격이 상품의 할인가 * 수량으로 정확히 계산됐는지 (식빵 마감세트 3000원 * 1개)
		assertEquals(before.getDiscountedPrice() * quantity, reservation.getTotalPrice());

		// 3. 픽업용 QR 코드 문자열이 실제로 생성됐는지
		assertNotNull(reservation.getPickupCode());
		assertTrue(reservation.getPickupCode().startsWith("PICK-"));

		// 4. 재고가 정확히 quantity만큼 줄었는지
		ProductEntity after = productRepository.findById(productId).orElseThrow();
		assertEquals(stockBefore - quantity, after.getRemainingQuantity());

		// 5. 결제 기록이 이 예약에 연결돼서 "paid" 상태로 저장됐는지
		PaymentEntity payment = paymentRepository.findByReservationId(reservation.getId()).orElseThrow();
		assertEquals("paid", payment.getPayStatus());
		assertEquals(reservation.getTotalPrice(), payment.getAmount());

		// 6. 영수증 PDF가 실제로 만들어져서 파일로 저장되고, DB에도 기록됐는지
		ReceiptEntity receipt = receiptRepository.findByReservationId(reservation.getId()).orElseThrow();
		assertNotNull(receipt.getPdfUrl());
		File pdfFile = new File("receipts/receipt-" + reservation.getId() + ".pdf");
		assertTrue(pdfFile.exists());

		System.out.println("생성된 예약 id: " + reservation.getId());
		System.out.println("픽업 코드: " + reservation.getPickupCode());
		System.out.println("결제 상태: " + payment.getPayStatus() + " / 금액: " + payment.getAmount());
		System.out.println("재고: " + stockBefore + " -> " + after.getRemainingQuantity());
		System.out.println("영수증 파일 저장 위치: " + pdfFile.getAbsolutePath());
		System.out.println("-> 저 파일 더블클릭해서 실제 예약 정보로 영수증이 잘 만들어졌는지 확인해보세요.");
	}
}

package net.dsa.girigiri;

import net.dsa.girigiri.domain.entity.ProductEntity;
import net.dsa.girigiri.repository.ProductRepository;
import net.dsa.girigiri.service.StockService;
import net.dsa.girigiri.util.QrCodeUtil;
import net.dsa.girigiri.util.ReceiptPdfGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.FileOutputStream;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 4개 부품(QR 생성 / 영수증 PDF 생성 / 재고 차감)이 실제로 동작하는지 눈으로 확인하기 위한 테스트.
 * 인텔리제이에서 각 메서드 옆 ▶ 버튼으로 하나씩 실행하면 된다.
 *
 * - qrCode_이미지가_생성된다, receiptPdf_파일이_생성된다: 로그인/DB 없이 바로 실행 가능.
 * - stock_재고가_차감된다: 로컬 DB 연결 + sql/sample-data.sql 데이터가 들어있어야 동작한다
 *   (product id=1 "식빵 마감세트", 재고 4개 기준). 실행할 때마다 실제로 재고가 줄어드니,
 *   여러 번 돌리면 결국 남은 수량이 바닥나서 OutOfStockException이 터지는 것도 확인해볼 수 있다.
 */
@SpringBootTest
class ReservationComponentsTest {

	@Autowired
	private StockService stockService;

	@Autowired
	private ProductRepository productRepository;

	@Test
	void qrCode_이미지가_생성된다() throws Exception {
		String pickupCode = QrCodeUtil.generatePickupCode();
		byte[] qrImage = QrCodeUtil.generateQrImage(pickupCode, 300);

		assertTrue(qrImage.length > 0);

		String path = "test-output-qr.png";
		try (FileOutputStream out = new FileOutputStream(path)) {
			out.write(qrImage);
		}
		System.out.println("생성된 픽업 코드: " + pickupCode);
		System.out.println("QR 이미지 저장 위치: " + new java.io.File(path).getAbsolutePath());
		System.out.println("-> 프로젝트 탐색기에서 저 파일 더블클릭해서 QR 이미지가 맞는지 확인해보세요.");
	}

	@Test
	void receiptPdf_파일이_생성된다() throws Exception {
		ReceiptPdfGenerator.ReceiptData data = new ReceiptPdfGenerator.ReceiptData(
				"다이스키 베이커리",
				"식빵 마감세트",
				2,
				6000,
				LocalDateTime.now().plusHours(2),
				QrCodeUtil.generatePickupCode(),
				true,   // 이 테스트는 QR 포함 버전을 눈으로 확인하려는 거라 true
				null,   // 정상 영수증이라 안내 배너 없음
				false
		);

		byte[] pdf = ReceiptPdfGenerator.generate(data);
		assertTrue(pdf.length > 0);

		String path = "test-output-receipt.pdf";
		try (FileOutputStream out = new FileOutputStream(path)) {
			out.write(pdf);
		}
		System.out.println("영수증 PDF 저장 위치: " + new java.io.File(path).getAbsolutePath());
		System.out.println("-> 저 파일 더블클릭해서 영수증 내용 + QR 이미지가 잘 들어갔는지 확인해보세요.");
	}

	@Test
	void receiptPdf_노쇼는_QR이_빠져서_더_작다() throws Exception {
		String pickupCode = QrCodeUtil.generatePickupCode();

		ReceiptPdfGenerator.ReceiptData withQr = new ReceiptPdfGenerator.ReceiptData(
				"다이스키 베이커리", "식빵 마감세트", 2, 6000,
				LocalDateTime.now().plusHours(2), pickupCode, true, null, false);
		ReceiptPdfGenerator.ReceiptData withoutQr = new ReceiptPdfGenerator.ReceiptData(
				"다이스키 베이커리", "식빵 마감세트", 2, 6000,
				LocalDateTime.now().plusHours(2), pickupCode, false, null, false);

		byte[] pdfWithQr = ReceiptPdfGenerator.generate(withQr);
		byte[] pdfWithoutQr = ReceiptPdfGenerator.generate(withoutQr);

		assertTrue(pdfWithQr.length > 0);
		assertTrue(pdfWithoutQr.length > 0);
		// QR 이미지(base64 인코딩된 PNG)가 통째로 빠지니까, 없는 쪽이 확실히 더 작아야 정상이다.
		assertTrue(pdfWithoutQr.length < pdfWithQr.length);
	}

	@Test
	void receiptPdf_취소_안내_배너가_있으면_더_크다() throws Exception {
		String pickupCode = QrCodeUtil.generatePickupCode();

		// QR 포함 여부는 똑같이 맞춰두고(둘 다 false) 안내 배너 유무만 다르게 해서, 크기 차이가
		// 순수하게 배너 때문인지 확인한다.
		ReceiptPdfGenerator.ReceiptData withoutNotice = new ReceiptPdfGenerator.ReceiptData(
				"다이스키 베이커리", "식빵 마감세트", 2, 6000,
				LocalDateTime.now().plusHours(2), pickupCode, false, null, false);
		ReceiptPdfGenerator.ReceiptData withNotice = new ReceiptPdfGenerator.ReceiptData(
				"다이스키 베이커리", "식빵 마감세트", 2, 6000,
				LocalDateTime.now().plusHours(2), pickupCode, false,
				"이 예약은 취소되었어요. 결제하신 금액은 환불됩니다.", true);

		byte[] pdfWithoutNotice = ReceiptPdfGenerator.generate(withoutNotice);
		byte[] pdfWithNotice = ReceiptPdfGenerator.generate(withNotice);

		assertTrue(pdfWithoutNotice.length > 0);
		assertTrue(pdfWithNotice.length > 0);
		// 취소 안내 문구(+ 배너 스타일)가 추가로 들어가니 배너 있는 쪽이 더 커야 한다.
		assertTrue(pdfWithNotice.length > pdfWithoutNotice.length);
	}

	@Test
	void stock_재고가_차감된다() {
		Long productId = 1L; // sample-data.sql 기준 "식빵 마감세트", 재고 4개로 시작

		ProductEntity before = productRepository.findById(productId).orElseThrow();
		System.out.println("차감 전 남은 수량: " + before.getRemainingQuantity());

		stockService.decreaseStock(productId, 1);

		ProductEntity after = productRepository.findById(productId).orElseThrow();
		System.out.println("차감 후 남은 수량: " + after.getRemainingQuantity());

		assertTrue(after.getRemainingQuantity() == before.getRemainingQuantity() - 1);
	}
}

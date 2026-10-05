package net.dsa.girigiri;

import net.dsa.girigiri.domain.entity.ComplaintEntity;
import net.dsa.girigiri.domain.entity.PayStatus;
import net.dsa.girigiri.domain.entity.PaymentEntity;
import net.dsa.girigiri.domain.entity.RefundStatus;
import net.dsa.girigiri.domain.entity.ReservationEntity;
import net.dsa.girigiri.exception.AdminRefundNotAllowedException;
import net.dsa.girigiri.repository.ComplaintRepository;
import net.dsa.girigiri.repository.PaymentRepository;
import net.dsa.girigiri.repository.RefundRepository;
import net.dsa.girigiri.repository.ReservationRepository;
import net.dsa.girigiri.service.AdminRefundService;
import net.dsa.girigiri.util.PaymentGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * AdminRefundService(신고 기반 리팩터링 — 스펙 C) 단위/통합 테스트. sample-data.sql엔 payment 행이
 * 없어서(결제 연동 이전 데이터) 각 테스트가 필요한 PaymentEntity를 직접 만든다. PortOne 실 호출을
 * 피하려고 PaymentGateway는 MockitoBean으로 대체 — "진짜 PG 성공/실패"를 결정적으로 재현한다.
 */
@SpringBootTest
@Transactional
class AdminRefundServiceTest {

	@Autowired
	private AdminRefundService adminRefundService;
	@Autowired
	private ComplaintRepository complaintRepository;
	@Autowired
	private ReservationRepository reservationRepository;
	@Autowired
	private PaymentRepository paymentRepository;
	@Autowired
	private RefundRepository refundRepository;

	@MockitoBean
	private PaymentGateway paymentGateway;

	private Long pickedReservationId;
	private Long complaintId;

	@BeforeEach
	void setUp() {
		// sample-data.sql reservation id=2: picked 상태, 4500원. 결제 기록만 이 테스트가 직접 만든다.
		pickedReservationId = 2L;
		PaymentEntity payment = PaymentEntity.ready(pickedReservationId, "test-merchant-uid-2", 4500);
		payment.approve("test-imp-uid-2", 4500, "card", null);
		paymentRepository.save(payment);

		ComplaintEntity complaint = ComplaintEntity.builder()
				.targetName("다이스키 베이커리")
				.targetStoreId(1L)
				.targetReservationId(pickedReservationId)
				.reason("상품 상태 불량")
				.content("테스트용 신고")
				.reporterName("테스트유저")
				.reporterId(2L)
				.build();
		complaintId = complaintRepository.save(complaint).getId();
	}

	@Test
	void PG_환불이_성공하면_주문_refunded_신고_처리완료_답변저장까지_끝난다() {
		when(paymentGateway.cancelPayment(anyString(), anyString()))
				.thenReturn(new PaymentGateway.PaymentCancelResult(true, "test-merchant-uid-2", null));

		AdminRefundService.RefundResult result =
				adminRefundService.refund(complaintId, "신고 확인 후 환불", "환불 처리했습니다.", 4L);

		assertTrue(result.success());

		ReservationEntity reservation = reservationRepository.findById(pickedReservationId).orElseThrow();
		assertEquals("refunded", reservation.getStatus());
		assertEquals("ADMIN", reservation.getCancelledBy());

		ComplaintEntity complaint = complaintRepository.findById(complaintId).orElseThrow();
		assertEquals(ComplaintEntity.STATUS_RESOLVED, complaint.getStatus());
		assertEquals("환불 처리했습니다.", complaint.getAdminReply());

		assertEquals(PayStatus.CANCELLED, paymentRepository.findByReservationId(pickedReservationId).orElseThrow().getPayStatus());
		assertEquals(RefundStatus.DONE, refundRepository.findByOrderId(pickedReservationId).orElseThrow().getStatus());
	}

	@Test
	void PG_환불이_실패하면_주문과_신고_상태는_그대로_유지된다() {
		when(paymentGateway.cancelPayment(anyString(), anyString()))
				.thenReturn(new PaymentGateway.PaymentCancelResult(false, null, "PG 테스트 실패"));

		AdminRefundService.RefundResult result =
				adminRefundService.refund(complaintId, "신고 확인 후 환불", "환불 처리했습니다.", 4L);

		assertTrue(!result.success());
		assertEquals("PG 테스트 실패", result.failReason());

		ReservationEntity reservation = reservationRepository.findById(pickedReservationId).orElseThrow();
		assertEquals("picked", reservation.getStatus());   // 그대로 유지

		ComplaintEntity complaint = complaintRepository.findById(complaintId).orElseThrow();
		assertEquals(ComplaintEntity.STATUS_PENDING, complaint.getStatus());   // 그대로 유지

		assertEquals(RefundStatus.FAILED, refundRepository.findByOrderId(pickedReservationId).orElseThrow().getStatus());
	}

	@Test
	void 이미_환불된_주문을_다시_환불하려_하면_막는다() {
		when(paymentGateway.cancelPayment(anyString(), anyString()))
				.thenReturn(new PaymentGateway.PaymentCancelResult(true, "test-merchant-uid-2", null));
		adminRefundService.refund(complaintId, "1차 환불", "환불 처리했습니다.", 4L);

		assertThrows(AdminRefundNotAllowedException.class,
				() -> adminRefundService.refund(complaintId, "2차 환불 시도", "다시 환불", 4L));
	}

	@Test
	void 픽업완료_상태가_아닌_주문은_환불할_수_없다() {
		// sample-data.sql reservation id=1: confirmed 상태(아직 픽업 전)
		ComplaintEntity confirmedOrderComplaint = ComplaintEntity.builder()
				.targetName("다이스키 베이커리")
				.targetStoreId(1L)
				.targetReservationId(1L)
				.reason("상품 상태 불량")
				.content("테스트용 신고")
				.reporterName("테스트유저")
				.reporterId(1L)
				.build();
		Long id = complaintRepository.save(confirmedOrderComplaint).getId();

		assertThrows(AdminRefundNotAllowedException.class,
				() -> adminRefundService.refund(id, "환불 시도", "환불", 4L));
	}
}

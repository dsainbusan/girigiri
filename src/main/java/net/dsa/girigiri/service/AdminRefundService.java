package net.dsa.girigiri.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dsa.girigiri.domain.entity.ComplaintEntity;
import net.dsa.girigiri.domain.entity.PayStatus;
import net.dsa.girigiri.domain.entity.PaymentCancelEntity;
import net.dsa.girigiri.domain.entity.PaymentEntity;
import net.dsa.girigiri.domain.entity.RefundEntity;
import net.dsa.girigiri.domain.entity.RefundStatus;
import net.dsa.girigiri.domain.entity.ReservationEntity;
import net.dsa.girigiri.domain.entity.ReservationStatusHistoryEntity;
import net.dsa.girigiri.exception.AdminRefundNotAllowedException;
import net.dsa.girigiri.repository.ComplaintRepository;
import net.dsa.girigiri.repository.PaymentCancelRepository;
import net.dsa.girigiri.repository.PaymentRepository;
import net.dsa.girigiri.repository.RefundRepository;
import net.dsa.girigiri.repository.ReservationRepository;
import net.dsa.girigiri.repository.ReservationStatusHistoryRepository;
import net.dsa.girigiri.util.PortOneClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 신고 처리 중 슈퍼어드민이 "픽업완료(picked)" 주문을 환불하는 경로 (2026-10-06, 신고 기반 리팩터링).
 *
 * 기존 취소(ReservationService#cancelByAdmin 계열)는 픽업 전 예약만 대상이라 이미 음식을 받아간
 * 주문엔 쓸 수 없다 — 그래서 picked → refunded 전이를 별도 서비스로 분리했다. 재고는 복구하지
 * 않는다(음식이 이미 나갔다). PG 환불이 실패하면 예외 대신 RefundResult.fail을 돌려주고 FAILED로
 * 기록해 커밋한다(주문 상태·신고는 그대로, 같은 주문으로 재시도 가능).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminRefundService {

	private final ReservationRepository reservationRepository;
	private final ComplaintRepository complaintRepository;
	private final PaymentRepository paymentRepository;
	private final PaymentCancelRepository paymentCancelRepository;
	private final RefundRepository refundRepository;
	private final ReservationStatusHistoryRepository historyRepository;
	private final PortOneClient portOneClient;
	private final LookupService lookupService;

	public record RefundResult(boolean success, String failReason) {
		public static RefundResult ok() {
			return new RefundResult(true, null);
		}

		public static RefundResult fail(String reason) {
			return new RefundResult(false, reason);
		}
	}

	/** 환불 가능하면 null, 아니면 이유 메시지. 화면(버튼 노출)과 서버 검증이 같은 판정을 쓴다. */
	public String blockedRefundMessage(ReservationEntity reservation) {
		return switch (reservation.getStatus()) {
			case "picked" -> null;
			case "refunded" -> "이미 환불 처리된 주문이에요.";
			default -> "픽업 완료된 주문만 환불할 수 있어요. (현재 상태: " + reservation.getStatus() + ")";
		};
	}

	@Transactional(readOnly = true)
	public Optional<RefundEntity> findRefund(Long reservationId) {
		return refundRepository.findByOrderId(reservationId)
				.filter(refund -> refund.getStatus() == RefundStatus.DONE);
	}

	/** 환불 + 신고 처리완료 + 답변 저장을 한 트랜잭션으로 끝낸다. */
	@Transactional
	public RefundResult refund(Long complaintId, String reason, String replyContent, Long adminId) {
		ComplaintEntity complaint = lookupService.getComplaint(complaintId);
		if (complaint.getTargetReservationId() == null) {
			throw new AdminRefundNotAllowedException("신고와 연결된 주문이 없어 환불할 수 없어요.");
		}
		ReservationEntity reservation = lookupService.getReservation(complaint.getTargetReservationId());
		String blocked = blockedRefundMessage(reservation);
		if (blocked != null) {
			throw new AdminRefundNotAllowedException(blocked);
		}

		int amount = reservation.getTotalPrice();
		RefundEntity refund = refundRepository.findByOrderId(reservation.getId())
				.map(existing -> {
					existing.retry(complaintId, reason, adminId);
					return existing;
				})
				.orElseGet(() -> RefundEntity.requested(reservation.getId(), complaintId, amount, reason, adminId));
		refundRepository.save(refund);

		Optional<PaymentEntity> paymentOpt = paymentRepository.findByReservationId(reservation.getId());
		if (paymentOpt.isEmpty() || paymentOpt.get().getPayStatus() != PayStatus.PAID) {
			refund.fail();
			return RefundResult.fail("환불할 결제 내역(PAID)이 없어요.");
		}
		PaymentEntity payment = paymentOpt.get();

		PortOneClient.PortOneCancelResult result = portOneClient.cancelPayment(payment.getMerchantUid(), reason);
		paymentCancelRepository.save(PaymentCancelEntity.of(payment.getId(), payment.getAmount(), reason, result.cancelled()));
		if (!result.cancelled()) {
			refund.fail();
			log.warn("> [AdminRefundService] PortOne 환불 실패 - reservationId={}, 사유={}", reservation.getId(), result.failReason());
			return RefundResult.fail(result.failReason());
		}

		payment.applyCancel(reason);
		paymentRepository.save(payment);
		refund.done(payment.getMerchantUid());   // PG 환불 거래 참조값: 현재는 merchantUid 재사용

		String fromStatus = reservation.getStatus();
		reservation.setStatus("refunded");
		reservation.setCancelledBy("ADMIN");
		reservation.setCancelReason(reason.length() > 255 ? reason.substring(0, 255) : reason);
		reservationRepository.save(reservation);
		historyRepository.save(ReservationStatusHistoryEntity.of(reservation.getId(), fromStatus, "refunded", adminId, reason));

		complaint.setAdminReply(replyContent.trim());
		complaint.setStatus(ComplaintEntity.STATUS_RESOLVED);
		complaint.setResolvedAt(LocalDateTime.now());
		complaintRepository.save(complaint);
		return RefundResult.ok();
	}
}

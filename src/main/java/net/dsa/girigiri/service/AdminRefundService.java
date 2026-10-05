package net.dsa.girigiri.service;

import jakarta.persistence.EntityNotFoundException;
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
import net.dsa.girigiri.util.PaymentGateway;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 신고 처리로 발생하는 "관리자 환불" 전담 서비스. 2026-10-06, 신고 기반 리팩터링(스펙 C) 신설.
 *
 * ReservationService.cancelByAdmin과는 완전히 분리한다(스펙 C.7) — cancelByAdmin은 지금도
 * pending/confirmed/ready(아직 안 끝난 예약)의 "분쟁 조정 취소"만 다루고, checkCancellableState가
 * picked를 명시적으로 막는다. 여기는 정반대로 "이미 픽업 완료(picked)된 주문을, 신고 접수 후
 * 운영자가 환불하는" 케이스만 다룬다 — 라우팅은 슈퍼어드민 전용(SuperAdminAccessInterceptor가
 * role=ADMIN을 이미 강제)인 SuperAdminSupportController에서만 호출된다.
 *
 * 범위 밖(스펙 TODO, 이번에 구현 안 함): 부분 환불, 쿠폰 복원, 정산 차감 — 전액 환불만, 쓴 쿠폰은
 * 그대로 소멸, 이미 만들어진 정산(SettlementEntity) 레코드는 건드리지 않는다. 정산 "금액 집계"는
 * SettlementService.aggregate가 PaymentEntity.payStatus만 보고 다시 계산하므로 다음 정산 확정부터는
 * 자동으로 맞게 반영된다(이미 확정된 지난 정산을 소급 수정하는 건 "정산 차감"의 몫, 범위 밖).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminRefundService {

	private final ReservationRepository reservationRepository;
	private final PaymentRepository paymentRepository;
	private final PaymentCancelRepository paymentCancelRepository;
	private final RefundRepository refundRepository;
	private final ComplaintRepository complaintRepository;
	private final ReservationStatusHistoryRepository statusHistoryRepository;
	private final PaymentGateway paymentGateway;
	private final ReceiptService receiptService;

	/**
	 * reservation이 지금 "신고 처리 환불" 대상이 될 수 있는지 — 가능하면 null, 아니면 이유 메시지.
	 * blockedCancelMessage(ReservationService, 유저 취소 기준)와는 기준이 정반대다: 여기는 picked만
	 * 허용하고 나머지(아직 안 끝났거나, 이미 취소/노쇼/환불된 것)는 전부 막는다.
	 */
	public String blockedRefundMessage(ReservationEntity reservation) {
		return switch (reservation.getStatus()) {
			case "picked" -> null;
			case "refunded" -> "이미 환불 처리된 주문이에요.";
			case "cancelled" -> "이미 취소된 주문이라 환불할 수 없어요.";
			case "noshowed" -> "노쇼 처리된 주문이라 환불할 수 없어요.";
			default -> "아직 픽업 전인 주문이라 환불할 수 없어요. (현재 상태: " + reservation.getStatus() + ")";
		};
	}

	/**
	 * 신고(complaintId) 처리로 그 신고에 연결된 주문(complaint.targetReservationId)을 환불하고,
	 * 동시에 신고에 답변을 남기며 처리완료(RESOLVED)로 바꾼다 — 스펙 B.6 "확인 시 환불 + 처리완료 +
	 * 답변 저장"을 한 트랜잭션으로 묶는다.
	 *
	 * 처리 순서(스펙 C.10): refund REQUESTED 저장 → PG 호출 → 성공 시 DONE + 주문 refunded + 신고
	 * 처리완료 / 실패 시 FAILED(주문·신고 상태는 그대로 유지, 재시도 가능하도록 예외 없이 리턴).
	 */
	@Transactional
	public RefundResult refund(Long complaintId, String reason, String replyContent, Long adminUserId) {
		ComplaintEntity complaint = complaintRepository.findById(complaintId)
				.orElseThrow(() -> new EntityNotFoundException("신고를 찾을 수 없습니다. id=" + complaintId));
		if (complaint.getTargetReservationId() == null) {
			throw new AdminRefundNotAllowedException("이 신고는 연결된 주문이 없어서 환불할 수 없어요. 픽업 코드 검색으로 처리해주세요.");
		}
		Long reservationId = complaint.getTargetReservationId();

		// findByIdForUpdate로 락 — cancelReservation/cancelByAdmin과 동일한 이유(동시 처리 방지).
		ReservationEntity reservation = reservationRepository.findByIdForUpdate(reservationId)
				.orElseThrow(() -> new EntityNotFoundException("예약을 찾을 수 없습니다. id=" + reservationId));

		String blocked = blockedRefundMessage(reservation);
		if (blocked != null) {
			throw new AdminRefundNotAllowedException(blocked);
		}

		PaymentEntity payment = paymentRepository.findByReservationId(reservationId)
				.orElseThrow(() -> new EntityNotFoundException("결제 기록을 찾을 수 없습니다. reservationId=" + reservationId));
		if (payment.getPayStatus() != PayStatus.PAID) {
			throw new AdminRefundNotAllowedException("결제 완료(PAID) 상태가 아니라 환불할 수 없어요. 현재 결제 상태=" + payment.getPayStatus());
		}

		String resolvedReason = truncate(reason == null || reason.isBlank() ? "신고 처리로 환불됨" : reason, 255);

		// order_id UNIQUE라, 이미 이 주문에 대한 환불 레코드(과거 FAILED 등)가 있으면 재사용한다.
		RefundEntity refund = refundRepository.findByOrderId(reservationId)
				.map(existing -> {
					if (existing.getStatus() == RefundStatus.DONE) {
						throw new AdminRefundNotAllowedException("이미 환불 처리된 주문이에요.");
					}
					existing.retry(reservation.getTotalPrice(), resolvedReason, adminUserId);
					return existing;
				})
				.orElseGet(() -> RefundEntity.requested(reservationId, complaintId, reservation.getTotalPrice(), resolvedReason, adminUserId));
		refund = refundRepository.save(refund);

		PaymentGateway.PaymentCancelResult pgResult = paymentGateway.cancelPayment(payment.getMerchantUid(), resolvedReason);

		if (!pgResult.cancelled()) {
			refund.markFailed();
			refundRepository.save(refund);
			log.warn("> [AdminRefundService] PG 환불 실패 - complaintId={}, reservationId={}, 사유={}",
					complaintId, reservationId, pgResult.failReason());
			return RefundResult.failed(pgResult.failReason());
		}

		payment.applyCancel(resolvedReason);
		paymentRepository.save(payment);
		paymentCancelRepository.save(PaymentCancelEntity.of(payment.getId(), payment.getAmount(), resolvedReason, true));

		refund.markDone(pgResult.transactionId());
		refundRepository.save(refund);

		String fromStatus = reservation.getStatus();
		reservation.setStatus("refunded");
		reservation.setCancelledBy("ADMIN");
		reservation.setCancelReason(resolvedReason);
		reservationRepository.save(reservation);
		statusHistoryRepository.save(
				ReservationStatusHistoryEntity.of(reservationId, fromStatus, "refunded", adminUserId, resolvedReason));

		complaint.setStatus(ComplaintEntity.STATUS_RESOLVED);
		complaint.setAdminReply(replyContent);
		complaint.setResolvedAt(LocalDateTime.now());
		complaintRepository.save(complaint);

		receiptService.generateReceipt(reservationId);

		return RefundResult.done();
	}

	/** 신고 상세 화면이 "환불 완료(일시, 처리자)"를 보여줄 때 쓴다 — order_id는 UNIQUE라 최대 1건. */
	@Transactional(readOnly = true)
	public Optional<RefundEntity> findRefund(Long reservationId) {
		return refundRepository.findByOrderId(reservationId);
	}

	private String truncate(String value, int maxLength) {
		return value.length() <= maxLength ? value : value.substring(0, maxLength);
	}

	public record RefundResult(boolean success, String failReason) {
		public static RefundResult done() {
			return new RefundResult(true, null);
		}

		public static RefundResult failed(String reason) {
			return new RefundResult(false, reason);
		}
	}
}

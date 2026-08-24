package net.dsa.girigiri.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.CancellableReservationDto;
import net.dsa.girigiri.domain.dto.ReservationIncomingItemDto;
import net.dsa.girigiri.domain.dto.ReservationListItemDto;
import net.dsa.girigiri.domain.entity.PaymentEntity;
import net.dsa.girigiri.domain.entity.ProductEntity;
import net.dsa.girigiri.domain.entity.ReservationEntity;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.domain.dto.StoreCancelStatsDto;
import net.dsa.girigiri.exception.AcceptNotAllowedException;
import net.dsa.girigiri.exception.CancellationNotAllowedException;
import net.dsa.girigiri.exception.PaymentVerificationException;
import net.dsa.girigiri.exception.PickupNotAllowedException;
import net.dsa.girigiri.repository.PaymentRepository;
import net.dsa.girigiri.repository.ProductRepository;
import net.dsa.girigiri.repository.ReservationRepository;
import net.dsa.girigiri.repository.StoreRepository;
import net.dsa.girigiri.util.OperatingHoursUtil;
import net.dsa.girigiri.util.PortOneClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 예약 생성 흐름을 담당하는 서비스.
 * 지금까지 따로따로 만들어서 테스트했던 부품들(StockService, QrCodeUtil, Payment)을
 * 하나의 실제 "예약하기" 흐름으로 연결하는 역할이다.
 *
 * 주의: 로그인 기능이 아직 없어서 userId를 파라미터로 직접 받는다.
 *      나중에 로그인이 만들어지면, 컨트롤러에서 로그인 세션의 userId를 꺼내서
 *      이 메서드에 넘겨주기만 하면 되고, 이 서비스 내부는 안 바뀐다.
 *
 * 주의2 (2026-08-24, PortOne 연동) — 예전엔 "결제가 바로 성공했다"고 가정하고 createReservation()
 *      하나로 재고차감+결제기록+confirmed 저장까지 한 번에 끝냈는데, 이제 실제 결제창을 거치므로
 *      두 단계로 쪼갰다:
 *        1) prepareReservation() — 재고를 먼저 차감하고 예약을 "pending"으로 저장 + 결제 레코드를
 *           "ready"로 만들어 PortOne에 넘길 paymentId(=PaymentEntity.merchantUid)를 발급한다.
 *           (재고를 먼저 차감하는 이유: 결제창이 떠 있는 동안 다른 손님이 같은 재고를 또 살 수
 *            없어야 하기 때문 — 결제 실패/포기 시엔 재고를 다시 돌려놓는다.)
 *        2) confirmPayment() — 프론트에서 결제창 완료 후 넘어온 paymentId를 가지고, 서버가 직접
 *           PortOne에 물어봐서(PortOneClient) 진짜 결제가 됐는지 확인한 다음에야 "confirmed"로
 *           바꾼다. 실패하면 예약을 취소 처리하고 재고를 복구한다.
 *      결제창을 띄우지도 않고 이탈한 pending 예약은 expireStalePendingReservations()가 주기적으로
 *      정리한다 (NoShowScheduler 참고 — 같은 스케줄러에 얹어서 같이 돈다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationService {

	private static final DateTimeFormatter LIST_DISPLAY_FORMAT = DateTimeFormatter.ofPattern("MM월 dd일 HH:mm");

	// 마이페이지 탭 이름 -> 실제 DB status 값 매핑. ReservationEntity의 status 필드 주석과 동일한 규칙이다.
	public static final String TAB_PROGRESS = "progress";     // 진행중 (예약완료/픽업대기 통합)
	public static final String TAB_PICKED = "picked";         // 픽업완료
	public static final String TAB_CANCELLED = "cancelled";   // 노쇼·취소 통합

	// 손님 취소 허용 창: 주문 후 이 시간 이내, 그리고 매장 마감 이 시간 전까지만 취소 가능 (둘 다 만족해야 함)
	// (2026-08-24) — public으로 바꿈: 체크아웃 화면에서 "결제 전 30분 이내에만 취소 가능해요" 안내 문구를
	// 보여줄 때, "30"을 화면에 따로 하드코딩하지 않고 여기 값 하나만 그대로 가져다 쓰기 위해서.
	public static final int USER_CANCEL_WINDOW_MINUTES = 30;
	private static final int CLOSING_CUTOFF_MINUTES = 30;

	// 결제창을 띄운 채 결제를 끝내지 않고 이탈한 pending 예약을 "포기한 걸로 보고" 자동 취소하기까지
	// 기다리는 시간. 그동안 재고를 계속 붙잡고 있으면 다른 손님이 못 사니 너무 길게 두면 안 되지만,
	// 카드 인증 등으로 결제창 자체가 오래 걸릴 수도 있어서 너무 짧게도 안 둔다.
	public static final int PENDING_EXPIRY_MINUTES = 15;

	private final StockService stockService;
	private final ProductRepository productRepository;
	private final StoreRepository storeRepository;
	private final ReservationRepository reservationRepository;
	private final PaymentRepository paymentRepository;
	private final ReceiptService receiptService;
	private final PortOneClient portOneClient;

	/**
	 * 결제창을 띄우기 직전 단계 — 재고를 먼저 차감하고, 예약을 "pending" 상태로 저장한다.
	 * 아직 결제가 된 게 아니므로 영수증은 만들지 않는다 (confirmPayment 성공 시에만 만든다).
	 *
	 * @return 저장된 예약 (아직 pending 상태). 화면/컨트롤러는 이 반환값의 id로 결제 기록을
	 *         조회해서 PortOne.requestPayment()에 넘길 paymentId를 꺼내 쓰면 된다.
	 */
	@Transactional
	public ReservationEntity prepareReservation(Long userId, Long productId, int quantity, LocalDateTime pickupTime) {

		// 1. 재고 차감. 재고가 없으면 여기서 OutOfStockException이 터지면서 아래 코드는 실행되지 않는다.
		//    (동시에 여러 명이 예약해도 안전하게 처리되는 부분은 StockService가 이미 책임진다.)
		stockService.decreaseStock(productId, quantity);

		// 2. 가격 계산을 위해 상품 정보 조회
		ProductEntity product = productRepository.findById(productId)
				.orElseThrow(() -> new EntityNotFoundException("상품을 찾을 수 없습니다. id=" + productId));

		int totalPrice = product.getDiscountedPrice() * quantity;

		// 3. 픽업 확인용 QR 코드 문자열 생성 (결제 전이지만 미리 발급 — 픽업 코드 자체는 결제 여부와
		//    무관하게 예약 하나당 하나면 되고, confirmed로 바뀐 뒤에 새로 만들 이유가 없다)
		String pickupCode = net.dsa.girigiri.util.QrCodeUtil.generatePickupCode();

		// 4. 예약 레코드 저장 — 아직 결제 전이므로 pending으로 저장
		ReservationEntity reservation = ReservationEntity.builder()
				.userId(userId)
				.productId(productId)
				.storeId(product.getStoreId())
				.reservedQuantity(quantity)
				.totalPrice(totalPrice)
				.pickupTime(pickupTime)
				.pickupCode(pickupCode)
				.status("pending")
				.build();
		reservation = reservationRepository.save(reservation);

		// 5. 결제 레코드를 "ready"(결제 대기)로 미리 만들어둔다. merchantUid가 곧 PortOne의
		//    paymentId다 — 서버가 미리 발급해서 프론트에 내려주고, 프론트는 이 값 그대로
		//    PortOne.requestPayment()에 넘긴다 (프론트가 마음대로 paymentId를 만들게 하면 나중에
		//    confirmPayment에서 어떤 결제 기록과 매칭해야 할지 알 수 없어서, 반드시 서버가 먼저
		//    발급해야 한다).
		PaymentEntity payment = PaymentEntity.builder()
				.reservationId(reservation.getId())
				.merchantUid("MID-" + reservation.getId() + "-" + System.currentTimeMillis())
				.amount(totalPrice)
				.payStatus("ready")
				.build();
		paymentRepository.save(payment);

		return reservation;
	}

	/**
	 * 결제창(PortOne 브라우저 SDK)이 끝난 뒤, 프론트가 넘겨준 paymentId를 가지고 서버가 PortOne에
	 * 직접 재조회해서 진짜 결제가 됐는지 확인한다. 확인 없이 프론트 응답만 믿고 confirmed로 바꾸면
	 * 결제 안 하고도 예약을 확정시키는 조작이 가능해지므로, 이 서버 재검증은 생략하면 안 된다.
	 *
	 * 검증 실패(결제 미완료/금액 불일치/이미 처리된 예약 등)면 예약을 취소 처리하고 재고를 복구한
	 * 뒤 PaymentVerificationException을 던진다 — 실패한 채로 재고만 계속 붙잡고 있으면 안 되기 때문.
	 */
	@Transactional
	public ReservationEntity confirmPayment(Long reservationId, String paymentId) {
		ReservationEntity reservation = reservationRepository.findById(reservationId)
				.orElseThrow(() -> new EntityNotFoundException("예약을 찾을 수 없습니다. id=" + reservationId));

		if (!"pending".equals(reservation.getStatus())) {
			throw new PaymentVerificationException("이미 처리된 예약이에요 (현재 상태: " + reservation.getStatus() + ")");
		}

		PaymentEntity payment = paymentRepository.findByReservationId(reservationId)
				.orElseThrow(() -> new EntityNotFoundException("결제 기록을 찾을 수 없습니다. reservationId=" + reservationId));

		// 프론트가 엉뚱한 paymentId(다른 예약 것 등)를 실수로 넘겼는지 한 번 더 확인
		if (!payment.getMerchantUid().equals(paymentId)) {
			throw new PaymentVerificationException("결제 정보가 이 예약과 일치하지 않아요.");
		}

		PortOneClient.PortOneVerifyResult result = portOneClient.verifyPayment(paymentId, reservation.getTotalPrice());

		if (!result.paid()) {
			// 결제 실패/취소 -> 재고를 다시 돌려놓고, 예약도 취소된 걸로 확정지어야 한다
			// (pending으로 계속 남겨두면 재고를 영구히 붙잡고 있는 셈이라 다른 손님이 못 산다).
			stockService.restoreStock(reservation.getProductId(), reservation.getReservedQuantity());

			payment.setPayStatus("failed");
			payment.setFailReason(result.failReason());
			paymentRepository.save(payment);

			reservation.setStatus("cancelled");
			reservation.setCancelledBy("SYSTEM");
			reservation.setCancelReason("결제 실패: " + result.failReason());
			reservationRepository.save(reservation);

			throw new PaymentVerificationException(result.failReason());
		}

		// 결제 확인 완료 -> 결제 레코드를 실제 값으로 채우고, 예약을 confirmed로 전환
		payment.setPayStatus("paid");
		payment.setImpUid(result.transactionId());
		payment.setPaidAt(LocalDateTime.now());
		paymentRepository.save(payment);

		reservation.setStatus("confirmed");
		ReservationEntity saved = reservationRepository.save(reservation);

		// 결제가 실제로 확인된 이 시점에야 영수증 PDF를 만든다.
		receiptService.generateReceipt(saved.getId());

		return saved;
	}

	/**
	 * 결제창(PortOne)이 실패/취소로 끝났다는 걸 프론트가 그 즉시 알게 됐을 때 호출하는 메서드 —
	 * pending 예약을 바로 취소하고 재고를 즉시 복구한다.
	 *
	 * 추가됨 (2026-08-24) — 왜: expireStalePendingReservations()가 있긴 하지만, 그건 PENDING_EXPIRY_MINUTES
	 * (15분)가 지나야, 그것도 5분 주기 스케줄러가 다음에 돌 때야 정리해준다. 손님이 결제창을 그냥
	 * 닫아버린 순간 프론트는 이미 "결제 안 됐다"는 걸 확실히 알고 있는데, 그 상태에서 최대 20분 가까이
	 * 재고가 묶여 있는 것처럼 보이는 건 다른 손님 입장에서 이상하다("분명 남은 수량이 있다는데 왜
	 * 안 줄어드는지" 반대로 "왜 안 늘어나는지") — checkout.html의 startPayment()가 결제 실패/취소를
	 * 확인하는 즉시 이 메서드를 호출해서 바로 풀어준다.
	 *
	 * 결제가 이미 확정/취소/노쇼 등으로 처리된(=더 이상 pending이 아닌) 예약이면 아무것도 하지 않고
	 * 그대로 돌려준다 — 예를 들어 다른 탭에서 그 사이 결제가 성공해버린 경우처럼, pending이 아닌
	 * 예약까지 여기서 취소해버리면 안 되기 때문이다.
	 *
	 * 아직 결제 전(payStatus="ready")이라 PortOne에 실제로 돈이 오간 적이 없으므로, markPaymentCancelled와
	 * 달리 PortOne 환불 API는 호출하지 않는다.
	 */
	@Transactional
	public ReservationEntity cancelPendingReservation(Long reservationId) {
		ReservationEntity reservation = reservationRepository.findById(reservationId)
				.orElseThrow(() -> new EntityNotFoundException("예약을 찾을 수 없습니다. id=" + reservationId));

		if (!"pending".equals(reservation.getStatus())) {
			return reservation;
		}

		stockService.restoreStock(reservation.getProductId(), reservation.getReservedQuantity());

		paymentRepository.findByReservationId(reservationId).ifPresent(payment -> {
			payment.setPayStatus("cancelled");
			payment.setFailReason("결제창에서 취소/실패해서 손님이 결제를 끝내지 못함");
			paymentRepository.save(payment);
		});

		reservation.setStatus("cancelled");
		reservation.setCancelledBy("SYSTEM");
		reservation.setCancelReason("결제 미완료로 취소됨");
		return reservationRepository.save(reservation);
	}

	/**
	 * 결제창을 띄운 채로 결제를 끝내지 않고 이탈한(또는 결제창 자체를 아예 안 연 채 브라우저를 닫은)
	 * pending 예약들을 정리한다. PENDING_EXPIRY_MINUTES가 지난 pending 예약은 "결제 포기"로 보고
	 * 재고를 복구하며 자동 취소한다. NoShowScheduler가 노쇼 처리와 같은 주기로 이 메서드도 호출한다.
	 *
	 * (2026-08-24 추가된 cancelPendingReservation()과의 관계: 이 메서드는 프론트가 실패를 못 알아채고
	 * 그냥 브라우저를 닫아버린 경우처럼, 즉시 정리가 안 된 pending 예약들을 늦게라도 걸러내는
	 * "안전망"이다. cancelPendingReservation()이 정상적으로 매번 호출된다면 이 메서드가 처리할 대상은
	 * 거의 없어야 정상이다.)
	 *
	 * @return 이번에 자동 취소된 예약 개수
	 */
	@Transactional
	public int expireStalePendingReservations() {
		LocalDateTime cutoff = LocalDateTime.now().minusMinutes(PENDING_EXPIRY_MINUTES);
		List<ReservationEntity> stale = reservationRepository.findByStatusIn(List.of("pending")).stream()
				.filter(reservation -> reservation.getReservedAt() != null && reservation.getReservedAt().isBefore(cutoff))
				.toList();

		for (ReservationEntity reservation : stale) {
			stockService.restoreStock(reservation.getProductId(), reservation.getReservedQuantity());

			paymentRepository.findByReservationId(reservation.getId()).ifPresent(payment -> {
				payment.setPayStatus("cancelled");
				payment.setFailReason("결제 시간 초과로 자동 취소됨");
				paymentRepository.save(payment);
			});

			reservation.setStatus("cancelled");
			reservation.setCancelledBy("SYSTEM");
			reservation.setCancelReason("결제 시간 초과로 자동 취소됨");
		}
		reservationRepository.saveAll(stale);

		return stale.size();
	}

	/**
	 * 픽업 현장에서 QR/픽업코드를 확인했을 때 호출한다. 예약을 "picked" 상태로 바꾸고 픽업 시각을 기록한다.
	 * 이미 픽업됐거나, 취소/노쇼 처리됐거나, 아직 결제가 안 된 예약이면 PickupNotAllowedException을 던진다.
	 *
	 * 변경됨 (2026-08-21) — 왜: 결제만 끝났다고 바로 픽업이 되면, 매장이 실제로 그 주문을 확인하기도
	 * 전에 손님이 찾아와버릴 수 있다. "매장이 확인(수락)한 뒤에만 픽업 가능"하도록, confirmed 상태는
	 * 더 이상 픽업 허용 대상이 아니고 ready 상태만 허용한다 (acceptReservation 참고).
	 */
	@Transactional
	public ReservationEntity confirmPickup(String pickupCode) {
		ReservationEntity reservation = reservationRepository.findByPickupCode(pickupCode)
				.orElseThrow(() -> new EntityNotFoundException("픽업 코드를 찾을 수 없습니다: " + pickupCode));

		switch (reservation.getStatus()) {
			case "picked" -> throw new PickupNotAllowedException("이미 픽업 완료 처리된 예약이에요.");
			case "cancelled", "noshowed" -> throw new PickupNotAllowedException("취소되었거나 노쇼 처리된 예약이라 픽업할 수 없어요.");
			case "pending" -> throw new PickupNotAllowedException("아직 결제가 완료되지 않은 예약이에요.");
			case "confirmed" -> throw new PickupNotAllowedException("아직 매장에서 확인(수락)하지 않은 예약이에요. 예약 확인 화면에서 먼저 수락해주세요.");
			default -> { }   // "ready" 상태만 정상적으로 아래 로직 진행
		}

		reservation.setStatus("picked");
		reservation.setPickedAt(java.time.LocalDateTime.now());
		return reservationRepository.save(reservation);
	}

	/**
	 * 매장(사장님)이 "예약 확인" 화면에서 새로 들어온 주문을 수락한다 — confirmed -> ready.
	 * 이 수락이 끝나야 손님이 QR/픽업코드로 픽업 처리(confirmPickup)를 할 수 있다.
	 * 이미 수락됐거나, 픽업/취소/노쇼 처리된 예약을 또 수락하려 하면 AcceptNotAllowedException을 던진다.
	 */
	@Transactional
	public ReservationEntity acceptReservation(Long reservationId) {
		ReservationEntity reservation = reservationRepository.findById(reservationId)
				.orElseThrow(() -> new EntityNotFoundException("예약을 찾을 수 없습니다. id=" + reservationId));

		switch (reservation.getStatus()) {
			case "ready" -> throw new AcceptNotAllowedException("이미 수락된 예약이에요.");
			case "picked" -> throw new AcceptNotAllowedException("이미 픽업 완료된 예약이에요.");
			case "cancelled" -> throw new AcceptNotAllowedException("이미 취소된 예약이에요.");
			case "noshowed" -> throw new AcceptNotAllowedException("이미 노쇼 처리된 예약이에요.");
			case "pending" -> throw new AcceptNotAllowedException("아직 결제가 완료되지 않은 예약이에요.");
			default -> { }   // "confirmed" 상태만 정상적으로 아래 로직 진행
		}

		reservation.setStatus("ready");
		reservation.setAcceptedAt(LocalDateTime.now());
		return reservationRepository.save(reservation);
	}

	/**
	 * 사장님용 "들어온 예약 확인/수락" 목록 — 아직 수락 안 한(confirmed) 주문들을 오래된 순으로 보여준다.
	 */
	public List<ReservationIncomingItemDto> getIncomingReservations() {
		List<ReservationEntity> incoming = reservationRepository.findByStatusOrderByReservedAtAsc("confirmed");
		return incoming.stream().map(this::toIncomingItemDto).toList();
	}

	private ReservationIncomingItemDto toIncomingItemDto(ReservationEntity reservation) {
		ProductEntity product = productRepository.findById(reservation.getProductId())
				.orElseThrow(() -> new EntityNotFoundException("상품을 찾을 수 없습니다. id=" + reservation.getProductId()));

		return new ReservationIncomingItemDto(
				reservation.getId(),
				product.getName(),
				reservation.getReservedQuantity(),
				reservation.getTotalPrice(),
				reservation.getPickupCode(),
				reservation.getReservedAt() != null ? reservation.getReservedAt().format(LIST_DISPLAY_FORMAT) : "-"
		);
	}

	/**
	 * 손님이 직접 예약을 취소한다. 재고를 원래대로 돌려놓고, 결제 기록을 "cancelled"로 바꾸고,
	 * 예약 상태도 "cancelled"로 바꾼다. 이미 픽업했거나 이미 취소/노쇼된 예약은 취소할 수 없다.
	 *
	 * 취소 가능 시간 조건 (둘 다 만족해야 함 — 마감세일 음식은 시간이 지나면 손실이라, 취소 창을
	 * 너무 오래 열어두면 매장이 재판매할 시간이 없어지기 때문):
	 *   1) 주문(reservedAt) 후 30분 이내
	 *   2) 매장 마감(영업종료) 30분 전까지
	 */
	@Transactional
	public ReservationEntity cancelReservation(Long reservationId) {
		ReservationEntity reservation = reservationRepository.findById(reservationId)
				.orElseThrow(() -> new EntityNotFoundException("예약을 찾을 수 없습니다. id=" + reservationId));

		checkCancellableState(reservation);

		LocalDateTime now = LocalDateTime.now();

		// 1) 주문 후 30분이 지났으면 취소 불가
		if (reservation.getReservedAt() != null
				&& Duration.between(reservation.getReservedAt(), now).toMinutes() >= USER_CANCEL_WINDOW_MINUTES) {
			throw new CancellationNotAllowedException(
					"주문 후 " + USER_CANCEL_WINDOW_MINUTES + "분이 지나서 취소할 수 없어요.");
		}

		// 2) 매장 마감 30분 전이 지났으면(=마감이 임박했으면) 취소 불가
		StoreEntity store = storeRepository.findById(reservation.getStoreId())
				.orElseThrow(() -> new EntityNotFoundException("매장을 찾을 수 없습니다. id=" + reservation.getStoreId()));
		if (isTooCloseToClosing(store, now)) {
			throw new CancellationNotAllowedException(
					"매장 마감 " + CLOSING_CUTOFF_MINUTES + "분 전이라 취소할 수 없어요.");
		}

		// 3. 재고를 다시 돌려놓는다 (예약할 때 차감했던 만큼)
		stockService.restoreStock(reservation.getProductId(), reservation.getReservedQuantity());

		// 4. 결제가 이미 완료(paid)된 건이면 PortOne에 실제 환불도 요청하고, 로컬 기록도 "cancelled"로 바꾼다.
		markPaymentCancelled(reservationId, "손님 요청으로 취소");

		// 5. 예약 상태 변경 + 취소 주체 기록
		reservation.setStatus("cancelled");
		reservation.setCancelledBy("USER");
		ReservationEntity saved = reservationRepository.save(reservation);

		// 6. 영수증도 지금 시점에 바로 다시 만들어둔다 (취소 안내 배너 + QR 제외 버전으로).
		//    조회할 때마다 다시 만드는 대신, 상태가 바뀌는 "이 순간" 딱 한 번만 다시 만들면 된다.
		receiptService.generateReceipt(reservationId);

		return saved;
	}

	/**
	 * 매장(사장님)이 예약을 취소한다 — 예를 들어 재고 착오로 실제로는 팔 수 없는 상품인 경우.
	 * 손님 잘못이 전혀 없는 상황이라 시간 제한 없이 언제든 취소 가능하고, 재고는 복구하지 않는다
	 * (원래 재고가 없었던 게 취소 사유라서, 복구하면 실제로 없는 재고가 있는 것처럼 돼버린다 —
	 *  재고 수량 자체를 바로잡는 건 사장님이 재고 관리 화면에서 따로 처리해야 한다).
	 * 대신 결제는 확실히 취소(환불) 처리하고, 취소 주체/사유를 남겨서 매장 신뢰도 계산에 반영한다.
	 */
	@Transactional
	public ReservationEntity cancelByStore(Long reservationId, String reason) {
		ReservationEntity reservation = reservationRepository.findById(reservationId)
				.orElseThrow(() -> new EntityNotFoundException("예약을 찾을 수 없습니다. id=" + reservationId));

		checkCancellableState(reservation);

		String resolvedReason = (reason == null || reason.isBlank()) ? "매장 사정으로 취소됨" : reason;

		// 결제가 이미 완료(paid)된 건이면 PortOne에 실제 환불을 요청한다 — 손님 잘못이 아니므로
		// (매장 취소는 재고 착오 등 매장 사정이 이유라서) 로컬 상태는 예외 없이 항상 "cancelled"로 바뀐다.
		markPaymentCancelled(reservationId, resolvedReason);

		reservation.setStatus("cancelled");
		reservation.setCancelledBy("STORE");
		reservation.setCancelReason(resolvedReason);
		ReservationEntity saved = reservationRepository.save(reservation);

		// 취소 안내 배너 + QR 제외 버전으로 영수증도 이 시점에 바로 다시 만들어둔다.
		receiptService.generateReceipt(reservationId);

		return saved;
	}

	/**
	 * 매장 취소 화면용 "취소 가능한 예약" 목록 — checkCancellableState와 동일한 기준(픽업/취소/노쇼가
	 * 아닌 예약)으로, 오래된 주문부터 보여준다. 픽업 코드를 직접 타이핑하지 않고 여기서 골라 취소한다.
	 */
	public List<CancellableReservationDto> getCancellableReservations() {
		List<ReservationEntity> cancellable =
				reservationRepository.findByStatusInOrderByReservedAtAsc(List.of("pending", "confirmed", "ready"));
		return cancellable.stream().map(this::toCancellableDto).toList();
	}

	private CancellableReservationDto toCancellableDto(ReservationEntity reservation) {
		ProductEntity product = productRepository.findById(reservation.getProductId())
				.orElseThrow(() -> new EntityNotFoundException("상품을 찾을 수 없습니다. id=" + reservation.getProductId()));
		StoreEntity store = storeRepository.findById(reservation.getStoreId())
				.orElseThrow(() -> new EntityNotFoundException("매장을 찾을 수 없습니다. id=" + reservation.getStoreId()));

		return new CancellableReservationDto(
				reservation.getId(),
				reservation.getPickupCode(),
				product.getName(),
				reservation.getReservedQuantity(),
				reservation.getTotalPrice(),
				store.getStoreName(),
				resolveStatusBadge(reservation)
		);
	}

	/** 픽업/취소/노쇼처럼 이미 끝난 예약을 또 취소하려는 걸 막는 공통 상태 체크. */
	private void checkCancellableState(ReservationEntity reservation) {
		switch (reservation.getStatus()) {
			case "picked" -> throw new CancellationNotAllowedException("이미 픽업 완료된 예약은 취소할 수 없어요.");
			case "cancelled" -> throw new CancellationNotAllowedException("이미 취소된 예약이에요.");
			case "noshowed" -> throw new CancellationNotAllowedException("이미 노쇼 처리된 예약이라 취소할 수 없어요.");
			default -> { }   // "pending", "confirmed" 상태만 정상적으로 아래 로직 진행
		}
	}

	/**
	 * 매장 영업종료시간까지 30분 이내로 남았거나 이미 지났으면 true.
	 *
	 * 수정됨 (2026-08-24, 점검/정리) — 왜: OperatingHoursUtil.parseClosingTime은 operatingHours 형식이
	 * 이상하면(매장 정보 화면 쪽에서 자유 텍스트로 입력받는 값이라 완벽한 형식 보장이 없음)
	 * IllegalArgumentException을 던지는데, 여기서 그걸 안 잡고 있어서 손님이 "취소하기"를 눌렀을 때
	 * (cancelReservation 경유) 이 값 하나 때문에 예외가 그대로 튀어나가 알 수 없는 오류 화면으로 떨어질
	 * 위험이 있었다. 마감시간을 못 읽으면(파싱 실패) "마감이 임박했는지 알 수 없다"는 뜻이므로, 손님
	 * 취소를 막을 근거가 없다고 보고 안전한 쪽(false = 마감 임박 아님, 취소 허용)으로 처리한다.
	 */
	private boolean isTooCloseToClosing(StoreEntity store, LocalDateTime now) {
		LocalTime closingTime;
		try {
			closingTime = OperatingHoursUtil.parseClosingTime(store.getOperatingHours());
		} catch (IllegalArgumentException e) {
			return false;
		}
		LocalDateTime closingDateTime = LocalDateTime.of(LocalDate.now(), closingTime);
		return closingDateTime.isBefore(now.plusMinutes(CLOSING_CUTOFF_MINUTES));
	}

	/**
	 * 결제 기록을 취소 처리한다 — 로컬 DB 상태를 "cancelled"로 바꾸는 것뿐 아니라, 이미 실제로 결제가
	 * 완료(paid)된 건이었다면 PortOne에도 진짜 환불(결제 취소) 요청을 보낸다.
	 *
	 * 변경됨 (2026-08-24, 실 결제 연동 이후) — 왜: 그동안은 로컬 payStatus만 "cancelled"로 바꿔두고
	 * PortOne 쪽엔 아무것도 요청하지 않았다("실제 PortOne 환불 연동 전까지는 상태만 남긴다"). 이제 나이스
	 * (NICE) 테스트 채널로 실제 카드 결제가 되는 상태라, paid였던 예약을 취소할 땐 PortOne에도 취소를
	 * 요청해야 손님 카드로 실제 환불이 나간다. 아직 결제가 안 끝난 상태(payStatus="ready")에서 취소되는
	 * 경우엔 애초에 결제된 돈이 없으니 PortOne 호출 자체를 건너뛴다.
	 *
	 * PortOne 환불 요청이 실패해도(네트워크 오류거나, 나이스 테스트 모드 특성상 그날 밤 23시대에 이미
	 * 자동취소돼버린 경우 등) 예약 취소 자체를 막지는 않는다 — 이 메서드는 cancelReservation/
	 * cancelByStore의 @Transactional 안에서 호출되는데, 여기서 예외를 던지면 재고 복구까지 통째로
	 * 롤백돼버려서 "환불 API 한 번 실패했다고 취소 자체가 안 되는" 더 이상한 상황이 된다. 대신 실패
	 * 사유를 결제 기록(failReason)에 남기고 로그(log.warn)도 남겨서, 나중에 확인/수동 환불이 필요한
	 * 건을 놓치지 않게 한다.
	 */
	private void markPaymentCancelled(Long reservationId, String reason) {
		paymentRepository.findByReservationId(reservationId).ifPresent(payment -> {
			if ("paid".equals(payment.getPayStatus())) {
				PortOneClient.PortOneCancelResult result = portOneClient.cancelPayment(payment.getMerchantUid(), reason);
				if (result.cancelled()) {
					payment.setFailReason(null);
				} else {
					payment.setFailReason("환불 실패(수동 확인 필요): " + result.failReason());
					log.warn("> [ReservationService] PortOne 환불 실패 - reservationId={}, merchantUid={}, 사유={}",
							reservationId, payment.getMerchantUid(), result.failReason());
				}
			}

			payment.setPayStatus("cancelled");
			paymentRepository.save(payment);
		});
	}

	/** 매장 신뢰도(취소율) 통계: 전체 예약 중 "매장 사정으로" 취소된 비율. 손님 취소는 매장 잘못이 아니라서 뺀다. */
	public StoreCancelStatsDto getStoreCancelStats(Long storeId) {
		long total = reservationRepository.countByStoreId(storeId);
		long storeCancelled = reservationRepository.countByStoreIdAndCancelledBy(storeId, "STORE");
		double rate = total == 0 ? 0.0 : (storeCancelled * 100.0 / total);
		return new StoreCancelStatsDto(total, storeCancelled, rate);
	}

	/**
	 * 주문일 다음날 자정(00:00)이 지났는데도 아직 픽업 안 된(confirmed/ready 상태) 예약들을 전부
	 * "noshowed"로 바꾼다. NoShowScheduler가 주기적으로 이 메서드를 호출한다.
	 *
	 * 변경됨 (2026-08-24) — 왜: "당일 픽업 서비스니까 마감시간을 살짝 넘겨서라도 그날 안에 늦게
	 * 픽업하러 올 수도 있는데, 마감시간 지나자마자 바로(또는 거기서 24시간 뒤에) 노쇼 처리하는 건
	 * 기준이 애매하다. 그냥 날짜가 바뀌면(자정 지나면) 노쇼로 하자"는 피드백. 매장별 lastPickupTime은
	 * 더 이상 노쇼 판단에 안 쓰고(화면에 보여주는 "예상 픽업 가능 시각" 계산에만 쓰임), 순수하게
	 * 주문일 기준 다음날 00:00을 컷오프로 쓴다 — isPastPickupDeadline 참고.
	 *
	 * 손님 취소/매장 취소와 다르게, 노쇼는:
	 *   - 재고를 복구하지 않는다 (노쇼로 확정되는 시점엔 이미 날짜가 바뀌어서, 다시 팔 시간이 없다고 봄)
	 *   - 결제를 환불하지 않는다 (손님이 안 나타난 거라 매장 손실을 메워주는 취지로 결제는 그대로 둔다)
	 * 이 두 정책은 나중에 팀 논의에 따라 바뀔 수 있는 부분이라 여기 한곳에만 모아뒀다.
	 *
	 * @return 이번에 노쇼 처리된 예약 개수
	 */
	@Transactional
	public int processNoShows() {
		LocalDateTime now = LocalDateTime.now();
		List<ReservationEntity> candidates = reservationRepository.findByStatusIn(List.of("confirmed", "ready"));

		List<ReservationEntity> overdue = candidates.stream()
				.filter(reservation -> isPastPickupDeadline(reservation, now))
				.toList();

		for (ReservationEntity reservation : overdue) {
			reservation.setStatus("noshowed");
		}
		reservationRepository.saveAll(overdue);

		// 노쇼 안내 배너 + QR 제외 버전으로, 상태가 바뀐 이 시점에 영수증도 바로 다시 만들어둔다.
		for (ReservationEntity reservation : overdue) {
			receiptService.generateReceipt(reservation.getId());
		}

		return overdue.size();
	}

	/**
	 * "노쇼로 확정"할지는 매장의 마감시간(lastPickupTime)이 아니라, 주문한 날짜가 지나 자정(밤 12시)을
	 * 넘겼는지로 판단한다 — 주문일 다음날 00:00이 되는 순간 노쇼 확정.
	 *
	 * 변경됨 (2026-08-24) — 왜: 처음엔 "마감시각 + 24시간"으로 만들었는데, 그러면 마감시각이 몇 시냐에
	 * 따라 실제 유예 시간이 매장마다 달라 보일 수 있고(예: 마감 22:00이면 다음날 22:00까지, 마감
	 * 21:00이면 다음날 21:00까지 — 기준이 마감시각에 딸려 있어서 헷갈림), 정작 원하는 규칙은 "당일
	 * 픽업 서비스니까 그날 안에만 오면 되고, 자정 넘어가면(=날짜가 바뀌면) 그냥 노쇼"라는 더 단순한
	 * 기준이었다. 그래서 매장의 lastPickupTime/픽업 마감시각은 아예 안 보고, 주문일(reservedAt의
	 * 날짜) 다음날 자정(00:00)을 그대로 컷오프로 쓴다.
	 */
	private boolean isPastPickupDeadline(ReservationEntity reservation, LocalDateTime now) {
		if (reservation.getReservedAt() == null) {
			return false;   // 이론상 있을 수 없지만(reservedAt은 @CreatedDate), 방어적으로 판단 보류
		}

		LocalDateTime midnightAfterOrderDay = reservation.getReservedAt().toLocalDate().plusDays(1).atStartOfDay();
		return !now.isBefore(midnightAfterOrderDay);
	}

	/**
	 * 마이페이지 예약 목록용 데이터. tab에 따라 다른 status 값들을 조회해서 화면에 필요한 형태(DTO)로 가공해 돌려준다.
	 * tab 값은 TAB_PROGRESS / TAB_PICKED / TAB_CANCELLED 셋 중 하나.
	 */
	public List<ReservationListItemDto> getMyReservations(Long userId, String tab) {
		List<String> statuses = switch (tab) {
			case TAB_PROGRESS -> List.of("confirmed", "ready");   // (2026-08-21) 매장 수락 대기중/수락됨 둘 다 "진행중"
			case TAB_PICKED -> List.of("picked");
			case TAB_CANCELLED -> List.of("cancelled", "noshowed");
			default -> throw new IllegalArgumentException("알 수 없는 탭입니다: " + tab);
		};

		List<ReservationEntity> reservations =
				reservationRepository.findByUserIdAndStatusInOrderByReservedAtDesc(userId, statuses);

		return reservations.stream()
				.map(this::toListItemDto)
				.toList();
	}

	private ReservationListItemDto toListItemDto(ReservationEntity reservation) {
		ProductEntity product = productRepository.findById(reservation.getProductId())
				.orElseThrow(() -> new EntityNotFoundException("상품을 찾을 수 없습니다. id=" + reservation.getProductId()));
		StoreEntity store = storeRepository.findById(reservation.getStoreId())
				.orElseThrow(() -> new EntityNotFoundException("매장을 찾을 수 없습니다. id=" + reservation.getStoreId()));

		return new ReservationListItemDto(
				reservation.getId(),
				store.getStoreName(),
				product.getName(),
				reservation.getReservedQuantity(),
				reservation.getTotalPrice(),
				reservation.getPickupTime() != null ? reservation.getPickupTime().format(LIST_DISPLAY_FORMAT) : "-",
				reservation.getPickupCode(),
				resolveStatusBadge(reservation)
		);
	}

	/**
	 * DB status 값을 화면에 보여줄 한글 배지로 바꾼다.
	 *
	 * 변경됨 (2026-08-21) — 왜: 예전엔 confirmed 하나뿐인 상태를 pickupTime이 지났는지로
	 * "예약완료"/"픽업대기"로 나눠서 보여줬는데, 이제 매장 수락 여부 자체가 별도 상태(ready)로
	 * 분리돼서 시간 비교 없이 상태값 그대로 배지로 보여주면 된다.
	 */
	private String resolveStatusBadge(ReservationEntity reservation) {
		return switch (reservation.getStatus()) {
			case "pending" -> "결제 대기";       // (2026-08-21 추가) getCancellableReservations 목록에서 어색한 영문 노출 방지용
			case "confirmed" -> "주문 확인중";   // 결제완료, 매장이 아직 수락 전
			case "ready" -> "픽업 가능";         // 매장이 수락함, 손님이 와서 픽업하면 됨
			case "picked" -> "픽업완료";
			case "cancelled" -> "취소";
			case "noshowed" -> "노쇼";
			default -> reservation.getStatus();
		};
	}
}

package net.dsa.girigiri.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.CancellableReservationDto;
import net.dsa.girigiri.domain.dto.ReservationCompletedItemDto;
import net.dsa.girigiri.domain.dto.ReservationIncomingItemDto;
import net.dsa.girigiri.domain.dto.ReservationListItemDto;
import net.dsa.girigiri.domain.entity.PayStatus;
import net.dsa.girigiri.domain.entity.PaymentCancelEntity;
import net.dsa.girigiri.domain.entity.PaymentEntity;
import net.dsa.girigiri.domain.entity.ProductEntity;
import net.dsa.girigiri.domain.entity.ReservationEntity;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.domain.dto.StoreCancelStatsDto;
import net.dsa.girigiri.domain.entity.CouponEntity;
import net.dsa.girigiri.domain.entity.NotificationEntity;
import net.dsa.girigiri.exception.AcceptNotAllowedException;
import net.dsa.girigiri.exception.CancellationNotAllowedException;
import net.dsa.girigiri.exception.PaymentVerificationException;
import net.dsa.girigiri.exception.PickupNotAllowedException;
import net.dsa.girigiri.repository.PaymentCancelRepository;
import net.dsa.girigiri.repository.PaymentRepository;
import net.dsa.girigiri.repository.ProductRepository;
import net.dsa.girigiri.repository.ReservationRepository;
import net.dsa.girigiri.repository.StoreRepository;
import net.dsa.girigiri.util.OperatingHoursUtil;
import net.dsa.girigiri.util.PortOneClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionDefinition;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

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

	// 추가됨 (2026-09-08, 코드 감사) — 왜: 회원 탈퇴/매장 삭제를 막는 "미완료 예약" 기준이
	// MypageService/SuperAdminMemberService/SuperAdminStoreService 세 곳에 각자
	// List.of("pending", "confirmed")로 따로 선언돼 있었는데, "ready"(결제완료+매장수락, 픽업만
	// 남은 상태 — 계정/매장이 사라지면 환불 경로가 없어지는 바로 그 상태)가 세 곳 다 빠져 있었다.
	// 취소 가능 상태 목록(647번 줄 근처)과 동일한 기준이라 여기 하나로 모은다.
	public static final List<String> INCOMPLETE_STATUSES = List.of("pending", "confirmed", "ready");

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
	private final PaymentCancelRepository paymentCancelRepository;
	private final ReceiptService receiptService;
	private final PortOneClient portOneClient;
	private final PlatformTransactionManager transactionManager;
	// 추가됨 (2026-09-07, 송채현, WBS "쿠폰 발급/관리") — 쿠폰을 썼던 예약이 취소될 때 복구하고,
	// 매장 귀책 취소일 때는 보상 쿠폰을 새로 지급한다. 아래 각 취소/노쇼 처리 메서드 참고.
	private final CouponService couponService;
	// 매장 귀책 취소로 보상 쿠폰을 줄 때 손님에게 바로 알려주기 위해. 예약 이벤트를 폴링으로 감지하는
	// NotificationTriggerScheduler(강노은)와 달리, 여기는 내 파일이라 직접 호출한다 — 다른 사람 파일을
	// 안 건드리려고 폴링 방식을 쓰는 팀 컨벤션은 "남의 코드에 훅을 안 심는다"가 핵심이라 내 서비스
	// 안에서 다른 사람이 만든 공용 서비스(NotificationService)를 호출하는 것과는 상충하지 않는다.
	private final NotificationService notificationService;
	// 문창호 (2026-09-07) — 픽업 완료 시 매출 리포트(Supabase)에 즉시 반영. best-effort, 실패해도 픽업엔 영향 없음.
	private final SalesSyncService salesSyncService;

	/**
	 * 결제창을 띄우기 직전 단계 — 재고를 먼저 차감하고, 예약을 "pending" 상태로 저장한다.
	 * 아직 결제가 된 게 아니므로 영수증은 만들지 않는다 (confirmPayment 성공 시에만 만든다).
	 *
	 * @return 저장된 예약 (아직 pending 상태). 화면/컨트롤러는 이 반환값의 id로 결제 기록을
	 *         조회해서 PortOne.requestPayment()에 넘길 paymentId를 꺼내 쓰면 된다.
	 */
	@Transactional
	public ReservationEntity prepareReservation(Long userId, Long productId, int quantity, LocalDateTime pickupTime) {
		return prepareReservation(userId, productId, quantity, pickupTime, null);
	}

	/**
	 * 쿠폰을 적용한 예약 준비 — 2026-09-07 추가 (채채 확인, 체크아웃 쿠폰 연동, WBS "할인코드 적용/검증").
	 * couponId가 null이면 기존 동작(할인 없음)과 완전히 같다 — 위 4개 인자짜리 메서드가 이 메서드를
	 * couponId=null로 호출하도록 바꿔서, 이 클래스 밖의 다른 호출부(테스트 포함)는 하나도 안 바뀐다.
	 *
	 * 쿠폰 사용 처리(couponService.markUsed)는 재고 차감과 똑같이 "낙관적으로 먼저 써버리고, 실패하면
	 * 되돌리는" 방식이다 — 결제가 실패하거나 예약이 취소되면 ReservationService 안의 다른 메서드들
	 * (confirmPayment의 결제 실패 처리/cancelPendingReservation/expireStalePendingReservations/
	 * cancelReservation/cancelByStore)이 reservation.getCouponId()로 couponService.restore()를
	 * 호출해서 다시 쓸 수 있게 돌려놓는다 — 이 메서드에서 따로 롤백 처리를 안 해도 되는 이유.
	 */
	@Transactional
	public ReservationEntity prepareReservation(Long userId, Long productId, int quantity, LocalDateTime pickupTime, Long couponId) {

		// 1. 쿠폰을 골랐다면 재고를 건드리기 전에 먼저 검증한다 (본인 소유 + 미사용 + 미만료).
		//    유효하지 않으면 CouponService.validateForRedeem이 ResponseStatusException을 던지고,
		//    이 메서드가 @Transactional이라 여기까지 온 변경사항(아직 없음)은 자동 롤백된다.
		CouponEntity coupon = couponId != null ? couponService.validateForRedeem(userId, couponId) : null;

		// 2. 재고 차감. 재고가 없으면 여기서 OutOfStockException이 터지면서 아래 코드는 실행되지 않는다.
		//    (동시에 여러 명이 예약해도 안전하게 처리되는 부분은 StockService가 이미 책임진다.)
		stockService.decreaseStock(productId, quantity);

		// 3. 가격 계산을 위해 상품 정보 조회
		ProductEntity product = productRepository.findById(productId)
				.orElseThrow(() -> new EntityNotFoundException("상품을 찾을 수 없습니다. id=" + productId));

		int totalPrice = product.getDiscountedPrice() * quantity;
		if (coupon != null) {
			int discountAmount = totalPrice * coupon.getDiscountRate() / 100;
			totalPrice = Math.max(totalPrice - discountAmount, 0);
		}

		// 4. 픽업 확인용 QR 코드 문자열 생성 (결제 전이지만 미리 발급 — 픽업 코드 자체는 결제 여부와
		//    무관하게 예약 하나당 하나면 되고, confirmed로 바뀐 뒤에 새로 만들 이유가 없다)
		String pickupCode = generateUniquePickupCode();

		// 5. 예약 레코드 저장 — 아직 결제 전이므로 pending으로 저장
		ReservationEntity reservation = ReservationEntity.builder()
				.userId(userId)
				.productId(productId)
				.productName(product.getName())
				.storeId(product.getStoreId())
				.reservedQuantity(quantity)
				.totalPrice(totalPrice)
				.couponId(couponId)
				.pickupTime(pickupTime)
				.pickupCode(pickupCode)
				.status("pending")
				.build();
		reservation = reservationRepository.save(reservation);

		// 5-1. 쿠폰을 실제로 골랐으면 이 시점에 사용 처리한다 (재고와 동일한 낙관적 처리 — 위 메서드
		// 설명 참고). 같은 쿠폰으로 결제창을 여러 번 열어서 중복 적용하는 걸 여기서 막는다.
		if (couponId != null) {
			couponService.markUsed(couponId);
		}

		// 6. 결제 레코드를 "ready"(결제 대기)로 미리 만들어둔다. merchantUid가 곧 PortOne의
		//    paymentId다 — 서버가 미리 발급해서 프론트에 내려주고, 프론트는 이 값 그대로
		//    PortOne.requestPayment()에 넘긴다 (프론트가 마음대로 paymentId를 만들게 하면 나중에
		//    confirmPayment에서 어떤 결제 기록과 매칭해야 할지 알 수 없어서, 반드시 서버가 먼저
		//    발급해야 한다).
		PaymentEntity payment = PaymentEntity.ready(
				reservation.getId(),
				"MID-" + reservation.getId() + "-" + System.currentTimeMillis(),
				totalPrice);
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
	// 추가됨/변경됨 (2026-09-08, 코드 감사 — 두 번째 반영) — noRollbackFor: 아래 결제 검증 실패
	// 분기에서 PaymentVerificationException을 던지기 "전에" 재고복구/결제실패기록/예약취소를 같은
	// 트랜잭션 안에서 저장한다. 예전엔 이 부분을 별도 트랜잭션(REQUIRES_NEW, markPaymentFailedInNewTransaction)
	// 으로 커밋한 뒤에 예외를 던졌는데, findByIdForUpdate로 이 예약 행에 락을 걸어둔 상태에서
	// REQUIRES_NEW로 같은 행에 또 쓰려고 하면 서로 다른 커넥션끼리 같은 락을 기다리다 자기 자신과
	// 데드락이 난다(실제로 테스트에서 MySQL Lock wait timeout으로 재현 확인함, 2026-09-08). 별도
	// 트랜잭션 없이 noRollbackFor로 "이 예외가 나도 지금까지 한 저장은 롤백하지 말라"고 표시하면,
	// 커넥션을 하나만 쓰면서도 실패 처리가 안전하게 커밋된 뒤 예외가 호출부로 전달된다.
	@Transactional(noRollbackFor = PaymentVerificationException.class)
	public ReservationEntity confirmPayment(Long reservationId, String paymentId) {
		// findByIdForUpdate로 이 예약 행을 PortOne 검증 호출이 끝날 때까지 잠근다.
		// expireStalePendingReservations가 같은 예약을 "결제 안 하고 포기됨"으로 착각해 동시에
		// 취소시키던 레이스를 막는 핵심 — 그 스케줄러도 같은 락을 쓰므로 이 트랜잭션이 끝날 때까지
		// 대기했다가, 그때는 이미 상태가 pending이 아니라서 자기 조건문에서 자연히 스킵된다.
		ReservationEntity reservation = reservationRepository.findByIdForUpdate(reservationId)
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
			// 메서드 상단의 @Transactional(noRollbackFor=...) 덕분에, 아래에서 예외를 던져도
			// 이 저장들은 롤백되지 않고 그대로 커밋된다.
			stockService.restoreStock(reservation.getProductId(), reservation.getReservedQuantity());

			payment.fail(result.failReason());
			paymentRepository.save(payment);

			reservation.setStatus("cancelled");
			reservation.setCancelledBy("SYSTEM");
			reservation.setCancelReason("결제 실패: " + result.failReason());
			reservationRepository.save(reservation);

			// 결제가 실제로 안 됐으니 쿠폰을 쓴 적이 있어도 손님 잘못이 아니라 복구한다.
			couponService.restore(reservation.getCouponId());

			throw new PaymentVerificationException(result.failReason());
		}

		// 결제 확인 완료 -> 결제 레코드를 실제 값으로 채우고, 예약을 confirmed로 전환
		// (2026-08-25) paidAt은 이제 PortOne이 실제로 알려준 카드 승인 시각(pgPaidAt)을 쓴다 —
		// 예전엔 여기서 LocalDateTime.now()를 그대로 박아넣어서 "우리 서버가 확인한 시각"이었다.
		payment.approve(result.transactionId(), result.amount(), result.payMethod(), result.pgPaidAt());
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
		// 추가됨 (2026-09-08, 코드 감사) — findByIdForUpdate로 락 (아래 "다른 탭에서 결제가 성공해버린
		// 경우" 주석이 실제로 막히도록: 락 없이는 그 체크가 스냅샷일 뿐이었다).
		ReservationEntity reservation = reservationRepository.findByIdForUpdate(reservationId)
				.orElseThrow(() -> new EntityNotFoundException("예약을 찾을 수 없습니다. id=" + reservationId));

		if (!"pending".equals(reservation.getStatus())) {
			return reservation;
		}

		stockService.restoreStock(reservation.getProductId(), reservation.getReservedQuantity());

		paymentRepository.findByReservationId(reservationId).ifPresent(payment -> {
			payment.cancel("결제창에서 취소/실패해서 손님이 결제를 끝내지 못함");
			paymentRepository.save(payment);
		});

		reservation.setStatus("cancelled");
		reservation.setCancelledBy("SYSTEM");
		reservation.setCancelReason("결제 미완료로 취소됨");

		// 결제가 안 끝나서 취소된 거라 쿠폰을 썼어도 손님 잘못이 아니다 — 복구.
		couponService.restore(reservation.getCouponId());

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
	 *
	 * 전면 수정됨 (2026-09-08, 코드 감사) — 이전 구현은 아래 두 가지 문제가 있었다:
	 *   1) (치명적) confirmPayment()가 PortOne에 결제 확인을 묻는 동안엔 이 예약이 여전히 "pending"
	 *      상태라, 하필 그 순간 PENDING_EXPIRY_MINUTES가 지났으면 여기서 "결제 포기"로 착각하고
	 *      재고 복구 + 로컬 취소를 해버릴 수 있었다 — 카드는 이미 결제됐는데 예약은 취소된 채로
	 *      남고, PortOne에 환불 요청도 안 나가서 돈이 밖에 떠 있는 상황이 아무 데도 기록되지 않았다.
	 *      confirmPayment가 findByIdForUpdate로 락을 걸게 됐어도, "락을 걸기 직전"이나 "락을 풀고
	 *      커밋 완료 후 아주 잠깐" 같은 좁은 틈은 여전히 있어서, 로컬 상태만 보고 판단하면 안 된다.
	 *   2) (일반) 메서드 전체가 하나의 @Transactional이라, 루프 중간에 하나가 실패하면(예:
	 *      PaymentEntity.cancel()이 이미 PAID인 행에서 IllegalStateException을 던지는 경우) 그
	 *      배치 전체가 조용히 롤백됐다 — 이미 처리됐어야 할 다른 항목들까지 전부 원래대로 되돌아간다.
	 * 이제 후보 하나하나를 findByIdForUpdate로 다시 잠가서 최신 상태를 확인하고(1번의 절반), PortOne에
	 * 실제로 결제됐는지 마지막으로 한 번 더 물어본 뒤(1번 완전 해결 — 로컬 상태 대신 PG 원본을 믿는다),
	 * 항목마다 독립된 트랜잭션 + try/catch로 처리해서 하나가 실패해도 나머지는 그대로 진행된다(2번 해결).
	 */
	public int expireStalePendingReservations() {
		// 변경됨 (2026-09-08, 코드 감사) — findByStatusIn(pending) 전체를 끌고 온 다음 자바에서
		// reservedAt < cutoff로 거르던 걸 DB 쿼리로 민다 — 1분마다 도는 스케줄러가 매번 pending
		// 전체를 훑던 구조였다.
		LocalDateTime cutoff = LocalDateTime.now().minusMinutes(PENDING_EXPIRY_MINUTES);
		List<Long> staleIds = reservationRepository.findByStatusAndReservedAtBefore("pending", cutoff).stream()
				.map(ReservationEntity::getId)
				.toList();

		int expiredCount = 0;
		for (Long reservationId : staleIds) {
			try {
				if (expireOnePendingReservation(reservationId)) {
					expiredCount++;
				}
			} catch (Exception e) {
				log.error("> [ReservationService] 만료 예약 자동취소 처리 중 오류 (건너뛰고 계속 진행) - reservationId={}",
						reservationId, e);
			}
		}
		return expiredCount;
	}

	/**
	 * expireStalePendingReservations의 항목 1건 처리 — 독립된 트랜잭션(REQUIRES_NEW)으로 실행해서,
	 * 이 항목이 실패해도 이미 처리된 다른 항목의 커밋에는 영향이 없다. (같은 클래스 안에서 private
	 * 메서드에 @Transactional(REQUIRES_NEW)를 붙이면 프록시를 안 타서 조용히 무시되므로,
	 * TransactionTemplate을 직접 쓴다.)
	 *
	 * @return 실제로 취소 처리했으면 true, 그 사이 이미 처리됐거나(락 재확인 결과 pending이 아님)
	 *         PortOne에서 이미 결제완료로 확인돼 건너뛰었으면 false
	 */
	private boolean expireOnePendingReservation(Long reservationId) {
		TransactionTemplate requiresNew = new TransactionTemplate(transactionManager);
		requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		return Boolean.TRUE.equals(requiresNew.execute(status -> {
			// 후보 목록을 만든 뒤 여기 오기까지 시간이 걸릴 수 있으니, 락을 걸고 최신 상태를 다시
			// 확인한다 — confirmPayment()가 그 사이 먼저 확정(confirmed)했거나 다른 경로로 이미
			// 취소됐으면 더 이상 손댈 대상이 아니다.
			ReservationEntity reservation = reservationRepository.findByIdForUpdate(reservationId).orElse(null);
			if (reservation == null || !"pending".equals(reservation.getStatus())) {
				return false;
			}

			// 로컬 상태만 믿지 않고, 결제 시도 흔적(merchantUid)이 있으면 PortOne에 마지막으로 한 번 더
			// 확인한다 — confirmPayment 콜백을 못 받았을 뿐 실제로는 결제가 이미 완료된 경우, 여기서
			// 그냥 취소해버리면 "돈은 나갔는데 예약은 사라지는" 사고가 나기 때문이다. 이 경우는 자동
			// 처리하지 않고 크게 로그를 남겨서 사람이 확인하게 한다.
			Optional<PaymentEntity> paymentOpt = paymentRepository.findByReservationId(reservationId);
			if (paymentOpt.isPresent() && portOneClient.isConfigured()) {
				PortOneClient.PortOneVerifyResult result =
						portOneClient.verifyPayment(paymentOpt.get().getMerchantUid(), reservation.getTotalPrice());
				if (result.paid()) {
					log.error("> [ReservationService] 만료 처리 대상이지만 PortOne에는 이미 결제완료로 확인됨 - " +
									"자동 취소하지 않고 건너뜀(수동 확인 필요). reservationId={}, merchantUid={}",
							reservationId, paymentOpt.get().getMerchantUid());
					return false;
				}
			}

			stockService.restoreStock(reservation.getProductId(), reservation.getReservedQuantity());

			paymentOpt.ifPresent(payment -> {
				payment.cancel("결제 시간 초과로 자동 취소됨");
				paymentRepository.save(payment);
			});

			reservation.setStatus("cancelled");
			reservation.setCancelledBy("SYSTEM");
			reservation.setCancelReason("결제 시간 초과로 자동 취소됨");

			// 결제가 안 끝나서 취소된 거라 쿠폰을 썼어도 손님 잘못이 아니다 — 복구.
			couponService.restore(reservation.getCouponId());

			reservationRepository.save(reservation);
			return true;
		}));
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

		String blockedMessage = blockedPickupMessage(reservation);
		if (blockedMessage != null) {
			throw new PickupNotAllowedException(blockedMessage);
		}

		reservation.setStatus("picked");
		reservation.setPickedAt(java.time.LocalDateTime.now());
		ReservationEntity saved = reservationRepository.save(reservation);

		// 문창호 (2026-09-07) — 픽업이 확정됐으니 이 매장의 오늘 매출을 Supabase 매출 리포트에 반영.
		// SalesSyncService가 예외를 삼키므로 여기서 픽업 흐름이 깨질 일은 없다.
		salesSyncService.syncStoreToday(saved.getStoreId());

		return saved;
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
	 * 변경됨 — 왜: storeId 조건이 없어서 전체 매장 예약이 다 섞여서 나오던 버그를 고쳤다.
	 */
	public List<ReservationIncomingItemDto> getIncomingReservations(Long storeId) {
		List<ReservationEntity> incoming = reservationRepository.findByStoreIdAndStatusOrderByReservedAtAsc(storeId, "confirmed");
		return incoming.stream().map(this::toIncomingItemDto).toList();
	}

	/**
	 * 추가됨 — 왜: 매장이 수락(ready)했지만 손님이 아직 QR/코드를 안 보여줘서 픽업 처리가 안 된 예약들을
	 * 볼 방법이 없었다 — "손님이 안 왔나?" 확인하려면 이 목록이 필요하다. getIncomingReservations()와
	 * 데이터 모양이 완전히 같아서(상품/수량/금액/픽업코드/주문시각) DTO를 그대로 재사용한다.
	 * 변경됨 — 왜: 위와 동일한 이유로 storeId 조건 추가.
	 */
	public List<ReservationIncomingItemDto> getReadyReservations(Long storeId) {
		List<ReservationEntity> ready = reservationRepository.findByStoreIdAndStatusOrderByReservedAtAsc(storeId, "ready");
		return ready.stream().map(this::toIncomingItemDto).toList();
	}

	// 완료 내역 화면 — 날짜별 그룹 헤더 / 그룹 내 시각 표시용
	private static final DateTimeFormatter COMPLETED_DATE_FORMAT = DateTimeFormatter.ofPattern("MM월 dd일 (E)", Locale.KOREAN);
	private static final DateTimeFormatter COMPLETED_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

	/**
	 * 추가됨 — 왜: 손님이 실제로 픽업해서 거래가 끝난 내역을 점주가 볼 화면이 없었다. 매장별로 픽업
	 * 완료(picked) 예약만 최신순으로 보여준다.
	 */
	public List<ReservationCompletedItemDto> getCompletedTransactions(Long storeId) {
		return getCompletedTransactions(storeId, null, null, null);
	}

	/**
	 * 추가됨 (2026-08-30, 문창호) — 왜: 판매 내역이 쌓이면 원하는 걸 찾기 힘들다. 특정 날짜 / 그 날의
	 * 시간대(예: 8/26 10:00~12:00)로 좁혀 볼 수 있게 필터를 받는다. 편의점 POS의 "시간대별 매출 조회" 감각.
	 * date/from/to 모두 null이면 기존과 동일(전체, 최신순).
	 */
	public List<ReservationCompletedItemDto> getCompletedTransactions(Long storeId, LocalDate date, LocalTime from, LocalTime to) {
		return reservationRepository.findByStoreIdAndStatusOrderByPickedAtDesc(storeId, "picked").stream()
				.filter(r -> inPickupRange(r.getPickedAt(), date, from, to))
				.map(this::toCompletedItemDto)
				.toList();
	}

	private boolean inPickupRange(LocalDateTime pickedAt, LocalDate date, LocalTime from, LocalTime to) {
		if (date == null && from == null && to == null) {
			return true;
		}
		if (pickedAt == null) {
			return false;   // 필터가 걸린 상태에선 픽업시각 없는 데이터는 제외
		}
		if (date != null && !pickedAt.toLocalDate().equals(date)) {
			return false;
		}
		LocalTime t = pickedAt.toLocalTime();
		if (from != null && t.isBefore(from)) {
			return false;
		}
		return to == null || !t.isAfter(to);
	}

	private ReservationCompletedItemDto toCompletedItemDto(ReservationEntity reservation) {
		LocalDateTime pickedAt = reservation.getPickedAt();
		return new ReservationCompletedItemDto(
				reservation.getId(),
				reservation.getProductName(),
				reservation.getReservedQuantity(),
				reservation.getTotalPrice(),
				reservation.getPickupCode(),
				reservation.getReservedAt() != null ? reservation.getReservedAt().format(LIST_DISPLAY_FORMAT) : "-",
				pickedAt != null ? pickedAt.format(LIST_DISPLAY_FORMAT) : "-",
				// picked인데 picked_at이 비어 있는 건 정상 픽업 처리를 안 거친 이상 데이터(옛 시드/마이그레이션).
				// "없음"이 아니라 "미기록"으로 — 노쇼/취소가 아니라 시각만 안 남은 것.
				pickedAt != null ? pickedAt.format(COMPLETED_DATE_FORMAT) : "픽업 시각 미기록",
				pickedAt != null ? pickedAt.format(COMPLETED_TIME_FORMAT) : "-"
		);
	}

	private ReservationIncomingItemDto toIncomingItemDto(ReservationEntity reservation) {
		return new ReservationIncomingItemDto(
				reservation.getId(),
				reservation.getProductName(),
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
		// 추가됨 (2026-09-08, 코드 감사) — findByIdForUpdate로 락. 더블클릭 등으로 같은 예약에 대한
		// 취소 요청이 동시에 들어와도 한쪽만 통과하게 한다(재고 이중복구·PortOne 이중환불 방지).
		ReservationEntity reservation = reservationRepository.findByIdForUpdate(reservationId)
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

		// 5-1. 쿠폰을 썼던 거면 복구 (2026-09-07, 채채 확인) — 손님이 "물건을 샀다가 취소"한 일반적인
		// 경우라 복구 대상. 복구 안 하는 예외는 딱 하나, 손님 본인 노쇼(processNoShows())뿐이다.
		couponService.restore(reservation.getCouponId());

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
		// 추가됨 (2026-09-08, 코드 감사) — findByIdForUpdate로 락 (cancelReservation과 동일한 이유).
		ReservationEntity reservation = reservationRepository.findByIdForUpdate(reservationId)
				.orElseThrow(() -> new EntityNotFoundException("예약을 찾을 수 없습니다. id=" + reservationId));

		checkCancellableState(reservation);

		// 길이 제한(255자)이 걸린 ReservationEntity.cancelReason / PaymentCancelEntity.reason에
		// 그대로 저장하는 값이라, storeCancel.html 폼에 프론트 maxlength가 없는 걸 감안해 여기서
		// 한 번 더 잘라준다 (2026-09-08, 코드 감사) — 안 자르면 긴 사유 입력 시 취소 처리 자체가
		// 저장 단계에서 500으로 실패한다.
		String resolvedReason = truncateReason((reason == null || reason.isBlank()) ? "매장 사정으로 취소됨" : reason);

		// 결제가 이미 완료(paid)된 건이면 PortOne에 실제 환불을 요청한다 — 손님 잘못이 아니므로
		// (매장 취소는 재고 착오 등 매장 사정이 이유라서) 로컬 상태는 예외 없이 항상 "cancelled"로 바뀐다.
		markPaymentCancelled(reservationId, resolvedReason);

		reservation.setStatus("cancelled");
		reservation.setCancelledBy("STORE");
		reservation.setCancelReason(resolvedReason);
		ReservationEntity saved = reservationRepository.save(reservation);

		// 매장 귀책 취소 (2026-09-07, 채채 확인) — 손님 잘못이 전혀 없는 취소라 두 가지를 한다:
		//   1) 쓴 쿠폰이 있으면 복구
		//   2) 매장 책임이니 사과 성격의 보상 쿠폰을 새로 하나 지급
		couponService.restore(reservation.getCouponId());
		CouponEntity compensationCoupon = couponService.issueStoreCompensationCoupon(reservation.getUserId(), reservation.getId());
		notificationService.createNotification(reservation.getUserId(), NotificationEntity.TYPE_STORE_CANCEL_COUPON,
				"매장 사정으로 예약이 취소돼서 " + compensationCoupon.getDiscountRate() + "% 보상 쿠폰을 내 쿠폰함에 넣어드렸어요.",
				"/coupons", "store_cancel_coupon:" + reservation.getId());

		// 취소 안내 배너 + QR 제외 버전으로 영수증도 이 시점에 바로 다시 만들어둔다.
		receiptService.generateReceipt(reservationId);

		return saved;
	}

	/**
	 * 운영자(슈퍼어드민)가 신고 처리 중 예약을 취소한다 — 손님/매장 어느 쪽 귀책인지 딱 잘라 말하기
	 * 애매한 분쟁 조정 성격이라 cancelByStore와는 다르게 다룬다.
	 * 2026-09-07 신설 — 왜: 신고 상세 화면에 "바로 취소" 버튼을 붙여달라는 요청. 지금까지 슈퍼어드민은
	 * 신고에 텍스트 답변만 남길 뿐 실제 예약/결제를 손댈 방법이 없었다(신고 처리로 취소했다고 답변은
	 * 남기는데 실제 데이터는 안 바뀌는 문제).
	 * - 시간 제한 없음(cancelByStore와 동일) — 신고는 보통 정상 취소 창(주문 후 30분/마감 30분 전)이
	 *   이미 지난 뒤에 처리되므로 cancelReservation(손님용)의 시간 제한을 걸면 대부분 막힌다.
	 * - cancelledBy="ADMIN"으로 남긴다. "STORE"를 재사용하면 매장 귀책이 아닌 취소까지
	 *   countByStoreIdAndCancelledBy(storeId, "STORE") 기준 매장 신뢰도 통계에 섞여 들어간다.
	 * - 보상 쿠폰은 자동 지급하지 않는다 — 매장 귀책이 확정된 상황(cancelByStore)과 달리, 신고 처리는
	 *   경우에 따라 손님 귀책일 수도 있어서 보상 여부는 운영자가 답변에서 별도로 판단할 몫으로 남긴다.
	 * - 재고는 cancelByStore처럼 복구하지 않는다 — 분쟁 조정 취소는 재고 상태와 무관한 경우가 많고,
	 *   실제로 재고를 다시 팔 수 있는지는 매장이 재고 관리 화면에서 직접 판단해야 더 정확하다.
	 */
	@Transactional
	public ReservationEntity cancelByAdmin(Long reservationId, String reason) {
		// 추가됨 (2026-09-08, 코드 감사) — findByIdForUpdate로 락 (cancelReservation과 동일한 이유).
		ReservationEntity reservation = reservationRepository.findByIdForUpdate(reservationId)
				.orElseThrow(() -> new EntityNotFoundException("예약을 찾을 수 없습니다. id=" + reservationId));

		checkCancellableState(reservation);

		// truncateReason (2026-09-08, 코드 감사) — cancelByStore와 동일한 이유.
		String resolvedReason = truncateReason((reason == null || reason.isBlank()) ? "운영자 처리로 취소됨" : reason);

		markPaymentCancelled(reservationId, resolvedReason);

		reservation.setStatus("cancelled");
		reservation.setCancelledBy("ADMIN");
		reservation.setCancelReason(resolvedReason);
		ReservationEntity saved = reservationRepository.save(reservation);

		// 쓴 쿠폰이 있으면 복구 — 손님 본인 노쇼(processNoShows)를 제외한 모든 취소 경로와 동일한 정책.
		couponService.restore(reservation.getCouponId());

		receiptService.generateReceipt(reservationId);

		return saved;
	}

	/**
	 * 매장 취소 화면용 "취소 가능한 예약" 목록 — checkCancellableState와 동일한 기준(픽업/취소/노쇼가
	 * 아닌 예약)으로, 오래된 주문부터 보여준다. 픽업 코드를 직접 타이핑하지 않고 여기서 골라 취소한다.
	 * 변경됨 — 왜: storeId 조건이 없어서 전체 매장 예약이 다 섞여서 나오던 버그를 고쳤다.
	 */
	public List<CancellableReservationDto> getCancellableReservations(Long storeId) {
		List<ReservationEntity> cancellable =
				reservationRepository.findByStoreIdAndStatusInOrderByReservedAtAsc(storeId, CANCELLABLE_STATUSES);
		return cancellable.stream().map(this::toCancellableDto).toList();
	}

	private CancellableReservationDto toCancellableDto(ReservationEntity reservation) {
		StoreEntity store = storeRepository.findById(reservation.getStoreId())
				.orElseThrow(() -> new EntityNotFoundException("매장을 찾을 수 없습니다. id=" + reservation.getStoreId()));

		return new CancellableReservationDto(
				reservation.getId(),
				reservation.getPickupCode(),
				reservation.getProductName(),
				reservation.getReservedQuantity(),
				reservation.getTotalPrice(),
				store.getStoreName(),
				resolveStatusBadge(reservation)
		);
	}

	/**
	 * 추가됨 (2026-08-31, 챗봇 "예약 조회" tool용) — 왜: 챗봇이 "지금 취소할 수 있나요?" 같은 질문에
	 * 추측으로 답하지 않고, cancelReservation()이 실제로 쓰는 것과 완전히 같은 두 가지 조건(주문 후
	 * USER_CANCEL_WINDOW_MINUTES분 이내 / 매장 마감 CLOSING_CUTOFF_MINUTES분 전까지)으로 지금
	 * 시점 기준 취소 가능 여부만 조회한다. 실제로 취소를 처리하지는 않는다(읽기 전용) — 조회
	 * 결과에 따라 챗봇은 안내만 하고, 진짜 취소는 항상 마이페이지 버튼으로만 하게 한다.
	 *
	 * userId는 반드시 로그인 세션에서 가져온 값만 넘겨야 한다 — Gemini(모델)가 주는 값을 그대로
	 * 믿고 쓰면 다른 사람 예약을 조회할 수 있게 되므로, 이 메서드를 호출하는 ChatService가 그
	 * 책임을 진다(본인 예약만 조회).
	 */
	public List<net.dsa.girigiri.domain.dto.ReservationCancelStatusDto> getCancelEligibilityForUser(Long userId) {
		List<ReservationEntity> active = reservationRepository.findByUserIdAndStatusInOrderByReservedAtDesc(
				userId, CANCELLABLE_STATUSES);

		LocalDateTime now = LocalDateTime.now();

		return active.stream().map(reservation -> {
			StoreEntity store = storeRepository.findById(reservation.getStoreId()).orElse(null);
			String storeName = store != null ? store.getStoreName() : "-";

			boolean pastWindow = reservation.getReservedAt() != null
					&& Duration.between(reservation.getReservedAt(), now).toMinutes() >= USER_CANCEL_WINDOW_MINUTES;
			boolean closingSoon = store != null && isTooCloseToClosing(store, now);
			boolean eligible = !pastWindow && !closingSoon;

			String reason = null;
			if (pastWindow) {
				reason = "주문 후 " + USER_CANCEL_WINDOW_MINUTES + "분이 지나서 취소할 수 없어요.";
			} else if (closingSoon) {
				reason = "매장 마감 " + CLOSING_CUTOFF_MINUTES + "분 전이라 취소할 수 없어요.";
			}

			return new net.dsa.girigiri.domain.dto.ReservationCancelStatusDto(
					reservation.getId(),
					reservation.getProductName(),
					storeName,
					reservation.getStatus(),
					reservation.getReservedAt() != null ? reservation.getReservedAt().format(LIST_DISPLAY_FORMAT) : "-",
					eligible,
					reason
			);
		}).toList();
	}

	// 추가됨 (2026-09-08, 코드 감사) — QrCodeUtil.generatePickupCode()의 엔트로피를 늘려서
	// 충돌 확률을 크게 낮췄지만(QrCodeUtil 주석 참고) 0은 아니라서, 저장 전에 이미 쓰인 코드인지
	// 한 번 더 확인하고 충돌이면 재생성한다. DB의 unique 제약(ReservationEntity.pickupCode)이
	// 마지막 안전망이다.
	private static final int PICKUP_CODE_GENERATION_MAX_ATTEMPTS = 5;

	private String generateUniquePickupCode() {
		for (int attempt = 0; attempt < PICKUP_CODE_GENERATION_MAX_ATTEMPTS; attempt++) {
			String candidate = net.dsa.girigiri.util.QrCodeUtil.generatePickupCode();
			if (!reservationRepository.existsByPickupCode(candidate)) {
				return candidate;
			}
			log.warn("> [ReservationService] 픽업 코드 충돌 발생, 재생성합니다 - candidate={}, attempt={}", candidate, attempt + 1);
		}
		throw new IllegalStateException("픽업 코드 생성에 반복적으로 실패했어요. 잠시 후 다시 시도해주세요.");
	}

	// 추가됨 (2026-09-08, 코드 감사) — "취소 가능한 상태"를 checkCancellableState(예외 던지는 쪽)와
	// getCancellableReservations/getCancelEligibilityForUser(목록 조회 쪽)가 각자 따로 표현하고
	// 있었다(전자는 switch로 "안 되는 상태"를 나열, 후자는 List.of(...)로 "되는 상태"를 나열 —
	// 우연히 서로 반대쪽까지 일치했지만 한 군데를 고치면 다른 데를 빠뜨리기 쉬운 구조였다). 이제 이
	// 목록 하나만 진짜 기준으로 삼는다.
	private static final List<String> CANCELLABLE_STATUSES = List.of("pending", "confirmed", "ready");

	/** 픽업/취소/노쇼처럼 이미 끝난 예약을 또 취소하려는 걸 막는 공통 상태 체크. */
	private void checkCancellableState(ReservationEntity reservation) {
		String message = blockedCancelMessage(reservation);
		if (message != null) {
			throw new CancellationNotAllowedException(message);
		}
	}

	// 추가됨 (2026-09-08, 코드 감사) — "취소 가능 상태" 판정이 이 클래스 안(checkCancellableState)
	// 말고도 ReservationStoreController#storeCancelLookup, SuperAdminSupportController#blockedMessageFor
	// 에 각자 똑같은 switch로 따로 있었다(상태를 하나 추가하면 셋 중 하나만 고치고 나머지를 빠뜨리기
	// 쉬운 구조). 취소 가능하면 null, 아니면 이유 메시지를 돌려주는 공용 판정으로 모으고 그 두
	// 컨트롤러가 이 메서드를 부르도록 바꿨다.
	public String blockedCancelMessage(ReservationEntity reservation) {
		if (CANCELLABLE_STATUSES.contains(reservation.getStatus())) {
			return null;
		}
		return switch (reservation.getStatus()) {
			case "picked" -> "이미 픽업 완료된 예약은 취소할 수 없어요.";
			case "cancelled" -> "이미 취소된 예약이에요.";
			case "noshowed" -> "이미 노쇼 처리된 예약이라 취소할 수 없어요.";
			default -> "취소할 수 없는 상태의 예약이에요. (현재 상태: " + reservation.getStatus() + ")";
		};
	}

	// 추가됨 (2026-09-08, 코드 감사) — "픽업 가능 상태" 판정도 confirmPickup(여기)과
	// ReservationPickupController#pickupLookup 2곳에 따로 있었는데, "confirmed" 케이스 문구가
	// 이미 미묘하게 갈라져 있었다(서비스 쪽만 "예약 확인 화면에서 먼저 수락해주세요"가 붙어있었음)
	// — 취소 판정과 같은 패턴으로 여기 하나로 모은다. 픽업 가능(ready)하면 null, 아니면 이유 메시지.
	public String blockedPickupMessage(ReservationEntity reservation) {
		return switch (reservation.getStatus()) {
			case "picked" -> "이미 픽업 완료 처리된 예약이에요.";
			case "cancelled", "noshowed" -> "취소되었거나 노쇼 처리된 예약이라 픽업할 수 없어요.";
			case "pending" -> "아직 결제가 완료되지 않은 예약이에요.";
			case "confirmed" -> "아직 매장에서 확인(수락)하지 않은 예약이에요. 예약 확인 화면에서 먼저 수락해주세요.";
			default -> null;   // "ready" 상태만 정상 진행
		};
	}

	// 추가됨 (2026-09-08, 코드 감사) — cancelByStore/cancelByAdmin의 취소 사유 자유입력을
	// ReservationEntity.cancelReason / PaymentCancelEntity.reason(둘 다 varchar(255))에 그대로
	// 저장하는데, storeCancel.html 폼엔 길이 제한(maxlength)이 없다. 여기서 한 번 더 잘라서
	// 저장 단계 길이초과로 취소 처리 자체가 500 에러로 실패하는 걸 막는다.
	private static final int CANCEL_REASON_MAX_LENGTH = 255;

	private String truncateReason(String reason) {
		if (reason == null || reason.length() <= CANCEL_REASON_MAX_LENGTH) {
			return reason;
		}
		return reason.substring(0, CANCEL_REASON_MAX_LENGTH);
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
	 *
	 * 변경됨 (2026-08-25, 송보미 제안) — 왜: 예전엔 결제가 PAID였든 아니든 그냥 payStatus를
	 * "cancelled"로 덮어쓰기만 해서, 같은 결제를 두 번 취소 시도한 이력(예: 환불 실패 후 재시도)이
	 * 안 남았다. 이제 PAID였던 건은 취소 시도 1건마다 PaymentCancelEntity로 한 행씩 남긴다 — 아직
	 * 결제 전(READY/FAILED)인 건은 애초에 환불할 돈이 없으니 이력을 남길 필요 없이 cancel()만 부른다.
	 */
	private void markPaymentCancelled(Long reservationId, String reason) {
		paymentRepository.findByReservationId(reservationId).ifPresent(payment -> {
			if (payment.getPayStatus() == PayStatus.PAID) {
				PortOneClient.PortOneCancelResult result = portOneClient.cancelPayment(payment.getMerchantUid(), reason);
				boolean refundSucceeded = result.cancelled();

				if (refundSucceeded) {
					payment.applyCancel(reason);
				} else {
					payment.applyCancel("환불 실패(수동 확인 필요): " + result.failReason());
					log.warn("> [ReservationService] PortOne 환불 실패 - reservationId={}, merchantUid={}, 사유={}",
							reservationId, payment.getMerchantUid(), result.failReason());
				}

				paymentCancelRepository.save(
						PaymentCancelEntity.of(payment.getId(), payment.getAmount(), reason, refundSucceeded));
			} else {
				payment.cancel(reason);
			}

			paymentRepository.save(payment);
		});
	}

	/**
	 * 매장 신뢰도(취소율) 통계: 전체 예약 중 "매장 사정으로" 취소된 비율. 손님 취소는 매장 잘못이 아니라서 뺀다.
	 *
	 * 변경됨 (2026-09-08, 코드 감사) — 왜: 분모(total)를 countByStoreId로 구하면 결제까지 안 가고
	 * 포기한(pending) 예약까지 다 세어버려서, 트래픽만 많고 결제 전환이 낮은 매장일수록 분모가
	 * 부풀어 취소율이 실제보다 좋게(희석되어) 나온다. "결제까지 갔던"(pending 제외) 예약만 분모로
	 * 삼도록 바꿨다.
	 */
	public StoreCancelStatsDto getStoreCancelStats(Long storeId) {
		long total = reservationRepository.countByStoreIdAndStatusNot(storeId, "pending");
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
	// 변경됨 (2026-09-08, 코드 감사) — 원래는 이 메서드 전체가 하나의 @Transactional이라, 후보 예약
	// 여러 건을 처리하는 도중 하나가 실패하면(예: receiptService.generateReceipt가 PDF 생성/업로드
	// 오류로 예외를 던지면) 이미 노쇼 처리됐어야 할 다른 항목들까지 전부 롤백됐다 — 조용히, 로그도 없이.
	// expireStalePendingReservations와 같은 패턴(항목별 REQUIRES_NEW + try/catch)으로 바꿔서, 하나가
	// 실패해도 나머지는 그대로 진행되게 한다.
	public int processNoShows() {
		LocalDateTime now = LocalDateTime.now();
		// 변경됨 (2026-09-08, 코드 감사) — findByStatusIn(confirmed, ready) 전체를 끌고 온 다음
		// isPastPickupDeadline(자바)으로 거르던 걸 DB 쿼리로 민다. "주문일 다음날 자정이 지남"은
		// 수학적으로 "reservedAt < 오늘 자정"과 동치라(reservedAt이 오늘이면 아직 아니고, 어제
		// 이전이면 이미 지남) 그대로 파생 쿼리 조건으로 옮길 수 있다 — processOneNoShow 안에서
		// findByIdForUpdate로 다시 잠그고 isPastPickupDeadline으로 한 번 더 정확히 재확인하니
		// 여기 DB 필터는 후보를 좁히는 용도일 뿐, 최종 판단은 여전히 그 정밀한 로직이 한다.
		LocalDateTime todayStart = now.toLocalDate().atStartOfDay();
		List<Long> candidateIds = reservationRepository
				.findByStatusInAndReservedAtBefore(List.of("confirmed", "ready"), todayStart).stream()
				.map(ReservationEntity::getId)
				.toList();

		int noShowCount = 0;
		for (Long id : candidateIds) {
			try {
				if (processOneNoShow(id, now)) {
					noShowCount++;
				}
			} catch (Exception e) {
				log.error("> [ReservationService] 노쇼 자동 처리 중 오류 (건너뛰고 계속 진행) - reservationId={}", id, e);
			}
		}
		return noShowCount;
	}

	/**
	 * processNoShows의 항목 1건 처리 — 독립된 트랜잭션(REQUIRES_NEW)으로 상태를 "noshowed"로 바꾼다.
	 * 다른 트랜잭션(픽업 확인/취소)이 후보 목록을 뽑은 뒤 여기 오기까지 먼저 상태를 바꿔놨을 수 있어
	 * findByIdForUpdate로 다시 잠그고 재확인한다 — 이미 픽업/취소된 예약을 덮어쓰지 않기 위해서다.
	 * 영수증 재생성은 노쇼 상태 전환 자체와는 별개 관심사라 상태 전환 트랜잭션이 커밋된 뒤에,
	 * 그리고 이 메서드를 감싼 processNoShows()의 try/catch 범위 안에서 실행한다 — 영수증 생성이
	 * 실패해도 "노쇼 처리됨"이라는 이미 커밋된 사실은 롤백되지 않는다.
	 */
	private boolean processOneNoShow(Long reservationId, LocalDateTime now) {
		TransactionTemplate requiresNew = new TransactionTemplate(transactionManager);
		requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		boolean noShowed = Boolean.TRUE.equals(requiresNew.execute(status -> {
			ReservationEntity reservation = reservationRepository.findByIdForUpdate(reservationId).orElse(null);
			if (reservation == null
					|| !("confirmed".equals(reservation.getStatus()) || "ready".equals(reservation.getStatus()))
					|| !isPastPickupDeadline(reservation, now)) {
				return false;
			}

			reservation.setStatus("noshowed");
			// (2026-09-07, 채채 확인) — 여기서는 의도적으로 couponService.restore()를 호출하지 않는다.
			// 손님이 안 나타난 건 본인 잘못이라, 썼던 쿠폰이 있어도 복구해주지 않기로 했다 — 다른
			// 취소 경로(위 cancelReservation/cancelByStore/결제실패 등)와 유일하게 다른 부분이다.
			reservationRepository.save(reservation);
			return true;
		}));

		if (noShowed) {
			// 노쇼 안내 배너 + QR 제외 버전으로, 상태가 바뀐 이 시점에 영수증도 바로 다시 만들어둔다.
			receiptService.generateReceipt(reservationId);
		}
		return noShowed;
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
		StoreEntity store = storeRepository.findById(reservation.getStoreId())
				.orElseThrow(() -> new EntityNotFoundException("매장을 찾을 수 없습니다. id=" + reservation.getStoreId()));

		return new ReservationListItemDto(
				reservation.getId(),
				store.getStoreName(),
				reservation.getProductName(),
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
	// 검토됨 (2026-09-08, 코드 감사) — SettlementService#statusLabel도 같은 예약 상태를 한글로
	// 바꾸는 매핑이라 감사에서 "라벨이 화면마다 갈린다(예: confirmed가 여긴 '주문 확인중', 정산
	// 쪽은 '수락 대기')"고 지적됐다. 실제로 확인해보니 대상 독자가 달라서 의도적인 차이다 —
	// 여긴 손님이 보는 마이페이지 배지(간결한 진행상황), 정산 쪽은 매장 정산 리포트라 "노쇼
	// (환불 없음)"처럼 돈 흐름을 같이 알려줘야 한다. 그래서 텍스트를 억지로 통일하진 않았고,
	// 대신 상태를 하나 추가할 때 두 곳 다 챙기라는 상호 참조만 남긴다.
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

	// ── 2026-09-03 추가 (레이어 규칙 2단계) ──────────────────────────────────
	// ReservationController에 남아있던 나머지 Repository 직접 호출(findByPickupCode 조회,
	// 결제 기록 조회, 픽업 설정 저장)을 옮겨온다. 위쪽 예약 상태 전이 로직과 달리 여기서부터는
	// 단순 조회/설정 저장 헬퍼다.

	@Transactional(readOnly = true)
	public Optional<ReservationEntity> findByPickupCode(String pickupCode) {
		return reservationRepository.findByPickupCode(pickupCode);
	}

	// 추가됨 (2026-09-08) — 신고 상세 화면이 ComplaintEntity.targetReservationId로 예약을 바로
	// 찾아 보여줄 때 쓴다. 삭제된 적 없는 값이라 사실상 항상 있지만, 혹시 몰라 Optional로 둔다.
	@Transactional(readOnly = true)
	public Optional<ReservationEntity> findById(Long id) {
		return reservationRepository.findById(id);
	}

	@Transactional(readOnly = true)
	public Optional<StoreEntity> findStoreById(Long storeId) {
		return storeRepository.findById(storeId);
	}

	@Transactional(readOnly = true)
	public PaymentEntity getPaymentByReservationId(Long reservationId) {
		return paymentRepository.findByReservationId(reservationId)
				.orElseThrow(() -> new EntityNotFoundException("결제 기록을 찾을 수 없습니다. reservationId=" + reservationId));
	}

	/**
	 * 매장의 "준비 시간"/"마지막 픽업 시간" 설정 저장. pickupTimeMode: "manual"(직접 입력한
	 * lastPickupTime 사용) / "close"(영업 종료 시간을 매번 다시 계산해서 사용) / "unlimited"(제한 없음, NULL 저장).
	 */
	@Transactional
	public void updatePickupSettings(StoreEntity store, int prepTimeMinutes, String lastPickupTime, String pickupTimeMode) {
		// 방어적으로 최소값 보정 (0/음수/공란 입력 방지) — 준비시간이 0 이하면 픽업 가능 시각 계산이 의미없어진다.
		store.setPrepTimeMinutes(Math.max(prepTimeMinutes, 1));

		switch (pickupTimeMode) {
			case "unlimited" -> store.setLastPickupTime(null);
			// operatingHours 파싱 실패하면(예: 그 사이 매장이 영업시간을 이상한 형식으로 바꿨다면) 조용히
			// 제한없음(null)으로 저장한다 — 화면에서 이 옵션은 파싱 성공했을 때만 보이므로 흔한 경우는 아니다.
			case "close" -> store.setLastPickupTime(parseClosingTimeOrNull(store.getOperatingHours()));
			default -> store.setLastPickupTime(
					(lastPickupTime == null || lastPickupTime.isBlank()) ? null : LocalTime.parse(lastPickupTime));
		}

		storeRepository.save(store);
	}

	private LocalTime parseClosingTimeOrNull(String operatingHours) {
		try {
			return OperatingHoursUtil.parseClosingTime(operatingHours);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}

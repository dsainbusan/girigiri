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
import net.dsa.girigiri.exception.PickupNotAllowedException;
import net.dsa.girigiri.repository.PaymentRepository;
import net.dsa.girigiri.repository.ProductRepository;
import net.dsa.girigiri.repository.ReservationRepository;
import net.dsa.girigiri.repository.StoreRepository;
import net.dsa.girigiri.util.OperatingHoursUtil;
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
 * 주의2: PortOne 실제 결제 연동은 아직 안 된 상태라, 지금은 "결제가 바로 성공했다"고
 *      가정하고 진행한다. 나중에 실제 결제 연동 시에는 이 메서드를 두 단계로 쪼개서
 *      (1) 예약을 pending으로 먼저 만들고 → (2) PortOne 결제 성공 콜백이 왔을 때
 *      confirmed로 바꾸는 식으로 수정하면 된다.
 */
@Service
@RequiredArgsConstructor
public class ReservationService {

	private static final DateTimeFormatter LIST_DISPLAY_FORMAT = DateTimeFormatter.ofPattern("MM월 dd일 HH:mm");

	// 마이페이지 탭 이름 -> 실제 DB status 값 매핑. ReservationEntity의 status 필드 주석과 동일한 규칙이다.
	public static final String TAB_PROGRESS = "progress";     // 진행중 (예약완료/픽업대기 통합)
	public static final String TAB_PICKED = "picked";         // 픽업완료
	public static final String TAB_CANCELLED = "cancelled";   // 노쇼·취소 통합

	// 손님 취소 허용 창: 주문 후 이 시간 이내, 그리고 매장 마감 이 시간 전까지만 취소 가능 (둘 다 만족해야 함)
	private static final int USER_CANCEL_WINDOW_MINUTES = 30;
	private static final int CLOSING_CUTOFF_MINUTES = 30;

	private final StockService stockService;
	private final ProductRepository productRepository;
	private final StoreRepository storeRepository;
	private final ReservationRepository reservationRepository;
	private final PaymentRepository paymentRepository;
	private final ReceiptService receiptService;

	@Transactional
	public ReservationEntity createReservation(Long userId, Long productId, int quantity, LocalDateTime pickupTime) {

		// 1. 재고 차감. 재고가 없으면 여기서 OutOfStockException이 터지면서 아래 코드는 실행되지 않는다.
		//    (동시에 여러 명이 예약해도 안전하게 처리되는 부분은 StockService가 이미 책임진다.)
		stockService.decreaseStock(productId, quantity);

		// 2. 가격 계산을 위해 상품 정보 조회
		ProductEntity product = productRepository.findById(productId)
				.orElseThrow(() -> new EntityNotFoundException("상품을 찾을 수 없습니다. id=" + productId));

		int totalPrice = product.getDiscountedPrice() * quantity;

		// 3. 픽업 확인용 QR 코드 문자열 생성
		String pickupCode = net.dsa.girigiri.util.QrCodeUtil.generatePickupCode();

		// 4. 예약 레코드 저장 (결제 연동 전이므로 바로 confirmed로 저장)
		ReservationEntity reservation = ReservationEntity.builder()
				.userId(userId)
				.productId(productId)
				.storeId(product.getStoreId())
				.reservedQuantity(quantity)
				.totalPrice(totalPrice)
				.pickupTime(pickupTime)
				.pickupCode(pickupCode)
				.status("confirmed")
				.build();
		reservation = reservationRepository.save(reservation);

		// 5. 결제 기록 저장 (나중에 진짜 PortOne 콜백 데이터로 채워질 부분을 지금은 흉내만 낸다)
		PaymentEntity payment = PaymentEntity.builder()
				.reservationId(reservation.getId())
				.merchantUid("MID-" + reservation.getId() + "-" + System.currentTimeMillis())
				.amount(totalPrice)
				.payStatus("paid")
				.paidAt(LocalDateTime.now())
				.build();
		paymentRepository.save(payment);

		// 6. 결제가 성공했으니(지금은 가정) 영수증 PDF도 바로 만들어서 저장해둔다.
		receiptService.generateReceipt(reservation.getId());

		return reservation;
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

		// 4. 결제 기록이 있으면 "cancelled"로 표시해둔다 (실제 PortOne 환불 연동 전까지는 상태만 남긴다)
		markPaymentCancelled(reservationId);

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

		// 결제는 무조건, 즉시 취소(환불) 처리 — 손님 잘못이 아니므로 예외 없이 보장한다.
		markPaymentCancelled(reservationId);

		reservation.setStatus("cancelled");
		reservation.setCancelledBy("STORE");
		reservation.setCancelReason(reason == null || reason.isBlank() ? "매장 사정으로 취소됨" : reason);
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

	/** 매장 영업종료시간까지 30분 이내로 남았거나 이미 지났으면 true. */
	private boolean isTooCloseToClosing(StoreEntity store, LocalDateTime now) {
		LocalTime closingTime = OperatingHoursUtil.parseClosingTime(store.getOperatingHours());
		LocalDateTime closingDateTime = LocalDateTime.of(LocalDate.now(), closingTime);
		return closingDateTime.isBefore(now.plusMinutes(CLOSING_CUTOFF_MINUTES));
	}

	private void markPaymentCancelled(Long reservationId) {
		paymentRepository.findByReservationId(reservationId).ifPresent(payment -> {
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
	 * 픽업 마감시간이 지났는데도 아직 픽업 안 된(confirmed/ready 상태) 예약들을 전부 "noshowed"로 바꾼다.
	 * NoShowScheduler가 주기적으로 이 메서드를 호출한다.
	 *
	 * 변경됨 (2026-08-21) — 왜: 픽업 시간을 손님이 고르지 않고 자동 계산(현재+준비시간)하는 구조로
	 * 바뀌면서, reservation.pickupTime은 이제 "가장 빠른 픽업 가능 시각"일 뿐 마감 기준이 아니다.
	 * 진짜 마감 기준은 매장의 lastPickupTime(마지막 픽업시간)이라서, 매장이 그 값을 설정해뒀으면
	 * 그 기준으로 판단한다. 아직 매장 설정 화면이 없어서 값이 비어있는 매장(대부분의 기존 데이터)은
	 * 예전처럼 reservation.pickupTime 기준으로 판단(하위 호환)한다.
	 *
	 * 손님 취소/매장 취소와 다르게, 노쇼는:
	 *   - 재고를 복구하지 않는다 (노쇼가 감지되는 시점 자체가 이미 마감 임박/직후라, 다시 팔 시간이 없다고 봄)
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

	/** 매장의 lastPickupTime이 설정돼 있으면 그 기준으로, 아니면 예전처럼 reservation.pickupTime 기준으로 마감 여부 판단. */
	private boolean isPastPickupDeadline(ReservationEntity reservation, LocalDateTime now) {
		StoreEntity store = storeRepository.findById(reservation.getStoreId()).orElse(null);

		if (store != null && store.getLastPickupTime() != null && reservation.getReservedAt() != null) {
			LocalDateTime lastPickupDateTime =
					LocalDateTime.of(reservation.getReservedAt().toLocalDate(), store.getLastPickupTime());
			return now.isAfter(lastPickupDateTime);
		}

		return reservation.getPickupTime() != null && reservation.getPickupTime().isBefore(now);
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

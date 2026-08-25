package net.dsa.girigiri.controller;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.CancellableReservationDto;
import net.dsa.girigiri.domain.dto.PaymentConfirmResponseDto;
import net.dsa.girigiri.domain.dto.PickupBatchItemResultDto;
import net.dsa.girigiri.domain.dto.PickupLookupResponseDto;
import net.dsa.girigiri.domain.dto.ReservationIncomingItemDto;
import net.dsa.girigiri.domain.dto.ReservationListItemDto;
import net.dsa.girigiri.domain.dto.ReservationPrepareResponseDto;
import net.dsa.girigiri.domain.entity.PaymentEntity;
import net.dsa.girigiri.domain.entity.ProductEntity;
import net.dsa.girigiri.domain.entity.ReservationEntity;
import net.dsa.girigiri.domain.entity.ReceiptEntity;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.exception.AcceptNotAllowedException;
import net.dsa.girigiri.exception.CancellationNotAllowedException;
import net.dsa.girigiri.exception.OrderNotAllowedException;
import net.dsa.girigiri.exception.PaymentVerificationException;
import net.dsa.girigiri.exception.PickupNotAllowedException;
import net.dsa.girigiri.repository.PaymentRepository;
import net.dsa.girigiri.repository.ProductRepository;
import net.dsa.girigiri.repository.ReceiptRepository;
import net.dsa.girigiri.repository.ReservationRepository;
import net.dsa.girigiri.repository.StoreRepository;
import net.dsa.girigiri.service.ReceiptService;
import net.dsa.girigiri.service.ReservationService;
import net.dsa.girigiri.util.OperatingHoursUtil;
import net.dsa.girigiri.util.PickupAvailabilityUtil;
import net.dsa.girigiri.util.PortOneClient;
import net.dsa.girigiri.util.QrCodeUtil;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 예약 확인 -> 예약 생성 -> 완료 화면(QR/영수증)까지 담당하는 컨트롤러.
 * ReservationService/ReceiptService(이미 만들어서 테스트해둔 부품)를 실제 화면 버튼과 연결하는 역할만 한다.
 *
 * 주의: 로그인이 아직 없어서, 지금은 임시로 sample-data.sql의 user id=1을 "로그인한 사용자"로
 *      취급한다 (resolveCurrentUserId() 참고). 나중에 로그인/세션이 만들어지면 이 메서드 안쪽만
 *      세션에서 실제 userId를 꺼내오도록 바꾸면 되고, 그 아래 로직(체크아웃/완료 화면)은 안 바뀐다.
 *
 * 주의2 (2026-08-21 변경): "당일 판매·당일 픽업" 컨셉에 맞춰, 픽업 시간을 손님이 직접 고르지 않고
 *      "현재시간 + 매장 준비시간"으로 자동 계산한다(PickupAvailabilityUtil). 매장의 마지막
 *      픽업시간을 넘겼으면 주문 자체를 막는다. 이 검증은 여기 컨트롤러 레벨에서만 하고
 *      ReservationService.prepareReservation 자체는 그대로 둔다 — 테스트에서 시간대와 무관하게
 *      결정적으로 동작해야 하는데, 서비스 안에 넣으면 실행 시각에 따라 테스트가 흔들릴 수 있어서다.
 */
@Controller
@RequestMapping("/reservation")
@RequiredArgsConstructor
public class ReservationController {

	private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("MM월 dd일 HH:mm");

	// 매장이 아직 prepTimeMinutes를 설정 안 했을 때 쓰는 기본 준비시간 (StoreEntity 기본값과 동일하게 맞춤)
	private static final int DEFAULT_PREP_TIME_MINUTES = 20;

	private final ReservationService reservationService;
	private final ReceiptService receiptService;
	private final ReservationRepository reservationRepository;
	private final ProductRepository productRepository;
	private final StoreRepository storeRepository;
	private final ReceiptRepository receiptRepository;
	private final PaymentRepository paymentRepository;
	private final PortOneClient portOneClient;

	// TODO(송보미 로그인 완료 후): 세션에서 실제 로그인한 userId를 꺼내오도록 교체
	private Long resolveCurrentUserId() {
		return 1L;
	}

	// 변경됨 — 왜: 매장별로 필터링하는 화면(완료된 거래 내역 등)에서 하드코딩된 store id=1이 실제
	// 로그인한 점주의 매장(예: id=2)과 안 맞아서 데이터가 안 보이는 문제가 있었다. StoreController가
	// 이미 쓰는 패턴(session.userId → storeRepository.findByOwnerId)과 동일하게 세션 기반으로 바꿨다.
	private Long resolveCurrentStoreId(HttpSession session) {
		Long userId = (Long) session.getAttribute("userId");
		return storeRepository.findByOwnerId(userId)
				.orElseThrow(() -> new EntityNotFoundException("로그인한 점주의 매장을 찾을 수 없습니다."))
				.getId();
	}

	/**
	 * 예약하기 버튼을 누르면 오는 확인 화면: 상품 정보 + 결제 예정 금액을 보여준다.
	 * (2026-08-21 변경) 픽업 시간대를 손님이 고르는 화면이 아니라, 매장 설정(준비시간/마지막
	 * 픽업시간) 기준으로 "지금 주문하면 픽업 가능한지"와 "예상 픽업 가능 시각"을 자동 계산해서 보여준다.
	 */
	@GetMapping("/checkout")
	public String checkout(@RequestParam Long productId,
							@RequestParam(defaultValue = "1") int quantity,
							Model model) {
		ProductEntity product = productRepository.findById(productId)
				.orElseThrow(() -> new EntityNotFoundException("상품을 찾을 수 없습니다. id=" + productId));
		StoreEntity store = storeRepository.findById(product.getStoreId())
				.orElseThrow(() -> new EntityNotFoundException("매장을 찾을 수 없습니다. id=" + product.getStoreId()));

		LocalDateTime now = LocalDateTime.now();
		int prepTimeMinutes = store.getPrepTimeMinutes() != null ? store.getPrepTimeMinutes() : DEFAULT_PREP_TIME_MINUTES;
		boolean canOrder = PickupAvailabilityUtil.canOrderNow(now, store.getLastPickupTime(), prepTimeMinutes);
		LocalDateTime earliestPickupTime = PickupAvailabilityUtil.earliestPickupTime(now, prepTimeMinutes);

		// 추가됨 (2026-08-21) — 왜: 남은 수량이 0개(품절)인데도 수량 스테퍼와 "예약 확정하기" 버튼이
		// 눌리는 문제가 있었다. 여기서 품절 여부를 미리 계산해서 화면에서 아예 막고, 요청으로 들어온
		// quantity가 남은 수량보다 많거나 잘못된 값이어도(URL 직접 조작 등) 항상 1~remaining 사이로
		// 보정해서 결제 예정 금액이 실제와 다르게 보이지 않게 한다.
		int remainingQuantity = product.getRemainingQuantity();
		boolean soldOut = remainingQuantity <= 0;
		int safeQuantity = soldOut ? 0 : Math.max(1, Math.min(quantity, remainingQuantity));

		model.addAttribute("productId", product.getId());
		model.addAttribute("name", product.getName());
		model.addAttribute("originalPrice", product.getOriginalPrice());
		model.addAttribute("discountedPrice", product.getDiscountedPrice());
		model.addAttribute("remainingQuantity", remainingQuantity);
		model.addAttribute("quantity", safeQuantity);
		model.addAttribute("totalPrice", product.getDiscountedPrice() * safeQuantity);
		model.addAttribute("canOrder", canOrder);
		model.addAttribute("soldOut", soldOut);
		model.addAttribute("earliestPickupDisplay", earliestPickupTime.format(DISPLAY_FORMAT));
		// 추가됨 (2026-08-24) — 왜: "결제 후 30분 이내에만 취소할 수 있으니 신중하게 결제하라"는 안내를
		// 예약 확정 버튼 누르기 전에 보여주고 싶다는 요청. ReservationService.cancelReservation이 실제로
		// 쓰는 값(USER_CANCEL_WINDOW_MINUTES)을 그대로 가져다 써서, 나중에 정책이 바뀌어도 화면 문구가
		// 따로 안 맞을 일이 없게 했다.
		model.addAttribute("cancelWindowMinutes", ReservationService.USER_CANCEL_WINDOW_MINUTES);

		// 추가됨 (2026-08-24, PortOne 연동) — 왜: 결제창(PortOne 브라우저 SDK)을 프론트에서 띄우려면
		// storeId/channelKey가 필요한데, 이 값들은 브라우저에 노출돼도 되는 "공개" 설정값이라(진짜
		// 비밀값인 apiSecret은 서버(PortOneClient.verifyPayment)에서만 쓰고 여기 안 넘긴다) 그냥
		// 모델에 실어 화면으로 보낸다. 팀에 아직 PortOne 테스트 계정이 없어서(portOneConfigured=false)
		// 지금은 결제창 대신 "결제 준비중" 안내만 뜬다 — 계정 생기고 .env만 채우면 자동으로 실제
		// 결제창이 뜨게 된다(코드 수정 불필요).
		model.addAttribute("portOneConfigured", portOneClient.isConfigured());
		model.addAttribute("portOneStoreId", portOneClient.getStoreId());
		model.addAttribute("portOneChannelKey", portOneClient.getChannelKey());

		return "reservationView/checkout";
	}

	/**
	 * "예약 확정하기" 팝업에서 "확인했어요"를 누르면 결제창을 띄우기 직전에 호출되는 API(AJAX).
	 * 재고 차감 + 예약을 pending으로 저장 + PortOne에 넘길 paymentId 발급까지 여기서 처리하고,
	 * 그 값을 프론트로 돌려주면 프론트가 PortOne.requestPayment()를 호출한다.
	 *
	 * (2026-08-21에 있던 pickup 시간 재계산 로직은 그대로 유지 — 체크아웃 화면을 열어본 뒤 시간이
	 *  좀 지나 실제 제출하는 사이에 마감시간을 넘겨버렸을 수도 있어서, 진짜로 예약을 만들기
	 *  직전에 다시 확인한다.)
	 */
	@PostMapping("/prepare")
	@ResponseBody
	public ReservationPrepareResponseDto prepare(@RequestParam Long productId,
												  @RequestParam(defaultValue = "1") int quantity) {
		ProductEntity product = productRepository.findById(productId)
				.orElseThrow(() -> new EntityNotFoundException("상품을 찾을 수 없습니다. id=" + productId));
		StoreEntity store = storeRepository.findById(product.getStoreId())
				.orElseThrow(() -> new EntityNotFoundException("매장을 찾을 수 없습니다. id=" + product.getStoreId()));

		LocalDateTime now = LocalDateTime.now();
		int prepTimeMinutes = store.getPrepTimeMinutes() != null ? store.getPrepTimeMinutes() : DEFAULT_PREP_TIME_MINUTES;

		if (!PickupAvailabilityUtil.canOrderNow(now, store.getLastPickupTime(), prepTimeMinutes)) {
			throw new OrderNotAllowedException("죄송해요, 오늘 주문 가능한 시간이 지났어요. 내일 다시 확인해주세요.");
		}

		LocalDateTime pickupTime = PickupAvailabilityUtil.earliestPickupTime(now, prepTimeMinutes);

		ReservationEntity reservation = reservationService.prepareReservation(
				resolveCurrentUserId(), productId, quantity, pickupTime);

		PaymentEntity payment = paymentRepository.findByReservationId(reservation.getId())
				.orElseThrow(() -> new EntityNotFoundException("결제 기록을 찾을 수 없습니다. reservationId=" + reservation.getId()));

		return new ReservationPrepareResponseDto(
				reservation.getId(), payment.getMerchantUid(), reservation.getTotalPrice(), product.getName());
	}

	/**
	 * 결제창(PortOne 브라우저 SDK)이 끝난 뒤, 프론트가 이 API로 paymentId를 넘기면 서버가 PortOne에
	 * 직접 재조회해서 진짜 결제가 됐는지 확인한다(ReservationService.confirmPayment). 검증에 성공하면
	 * 예약 완료 화면 주소를 돌려주고, 프론트는 그 주소로 이동만 하면 된다.
	 *
	 * AJAX 호출이라 실패 시에도 에러 페이지로 안 넘기고, 항상 200 + success:false로 응답해서
	 * 프론트가 같은 화면에서 실패 메시지를 보여줄 수 있게 한다.
	 */
	@PostMapping("/{id}/confirm-payment")
	@ResponseBody
	public PaymentConfirmResponseDto confirmPayment(@PathVariable Long id, @RequestParam String paymentId) {
		try {
			ReservationEntity reservation = reservationService.confirmPayment(id, paymentId);
			return PaymentConfirmResponseDto.success("/reservation/" + reservation.getId() + "/complete");
		} catch (PaymentVerificationException | EntityNotFoundException e) {
			return PaymentConfirmResponseDto.failure(e.getMessage());
		} catch (IllegalStateException e) {
			// PortOneClient.verifyPayment()가 .env 미설정 상태에서 호출된 경우(정상 흐름에선 checkout.html이
			// portOneConfigured를 먼저 확인해서 여기까지 안 오지만, 방어적으로 한 번 더 막아둔다).
			return PaymentConfirmResponseDto.failure("결제 시스템이 아직 준비되지 않았어요.");
		}
	}

	/**
	 * 결제창(PortOne)이 실패했거나 손님이 그냥 닫아버렸을 때, checkout.html의 startPayment()가 바로
	 * 호출하는 API. pending 예약을 즉시 취소하고 재고를 바로 복구한다
	 * (ReservationService.cancelPendingReservation 참고).
	 *
	 * 추가됨 (2026-08-24) — 왜: 이 호출이 없으면 pending 예약은 최대 PENDING_EXPIRY_MINUTES(15분) +
	 * 스케줄러 주기(5분)만큼 지나야 재고가 풀렸다 — "결제 안 했는데 남은 수량이 그대로/안 늘어난다"는
	 * 피드백을 받고 추가했다. 프론트 fetch가 실패해도(네트워크 문제 등) 화면 자체는 막지 않도록
	 * body 없이 204만 내려준다 — 최악의 경우엔 기존 스케줄러가 안전망으로 정리해준다.
	 */
	@PostMapping("/{id}/cancel-pending")
	@ResponseBody
	public ResponseEntity<Void> cancelPending(@PathVariable Long id) {
		reservationService.cancelPendingReservation(id);
		return ResponseEntity.noContent().build();
	}

	/** 예약 완료 화면: QR 코드 + 픽업 코드 + 영수증 링크를 보여준다. */
	@GetMapping("/{id}/complete")
	public String complete(@PathVariable Long id, Model model) {
		ReservationEntity reservation = reservationRepository.findById(id)
				.orElseThrow(() -> new EntityNotFoundException("예약을 찾을 수 없습니다. id=" + id));
		StoreEntity store = storeRepository.findById(reservation.getStoreId())
				.orElseThrow(() -> new EntityNotFoundException("매장을 찾을 수 없습니다. id=" + reservation.getStoreId()));

		model.addAttribute("reservationId", reservation.getId());
		model.addAttribute("storeName", store.getStoreName());
		model.addAttribute("productName", reservation.getProductName());
		model.addAttribute("quantity", reservation.getReservedQuantity());
		model.addAttribute("totalPrice", reservation.getTotalPrice());
		model.addAttribute("pickupTime", reservation.getPickupTime().format(DISPLAY_FORMAT));
		model.addAttribute("pickupCode", reservation.getPickupCode());

		return "reservationView/complete";
	}

	/** 완료 화면의 <img> 태그가 실제로 부르는 QR 이미지 바이트. */
	@GetMapping("/{id}/qr-image")
	@ResponseBody
	public ResponseEntity<byte[]> qrImage(@PathVariable Long id) {
		ReservationEntity reservation = reservationRepository.findById(id)
				.orElseThrow(() -> new EntityNotFoundException("예약을 찾을 수 없습니다. id=" + id));
		try {
			byte[] png = QrCodeUtil.generateQrImage(reservation.getPickupCode(), 240);
			return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(png);
		} catch (Exception e) {
			throw new IllegalStateException("QR 이미지 생성에 실패했습니다. reservationId=" + id, e);
		}
	}

	/** 마이페이지 예약 목록: 진행중/픽업완료/노쇼·취소 3탭. */
	@GetMapping("/my")
	public String myReservations(@RequestParam(defaultValue = ReservationService.TAB_PROGRESS) String tab,
								  Model model) {
		List<ReservationListItemDto> reservations =
				reservationService.getMyReservations(resolveCurrentUserId(), tab);

		model.addAttribute("reservations", reservations);
		model.addAttribute("activeTab", tab);

		return "reservationView/myReservations";
	}

	/**
	 * "예약 취소" 버튼: 재고 복구 + 결제 취소 표시 + 상태 변경까지 ReservationService.cancelReservation 하나로 처리.
	 * 취소 눌러도 지금까지는 화면이 그냥 목록으로 넘어가기만 해서 "진짜 환불되는 건지" 알 수가 없었다 —
	 * flash 메시지로 "환불됩니다" 안내를 목록 화면 맨 위에 한 번 보여준다 (새로고침하면 사라짐).
	 */
	@PostMapping("/{id}/cancel")
	public String cancel(@PathVariable Long id, RedirectAttributes redirectAttributes) {
		reservationService.cancelReservation(id);
		redirectAttributes.addFlashAttribute("cancelledMessage", "예약이 취소됐어요. 결제하신 금액은 환불됩니다.");
		return "redirect:/reservation/my?tab=cancelled";
	}

	/**
	 * 사장님이 재고 착오 등으로 예약을 취소해야 할 때 쓰는 화면.
	 * (2026-08-21 변경) 픽업 코드를 직접 타이핑하는 대신, 지금 취소 가능한 예약 목록에서 골라
	 * 취소하도록 바꿨다 — 코드를 손으로 옮겨 적다 오타가 나거나, 이미 지나간 예약 번호를 잘못
	 * 입력하는 걸 막을 수 있다.
	 */
	@GetMapping("/store-cancel")
	public String storeCancelForm(HttpSession session, Model model) {
		List<CancellableReservationDto> cancellable = reservationService.getCancellableReservations(resolveCurrentStoreId(session));
		model.addAttribute("cancellable", cancellable);
		return "reservationView/storeCancel";
	}

	/**
	 * 매장 취소 화면에서, 픽업 코드를 입력하는 동안 어떤 예약(상품/수량/매장)을 취소하려는 건지
	 * 미리 보여주는 조회 전용 API. pickupLookup과 비슷하지만 "취소 가능 상태" 기준이 다르다 —
	 * 픽업은 ready 상태만 가능하지만, 매장 취소는 ReservationService.checkCancellableState와
	 * 동일하게 pending/confirmed/ready 다 가능하다 (손님이 아직 픽업 전이면 매장 수락 여부와 상관없이
	 * 언제든 매장이 취소 가능).
	 */
	@GetMapping("/store-cancel/lookup")
	@ResponseBody
	public PickupLookupResponseDto storeCancelLookup(@RequestParam String pickupCode) {
		ReservationEntity reservation = reservationRepository.findByPickupCode(pickupCode).orElse(null);
		if (reservation == null) {
			return PickupLookupResponseDto.notFound();
		}

		String blockedMessage = switch (reservation.getStatus()) {
			case "picked" -> "이미 픽업 완료된 예약은 취소할 수 없어요.";
			case "cancelled" -> "이미 취소된 예약이에요.";
			case "noshowed" -> "이미 노쇼 처리된 예약이라 취소할 수 없어요.";
			default -> null;   // "pending", "confirmed", "ready"만 정상 진행
		};
		if (blockedMessage != null) {
			return PickupLookupResponseDto.blocked(blockedMessage);
		}

		StoreEntity store = storeRepository.findById(reservation.getStoreId()).orElse(null);

		return PickupLookupResponseDto.success(
				store != null ? store.getStoreName() : "-",
				reservation.getProductName(),
				reservation.getReservedQuantity(),
				reservation.getTotalPrice());
	}

	/**
	 * 매장 취소 처리: 시간 제한 없이 언제든 가능하고, ReservationService.cancelByStore가 환불 처리까지 담당한다.
	 * 결과 화면에서 "뭘 취소한 건지" 바로 보이게, 상품/매장 정보도 같이 조회해서 넘긴다.
	 *
	 * 추가됨 — 왜: pickupCode로만 예약을 찾아서, 다른 매장의 픽업 코드를 알기만 하면(또는 목록
	 * 필터링 버그로 노출됐던 다른 매장 코드로) 취소시킬 수 있는 구멍이 있었다. 로그인한 점주의
	 * 매장 소유가 아니면 막는다.
	 */
	@PostMapping("/store-cancel")
	public String storeCancel(@RequestParam String pickupCode,
							   @RequestParam(required = false) String reason,
							   HttpSession session,
							   Model model) {
		ReservationEntity target = reservationRepository.findByPickupCode(pickupCode)
				.orElseThrow(() -> new EntityNotFoundException("픽업 코드를 찾을 수 없습니다: " + pickupCode));

		if (!target.getStoreId().equals(resolveCurrentStoreId(session))) {
			throw new CancellationNotAllowedException("다른 매장의 예약은 취소할 수 없어요.");
		}

		ReservationEntity cancelled = reservationService.cancelByStore(target.getId(), reason);

		StoreEntity store = storeRepository.findById(cancelled.getStoreId()).orElse(null);

		model.addAttribute("pickupCode", cancelled.getPickupCode());
		model.addAttribute("cancelReason", cancelled.getCancelReason());
		model.addAttribute("productName", cancelled.getProductName());
		model.addAttribute("quantity", cancelled.getReservedQuantity());
		model.addAttribute("totalPrice", cancelled.getTotalPrice());
		model.addAttribute("storeName", store != null ? store.getStoreName() : "-");

		return "reservationView/storeCancelResult";
	}

	/**
	 * 사장님용 "들어온 예약 확인" 화면: 결제 완료됐지만 아직 매장이 수락 안 한(confirmed) 주문 목록.
	 * (2026-08-21 추가) 매장이 여기서 "수락" 버튼을 눌러야(ready로 전환) 손님이 픽업하러 올 수 있다.
	 */
	@GetMapping("/incoming")
	public String incoming(HttpSession session, Model model) {
		Long storeId = resolveCurrentStoreId(session);
		List<ReservationIncomingItemDto> incoming = reservationService.getIncomingReservations(storeId);
		model.addAttribute("incoming", incoming);

		// 추가됨 — 왜: 수락(ready)까지는 됐는데 손님이 아직 QR/코드를 안 보여줘서 픽업 처리가 안 된
		// 예약을 확인할 방법이 없었다. "확인할 새 주문" 목록 화면에 자연스럽게 이어 붙인다.
		model.addAttribute("ready", reservationService.getReadyReservations(storeId));

		return "reservationView/incoming";
	}

	/**
	 * 추가됨 — 왜: 손님이 실제로 픽업해서 거래가 끝난 내역을 점주가 볼 화면이 없었다.
	 */
	@GetMapping("/completed")
	public String completed(HttpSession session, Model model) {
		model.addAttribute("completed", reservationService.getCompletedTransactions(resolveCurrentStoreId(session)));
		return "reservationView/completed";
	}

	/**
	 * "수락" 버튼 제출: confirmed -> ready로 전환한다. 이후부터 픽업 화면에서 이 예약을 처리할 수 있다.
	 * 추가됨 — 왜: id만 받아서 다른 매장 예약도 수락시킬 수 있는 구멍이 있었다. 로그인한 점주의
	 * 매장 소유가 아니면 막는다.
	 */
	@PostMapping("/{id}/accept")
	public String accept(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
		ReservationEntity reservation = reservationRepository.findById(id)
				.orElseThrow(() -> new EntityNotFoundException("예약을 찾을 수 없습니다. id=" + id));
		if (!reservation.getStoreId().equals(resolveCurrentStoreId(session))) {
			throw new AcceptNotAllowedException("다른 매장의 예약은 수락할 수 없어요.");
		}

		reservationService.acceptReservation(id);
		redirectAttributes.addFlashAttribute("acceptedMessage", "예약을 확인했어요. 이제 손님이 픽업하러 올 수 있어요.");
		return "redirect:/reservation/incoming";
	}

	/**
	 * 예약 확인 화면에서 바로 누르는 "취소" 버튼. (2026-08-21 추가 — 상의 후 결정: 예약 확인 화면과
	 * 매장 취소 화면은 성격이 달라서 계속 따로 두되, 새 주문이 들어온 그 자리에서 바로 거절도 할 수
	 * 있게 이 버튼만 추가했다.) 실제 취소 처리는 매장 취소 화면과 동일하게 cancelByStore를 그대로
	 * 재사용한다 — 재고 복구/환불 표시/상태 변경 로직을 중복 작성하지 않기 위함.
	 *
	 * 추가됨 — 왜: id만 받아서 다른 매장 예약도 취소시킬 수 있는 구멍이 있었다. 로그인한 점주의
	 * 매장 소유가 아니면 막는다.
	 */
	@PostMapping("/{id}/store-cancel")
	public String storeCancelById(@PathVariable Long id,
								   @RequestParam(required = false) String reason,
								   HttpSession session,
								   RedirectAttributes redirectAttributes) {
		ReservationEntity reservation = reservationRepository.findById(id)
				.orElseThrow(() -> new EntityNotFoundException("예약을 찾을 수 없습니다. id=" + id));
		if (!reservation.getStoreId().equals(resolveCurrentStoreId(session))) {
			throw new CancellationNotAllowedException("다른 매장의 예약은 취소할 수 없어요.");
		}

		reservationService.cancelByStore(id, reason);
		redirectAttributes.addFlashAttribute("cancelledMessage", "예약을 취소했어요. 결제하신 금액은 환불됩니다.");
		return "redirect:/reservation/incoming";
	}

	// 수정됨 (2026-08-24, 점검/정리) — 왜: 여기 직접 만들었던 정규식 기반 파서가
	// ReservationService.isTooCloseToClosing()이 이미 쓰고 있던 net.dsa.girigiri.util.OperatingHoursUtil의
	// parseClosingTime과 로직이 겹치면서(중복), 실패 시 동작도 서로 달랐다(이쪽은 null 반환, 저쪽은
	// IllegalArgumentException 발생) — 같은 "영업시간 문자열 파싱"을 두 곳에서 다르게 하고 있던 셈이라
	// 한쪽만 고치면 다른 쪽은 안 고쳐지는 버그가 나기 쉬웠다. OperatingHoursUtil을 유일한 파서로 쓰고,
	// 여기서는 그 예외를 잡아서 이 화면이 원래 기대하던 "파싱 실패 시 null(= 이 옵션 숨김)" 동작만 감싸준다.
	private LocalTime parseClosingTime(String operatingHours) {
		try {
			return OperatingHoursUtil.parseClosingTime(operatingHours);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/**
	 * 매장이 "준비 시간"/"마지막 픽업 시간"을 직접 설정하는 화면. (2026-08-21 추가, 이후 영업종료시간
	 * 옵션 추가) 이 값들이 PickupAvailabilityUtil의 자동계산(체크아웃 화면 "예상 픽업 가능 시각") 기준이
	 * 된다. 아직 설정 안 한 매장(마지막 픽업시간 NULL)은 "제한 없음"으로 취급된다 — StoreEntity 주석 참고.
	 */
	@GetMapping("/settings")
	public String settingsForm(HttpSession session, Model model) {
		StoreEntity store = storeRepository.findById(resolveCurrentStoreId(session))
				.orElseThrow(() -> new EntityNotFoundException("매장을 찾을 수 없습니다. id=" + resolveCurrentStoreId(session)));

		LocalTime closingTime = parseClosingTime(store.getOperatingHours());

		String pickupTimeMode;
		if (store.getLastPickupTime() == null) {
			pickupTimeMode = "unlimited";
		} else if (closingTime != null && closingTime.equals(store.getLastPickupTime())) {
			pickupTimeMode = "close";
		} else {
			pickupTimeMode = "manual";
		}

		model.addAttribute("storeName", store.getStoreName());
		model.addAttribute("prepTimeMinutes",
				store.getPrepTimeMinutes() != null ? store.getPrepTimeMinutes() : DEFAULT_PREP_TIME_MINUTES);
		model.addAttribute("lastPickupTime", store.getLastPickupTime() != null ? store.getLastPickupTime().toString() : "");
		model.addAttribute("operatingHours", store.getOperatingHours());
		model.addAttribute("closingTimeDisplay", closingTime != null ? closingTime.toString() : null);
		model.addAttribute("pickupTimeMode", pickupTimeMode);
		return "reservationView/pickupSettings";
	}

	/**
	 * "저장" 버튼 제출. pickupTimeMode: "manual"(직접 입력한 lastPickupTime 사용) /
	 * "close"(영업 종료 시간을 매번 다시 계산해서 사용 — operatingHours가 나중에 바뀌어도 따라간다) /
	 * "unlimited"(제한 없음, NULL 저장).
	 */
	@PostMapping("/settings")
	public String saveSettings(@RequestParam int prepTimeMinutes,
								@RequestParam(required = false) String lastPickupTime,
								@RequestParam(defaultValue = "manual") String pickupTimeMode,
								HttpSession session,
								RedirectAttributes redirectAttributes) {
		StoreEntity store = storeRepository.findById(resolveCurrentStoreId(session))
				.orElseThrow(() -> new EntityNotFoundException("매장을 찾을 수 없습니다. id=" + resolveCurrentStoreId(session)));

		// 방어적으로 최소값 보정 (0/음수/공란 입력 방지) — 준비시간이 0 이하면 픽업 가능 시각 계산이 의미없어진다.
		store.setPrepTimeMinutes(Math.max(prepTimeMinutes, 1));

		switch (pickupTimeMode) {
			case "unlimited" -> store.setLastPickupTime(null);
			// operatingHours 파싱 실패하면(예: 그 사이 매장이 영업시간을 이상한 형식으로 바꿨다면) 조용히
			// 제한없음(null)으로 저장한다 — 화면에서 이 옵션은 파싱 성공했을 때만 보이므로 흔한 경우는 아니다.
			case "close" -> store.setLastPickupTime(parseClosingTime(store.getOperatingHours()));
			default -> store.setLastPickupTime(
					(lastPickupTime == null || lastPickupTime.isBlank()) ? null : LocalTime.parse(lastPickupTime));
		}

		storeRepository.save(store);
		redirectAttributes.addFlashAttribute("savedMessage", "주문 마감 설정을 저장했어요.");
		return "redirect:/reservation/settings";
	}

	/** 사장님이 픽업 현장에서 픽업 코드를 입력하는 화면. */
	@GetMapping("/pickup")
	public String pickupForm() {
		return "reservationView/pickup";
	}

	/**
	 * QR 스캔 직후, 실제로 "픽업 완료 처리" 버튼을 누르기 전에 매장/상품명/수량을 화면에 미리
	 * 보여주기 위한 조회 전용 API. confirmPickup과 달리 예약 상태를 바꾸지 않고 조회만 하므로
	 * 스캔이 여러 번 잡혀도 안전하다. (JS fetch로 호출, 응답은 JSON)
	 */
	@GetMapping("/pickup/lookup")
	@ResponseBody
	public PickupLookupResponseDto pickupLookup(@RequestParam String pickupCode) {
		ReservationEntity reservation = reservationRepository.findByPickupCode(pickupCode).orElse(null);
		if (reservation == null) {
			return PickupLookupResponseDto.notFound();
		}

		String blockedMessage = switch (reservation.getStatus()) {
			case "picked" -> "이미 픽업 완료 처리된 예약이에요.";
			case "cancelled", "noshowed" -> "취소되었거나 노쇼 처리된 예약이라 픽업할 수 없어요.";
			case "pending" -> "아직 결제가 완료되지 않은 예약이에요.";
			// 추가됨 (2026-08-21) — 왜: 매장이 "예약 확인" 화면에서 수락하기 전에는 픽업 처리가 안 되게 막는다.
			case "confirmed" -> "아직 매장에서 확인(수락)하지 않은 예약이에요.";
			default -> null;   // "ready"만 정상 진행
		};
		if (blockedMessage != null) {
			return PickupLookupResponseDto.blocked(blockedMessage);
		}

		StoreEntity store = storeRepository.findById(reservation.getStoreId()).orElse(null);

		return PickupLookupResponseDto.success(
				store != null ? store.getStoreName() : "-",
				reservation.getProductName(),
				reservation.getReservedQuantity(),
				reservation.getTotalPrice());
	}

	/**
	 * 여러 예약을 한 번에 픽업 처리한다. 화면에서 QR을 여러 번 스캔해서 "장바구니"처럼 모아뒀다가
	 * 한 번에 이 API로 보낸다. 코드 하나가 실패(이미 픽업됨/취소됨 등)해도 나머지 코드는 계속
	 * 처리하도록, confirmPickup을 코드마다 개별로 try/catch 해서 결과를 모은다 — 배치 전체가
	 * 한 건의 실패 때문에 통째로 막히면 안 되기 때문.
	 */
	@PostMapping("/pickup/batch")
	@ResponseBody
	public List<PickupBatchItemResultDto> pickupBatch(@RequestParam List<String> pickupCodes) {
		List<PickupBatchItemResultDto> results = new ArrayList<>();

		for (String pickupCode : pickupCodes) {
			try {
				ReservationEntity reservation = reservationService.confirmPickup(pickupCode);
				results.add(PickupBatchItemResultDto.success(
						pickupCode,
						reservation.getProductName(),
						reservation.getReservedQuantity()));
			} catch (EntityNotFoundException | PickupNotAllowedException e) {
				results.add(PickupBatchItemResultDto.failure(pickupCode, e.getMessage()));
			}
		}

		return results;
	}

	/**
	 * 픽업 코드 확인 처리: ReservationService.confirmPickup 하나만 부르면 된다.
	 * 결과 화면에서 "뭘 픽업 처리한 건지" 바로 보이게, 상품/매장 정보도 같이 조회해서 넘긴다.
	 *
	 * (참고: 화면(pickup.html)은 이제 스캔/입력한 코드를 모았다가 위 pickupBatch로 한 번에 보내는
	 *  방식으로 바뀌어서, 이 단건 엔드포인트는 화면에서는 더 이상 안 쓰인다. 다른 곳에서 단건 처리가
	 *  필요해질 수도 있어 일단 남겨둔다.)
	 */
	@PostMapping("/pickup")
	public String pickup(@RequestParam String pickupCode, Model model) {
		ReservationEntity reservation = reservationService.confirmPickup(pickupCode);

		StoreEntity store = storeRepository.findById(reservation.getStoreId()).orElse(null);

		model.addAttribute("pickupCode", reservation.getPickupCode());
		model.addAttribute("pickedAt", reservation.getPickedAt().format(DISPLAY_FORMAT));
		model.addAttribute("productName", reservation.getProductName());
		model.addAttribute("quantity", reservation.getReservedQuantity());
		model.addAttribute("totalPrice", reservation.getTotalPrice());
		model.addAttribute("storeName", store != null ? store.getStoreName() : "-");

		return "reservationView/pickupResult";
	}

	/**
	 * 영수증 PDF는 이제 (Supabase가 설정돼 있으면) Supabase Storage 클라우드에 있어서, 그 실제 URL로
	 * 리다이렉트만 시켜준다. Supabase가 아직 설정 안 된 상태라면 ReceiptService가 예전처럼 로컬
	 * receipts/ 폴더에 저장해뒀을 거라, 그 경우엔 예전처럼 파일을 직접 읽어서 내려준다.
	 * (pdfUrl이 http(s)로 시작하는지 보고 두 경우를 구분한다.)
	 *
	 * 영수증은 원래 결제 확인 시점(ReservationService.confirmPayment)에 한 번 만들어지고, 이후 취소/노쇼로 상태가
	 * 바뀌는 "그 순간"(cancelReservation/cancelByStore/processNoShows 안에서) 다시 만들어지기 때문에,
	 * 여기서는 보통 DB에 이미 있는 Receipt 레코드를 찾아서 URL만 꺼내 쓰면 된다.
	 * 예외적으로 sample-data.sql로 직접 넣어서 우리 코드를 한 번도 안 거친 예약처럼 Receipt 레코드
	 * 자체가 없는 경우에만, 그 자리에서 한 번 만들어준다.
	 */
	@GetMapping("/{id}/receipt")
	public ResponseEntity<?> receipt(@PathVariable Long id) {
		reservationRepository.findById(id)
				.orElseThrow(() -> new EntityNotFoundException("예약을 찾을 수 없습니다. id=" + id));

		ReceiptEntity receipt = receiptRepository.findByReservationId(id)
				.orElseGet(() -> receiptService.generateReceipt(id));

		String pdfUrl = receipt.getPdfUrl();

		if (pdfUrl.startsWith("http://") || pdfUrl.startsWith("https://")) {
			return ResponseEntity.status(302).location(URI.create(pdfUrl)).build();
		}

		// Supabase 미설정 상태의 로컬 폴백: pdfUrl에 로컬 파일 경로가 그대로 들어있다.
		try {
			byte[] pdf = Files.readAllBytes(Path.of(pdfUrl));
			return ResponseEntity.ok()
					.contentType(MediaType.APPLICATION_PDF)
					.header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=receipt-" + id + ".pdf")
					.body(pdf);
		} catch (IOException e) {
			throw new EntityNotFoundException("영수증 파일을 찾을 수 없습니다. reservationId=" + id);
		}
	}
}

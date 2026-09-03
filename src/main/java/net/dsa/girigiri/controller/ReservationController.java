package net.dsa.girigiri.controller;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.PaymentConfirmResponseDto;
import net.dsa.girigiri.domain.dto.ReservationListItemDto;
import net.dsa.girigiri.domain.dto.ReservationPrepareResponseDto;
import net.dsa.girigiri.domain.entity.PaymentEntity;
import net.dsa.girigiri.domain.entity.ProductEntity;
import net.dsa.girigiri.domain.entity.ReservationEntity;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.exception.OrderNotAllowedException;
import net.dsa.girigiri.exception.PaymentVerificationException;
import net.dsa.girigiri.exception.ReservationAccessDeniedException;
import net.dsa.girigiri.service.LookupService;
import net.dsa.girigiri.service.ReservationService;
import net.dsa.girigiri.util.PickupAvailabilityUtil;
import net.dsa.girigiri.util.PortOneClient;
import net.dsa.girigiri.util.QrCodeUtil;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 예약 확인 -> 예약 생성 -> 완료 화면(QR/영수증)까지 담당하는 컨트롤러.
 * ReservationService(이미 만들어서 테스트해둔 부품)를 실제 화면 버튼과 연결하는 역할만 한다.
 *
 * 2026-09-03 — 686줄까지 커져서 도메인별로 분리했다. 이 클래스는 손님의 "예약 생성 흐름"(체크아웃 →
 * 결제 준비/확인 → 완료 화면 → 취소)만 남고, 점주용 화면(들어온 예약/매장 취소/픽업 설정)은
 * ReservationStoreController로, 현장 픽업 처리·영수증은 ReservationPickupController로 옮겼다.
 * @RequestMapping("/reservation")은 세 클래스 모두 그대로 유지해서 URL은 하나도 안 바뀐다.
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
	private final LookupService lookupService;
	private final PortOneClient portOneClient;

	// 변경됨 (2026-09-01) — 왜: 문창호님의 로그인/세션 작업(OAuth2LoginSuccessHandler +
	// AuthSessionInitializer)이 이미 끝나서 세션에 실제 로그인한 사용자의 userId가 들어있다.
	// 더 이상 고정 사용자(1L)를 쓸 이유가 없어서 ChatController/MypageController와 동일하게
	// 세션에서 꺼내오도록 교체했다. /reservation/**을 WebSecurityConfig의 공개 목록에서 뺐기
	// 때문에, 여기까지 도달했다면 이미 로그인된 상태라 null일 일은 없다.
	//
	// 2026-09-03 — package-private static으로 바꿨다. ReservationPickupController#receipt도
	// 동일한 로직이 필요한데, 컨트롤러 3개로 쪼개면서 헬퍼를 그대로 복사하지 않기로 해서
	// (레이어 규칙 정리 세션 합의) 여기 하나만 두고 그쪽에서 ReservationController.resolveCurrentUserId(session)
	// 형태로 그대로 호출한다.
	static Long resolveCurrentUserId(HttpSession session) {
		return (Long) session.getAttribute("userId");
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
		ProductEntity product = lookupService.getProduct(productId);
		StoreEntity store = lookupService.getStore(product.getStoreId());

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
												  @RequestParam(defaultValue = "1") int quantity,
												  HttpSession session) {
		ProductEntity product = lookupService.getProduct(productId);
		StoreEntity store = lookupService.getStore(product.getStoreId());

		LocalDateTime now = LocalDateTime.now();
		int prepTimeMinutes = store.getPrepTimeMinutes() != null ? store.getPrepTimeMinutes() : DEFAULT_PREP_TIME_MINUTES;

		if (!PickupAvailabilityUtil.canOrderNow(now, store.getLastPickupTime(), prepTimeMinutes)) {
			throw new OrderNotAllowedException("죄송해요, 오늘 주문 가능한 시간이 지났어요. 내일 다시 확인해주세요.");
		}

		LocalDateTime pickupTime = PickupAvailabilityUtil.earliestPickupTime(now, prepTimeMinutes);

		ReservationEntity reservation = reservationService.prepareReservation(
				resolveCurrentUserId(session), productId, quantity, pickupTime);

		PaymentEntity payment = reservationService.getPaymentByReservationId(reservation.getId());

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

	/**
	 * 예약 완료 화면: QR 코드 + 픽업 코드 + 영수증 링크를 보여준다.
	 *
	 * 변경됨 (2026-09-01) — 왜: 로그인은 확인하지만 이 예약이 진짜 로그인한 내 예약인지는 확인 안 해서,
	 * URL의 id만 바꾸면 남의 예약 완료 화면(픽업 코드/QR 포함)을 볼 수 있었다. resolveCurrentUserId와
	 * 비교해서 본인 예약이 아니면 막는다 (cancel/qrImage/receipt도 동일하게 적용).
	 */
	@GetMapping("/{id}/complete")
	public String complete(@PathVariable Long id, HttpSession session, Model model) {
		ReservationEntity reservation = lookupService.getReservation(id);
		if (!reservation.getUserId().equals(resolveCurrentUserId(session))) {
			throw new ReservationAccessDeniedException("본인 예약만 확인할 수 있어요.");
		}
		StoreEntity store = lookupService.getStore(reservation.getStoreId());

		model.addAttribute("reservationId", reservation.getId());
		model.addAttribute("storeName", store.getStoreName());
		model.addAttribute("productName", reservation.getProductName());
		model.addAttribute("quantity", reservation.getReservedQuantity());
		model.addAttribute("totalPrice", reservation.getTotalPrice());
		model.addAttribute("pickupTime", reservation.getPickupTime().format(DISPLAY_FORMAT));
		model.addAttribute("pickupCode", reservation.getPickupCode());

		return "reservationView/complete";
	}

	/**
	 * 완료 화면의 <img> 태그가 실제로 부르는 QR 이미지 바이트.
	 * 변경됨 (2026-09-01) — 본인 예약이 아니면 QR도 못 보게 막는다 (complete 참고).
	 */
	@GetMapping("/{id}/qr-image")
	@ResponseBody
	public ResponseEntity<byte[]> qrImage(@PathVariable Long id, HttpSession session) {
		ReservationEntity reservation = lookupService.getReservation(id);
		if (!reservation.getUserId().equals(resolveCurrentUserId(session))) {
			throw new ReservationAccessDeniedException("본인 예약의 QR만 볼 수 있어요.");
		}
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
								  HttpSession session, Model model) {
		List<ReservationListItemDto> reservations =
				reservationService.getMyReservations(resolveCurrentUserId(session), tab);

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
	public String cancel(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
		// 추가됨 (2026-09-01) — 왜: id만 받아서 다른 사람 예약도 취소시킬 수 있는 구멍이 있었다.
		// 로그인한 본인 예약이 아니면 막는다 (store 쪽 accept/storeCancelById와 동일한 패턴).
		ReservationEntity reservation = lookupService.getReservation(id);
		if (!reservation.getUserId().equals(resolveCurrentUserId(session))) {
			throw new ReservationAccessDeniedException("본인 예약만 취소할 수 있어요.");
		}

		reservationService.cancelReservation(id);
		redirectAttributes.addFlashAttribute("cancelledMessage", "예약이 취소됐어요. 결제하신 금액은 환불됩니다.");
		return "redirect:/reservation/my?tab=cancelled";
	}
}

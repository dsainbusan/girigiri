package net.dsa.girigiri.exception;

import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

	// 데이터를 찾을 수 없는 경우
	@ExceptionHandler(EntityNotFoundException.class)
	public String handleNotFound(EntityNotFoundException e, Model model) {
		log.debug("> [GlobalException] EntityNotFoundException: {}", e.getMessage());
		model.addAttribute("message", e.getMessage());
		return "errorView/custom-error-page";
	}

	// 재고 부족 (동시 예약으로 매진된 경우 포함)
	@ExceptionHandler(OutOfStockException.class)
	public String handleOutOfStock(OutOfStockException e, Model model) {
		log.debug("> [GlobalException] OutOfStockException: {}", e.getMessage());
		model.addAttribute("message", e.getMessage());
		return "errorView/custom-error-page";
	}

	// 픽업 처리 불가 (이미 픽업됨 / 취소·노쇼 / 결제 미완료)
	@ExceptionHandler(PickupNotAllowedException.class)
	public String handlePickupNotAllowed(PickupNotAllowedException e, Model model) {
		log.debug("> [GlobalException] PickupNotAllowedException: {}", e.getMessage());
		model.addAttribute("message", e.getMessage());
		return "errorView/custom-error-page";
	}

	// 취소 불가 (이미 픽업됨 / 이미 취소·노쇼)
	@ExceptionHandler(CancellationNotAllowedException.class)
	public String handleCancellationNotAllowed(CancellationNotAllowedException e, Model model) {
		log.debug("> [GlobalException] CancellationNotAllowedException: {}", e.getMessage());
		model.addAttribute("message", e.getMessage());
		return "errorView/custom-error-page";
	}

	// 주문 불가 (매장 마지막 픽업시간이 지나서 오늘 판매 마감)
	@ExceptionHandler(OrderNotAllowedException.class)
	public String handleOrderNotAllowed(OrderNotAllowedException e, Model model) {
		log.debug("> [GlobalException] OrderNotAllowedException: {}", e.getMessage());
		model.addAttribute("message", e.getMessage());
		return "errorView/custom-error-page";
	}

	// 예약 확인(수락) 불가 (이미 수락됨 / 이미 픽업·취소·노쇼)
	@ExceptionHandler(AcceptNotAllowedException.class)
	public String handleAcceptNotAllowed(AcceptNotAllowedException e, Model model) {
		log.debug("> [GlobalException] AcceptNotAllowedException: {}", e.getMessage());
		model.addAttribute("message", e.getMessage());
		return "errorView/custom-error-page";
	}

	// 로그인은 했지만 본인 예약이 아닌 걸 URL 조작으로 접근하려는 경우 (취소/완료화면/QR/영수증)
	@ExceptionHandler(ReservationAccessDeniedException.class)
	public String handleReservationAccessDenied(ReservationAccessDeniedException e, Model model) {
		log.debug("> [GlobalException] ReservationAccessDeniedException: {}", e.getMessage());
		model.addAttribute("message", e.getMessage());
		return "errorView/custom-error-page";
	}

	// PortOne 결제 검증 실패 (결제 미완료 / 금액 불일치 등). checkout.html의 confirm-payment API는
	// 이 예외를 컨트롤러에서 직접 잡아 JSON으로 응답하지만(AJAX라 에러 페이지로 넘기면 안 됨),
	// 혹시 다른 경로로 이 예외가 여기까지 올라오는 경우를 대비해 폴백으로 남겨둔다.
	@ExceptionHandler(PaymentVerificationException.class)
	public String handlePaymentVerificationFailed(PaymentVerificationException e, Model model) {
		log.debug("> [GlobalException] PaymentVerificationException: {}", e.getMessage());
		model.addAttribute("message", e.getMessage());
		return "errorView/custom-error-page";
	}

	// 리뷰 사진 업로드 실패 (이미지가 아닌 파일 / 용량 초과)
	@ExceptionHandler(InvalidImageFileException.class)
	public String handleInvalidImageFile(InvalidImageFileException e, Model model) {
		log.debug("> [GlobalException] InvalidImageFileException: {}", e.getMessage());
		model.addAttribute("message", e.getMessage());
		return "errorView/custom-error-page";
	}

	// 리뷰 작성 불가 (그 가게에서 예약·픽업 완료 이력이 없음 — 화면에서 버튼을 숨겨도 폼 직접 제출은 막아야 함)
	@ExceptionHandler(ReviewNotAllowedException.class)
	public String handleReviewNotAllowed(ReviewNotAllowedException e, Model model) {
		log.debug("> [GlobalException] ReviewNotAllowedException: {}", e.getMessage());
		model.addAttribute("message", e.getMessage());
		return "errorView/custom-error-page";
	}

	// 추가됨 (강노은) — 왜: 삭제된 리뷰/문의/가게 등을 (예: 브라우저 "뒤로가기"로 지운 문의 상세를
	// 다시 요청하는 경우) findById(...).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, ...))
	// 형태로 여기저기서 던지고 있는데, 지금까지는 이게 EntityNotFoundException이 아니라서 저 위
	// 핸들러에 안 잡히고 맨 아래 handleException(Exception)의 "알 수 없는 오류가 발생했습니다"
	// 딱딱한 에러 페이지로 떨어졌었다. 이런 "그냥 없는 걸 다시 봤을 뿐" 케이스는 페이지에 머무르게
	// 하기보다 알림창을 한 번 띄우고 홈으로 돌려보내는 게 자연스러워서 전용 처리를 추가한다.
	@ExceptionHandler(ResponseStatusException.class)
	public String handleResponseStatusException(ResponseStatusException e, Model model) {
		log.debug("> [GlobalException] ResponseStatusException: {}", e.getReason());
		model.addAttribute("message", e.getReason() != null ? e.getReason() : "요청하신 내용을 찾을 수 없어요.");
		return "errorView/alert-redirect";
	}

	// 그 외 처리되지 않은 예외
	@ExceptionHandler(Exception.class)
	public String handleException(Exception e, Model model) {
		log.debug("> [GlobalException] Exception: {}", e.getMessage());
		model.addAttribute("message", "알 수 없는 오류가 발생했습니다.");
		return "errorView/custom-error-page";
	}
}

package net.dsa.girigiri.exception;

import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

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

	// 그 외 처리되지 않은 예외
	@ExceptionHandler(Exception.class)
	public String handleException(Exception e, Model model) {
		log.debug("> [GlobalException] Exception: {}", e.getMessage());
		model.addAttribute("message", "알 수 없는 오류가 발생했습니다.");
		return "errorView/custom-error-page";
	}
}

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

	// 그 외 처리되지 않은 예외
	@ExceptionHandler(Exception.class)
	public String handleException(Exception e, Model model) {
		log.debug("> [GlobalException] Exception: {}", e.getMessage());
		model.addAttribute("message", "알 수 없는 오류가 발생했습니다.");
		return "errorView/custom-error-page";
	}
}

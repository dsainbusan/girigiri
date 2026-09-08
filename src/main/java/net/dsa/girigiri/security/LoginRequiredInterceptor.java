package net.dsa.girigiri.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * {@link LoginRequired} 참고 — 메서드(또는 클래스)에 그 어노테이션이 붙어있으면 세션에
 * userId가 없을 때 대신 막아준다. 뷰 컨트롤러는 로그인 화면으로 리다이렉트, REST 컨트롤러
 * ({@code @RestController})는 401 JSON으로 응답한다(fetch로 호출하는 쪽이 리다이렉트 HTML을
 * 받고 깨지는 걸 방지 — ChatController/LikeApiController 등에서 이미 같은 이유로 401을 씀).
 */
@Slf4j
@Component
public class LoginRequiredInterceptor implements HandlerInterceptor {

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
			throws IOException {
		if (!(handler instanceof HandlerMethod handlerMethod)) {
			return true;
		}
		boolean required = handlerMethod.hasMethodAnnotation(LoginRequired.class)
				|| handlerMethod.getBeanType().isAnnotationPresent(LoginRequired.class);
		if (!required) {
			return true;
		}

		HttpSession session = request.getSession(false);
		if (session != null && session.getAttribute("userId") != null) {
			return true;
		}

		if (handlerMethod.getBeanType().isAnnotationPresent(RestController.class)) {
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			response.setContentType("application/json;charset=UTF-8");
			response.getWriter().write("{\"error\":\"login_required\"}");
		} else {
			response.sendRedirect(request.getContextPath() + "/auth/loginForm");
		}
		return false;
	}
}

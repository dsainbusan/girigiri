package net.dsa.girigiri.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import net.dsa.girigiri.domain.entity.UserEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 추가됨 (2026-08-21) — 왜: 이메일 로그인 성공 시 OAuth2LoginSuccessHandler와 동일하게 세션을
 * 채워야 해서(AuthSessionInitializer 공용 로직) formLogin용 successHandler를 별도로 둔다.
 */
@Component
public class EmailLoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
			throws IOException, ServletException {
		UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
		UserEntity user = principal.getUser();

		HttpSession session = request.getSession();
		String targetUrl = AuthSessionInitializer.initSessionAndGetTargetUrl(session, user);

		clearAuthenticationAttributes(request);
		getRedirectStrategy().sendRedirect(request, response, targetUrl);
	}
}

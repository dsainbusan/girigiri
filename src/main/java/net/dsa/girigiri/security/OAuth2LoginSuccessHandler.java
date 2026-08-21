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
 * 소셜 로그인 성공 시 CLAUDE.md 세션 구조(userId, role, viewMode, storeId) 중
 * userId/role을 세션에 채운다.
 *
 * TODO(담당 미정): viewMode 초기값·storeId 연결은 점주(OWNER) 신청/승인 플로우가 갖춰지면 채운다.
 *
 * 변경됨 (2026-08-20) — 왜: LINE 로그인(openid 스코프)은 principal이 OAuth2UserPrincipal이 아니라
 * OidcUserPrincipal이라, 구체 타입으로 캐스팅하면 LINE 로그인에서만 ClassCastException이 났다.
 * 두 타입이 공통으로 구현하는 UserPrincipal 인터페이스로 캐스팅해서 로그인 방식에 상관없이 처리한다.
 *
 * 변경됨 (2026-08-21) — 왜: role=PENDING 개념이 없어지고 가입 시 무조건 role=USER로 확정되므로,
 * "PENDING이면 /auth/roleSelect로" 분기가 더는 필요 없다 — 로그인 성공하면 항상 "/"로 이동.
 *
 * 변경됨 (2026-08-21) — 왜: 최초 로그인 직후엔 닉네임/활동 지역을 아직 입력받지 않았으므로(profileCompleted=false),
 * "/" 대신 회원가입 완료 화면(/auth/signup)으로 보낸다. 이미 프로필을 완성한 재로그인 유저는 그대로 "/"로 이동.
 */
@Component
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
			throws IOException, ServletException {
		UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
		UserEntity user = principal.getUser();

		HttpSession session = request.getSession();
		session.setAttribute("userId", user.getId());
		session.setAttribute("role", user.getRole());

		clearAuthenticationAttributes(request);

		String targetUrl = user.isProfileCompleted() ? "/" : "/auth/signup";
		getRedirectStrategy().sendRedirect(request, response, targetUrl);
	}
}

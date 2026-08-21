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
 * TODO(담당 미정): storeId 연결은 점주(OWNER) 신청/승인 플로우가 갖춰지면 채운다.
 *
 * 변경됨 (2026-08-21) — 왜: role 라우팅 + 유저/점주 모드 분기 작업 시작하면서 viewMode 초기값을 채워넣는다.
 * role은 불변, viewMode는 헤더 토글(AuthController#toggleMode)로 로그인 후에도 바뀔 수 있다(CLAUDE.md
 * "인증 & 권한 설계" 참고). 여기서 이름이 "어드민"이던 옛 표기가 실제로는 점주(OWNER)를 가리키므로
 * ADMIN(운영자)은 이 분기와 무관 — OWNER만 OWNER_MODE로 시작하고 나머지(USER/ADMIN)는 USER_MODE.
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
 *
 * 변경됨 (2026-08-21) — 왜: 이메일 로그인(EmailLoginSuccessHandler)에도 완전히 동일한 세션 초기화
 * 로직이 필요해져서 AuthSessionInitializer로 공통 로직을 뽑아냈다.
 */
@Component
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

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

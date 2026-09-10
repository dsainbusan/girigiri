package net.dsa.girigiri.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import net.dsa.girigiri.domain.entity.UserEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * 부가정보 미입력(profile_completed=false) 로그인 사용자는 /auth/signup을 마치기 전엔
 * 다른 화면에 못 들어가게 막는다 (문창호, 2026-09-10).
 *
 * 왜: 소셜 로그인은 첫 로그인 시점에 이미 users 행이 생기고(SocialUserProvisioningService) 세션도
 * 세워진다. 이 가드가 없으면 /auth/signup에서 뒤로가기로 빠져나가 "부가정보 없이 로그인된" 상태로
 * 홈·마이페이지·탈퇴까지 쓸 수 있다(사용자 리포트로 발견).
 *
 * 세션 플래그 "profileCompleted"만 읽는다(매 요청 DB 조회 안 함). 로그인 시 AuthSessionInitializer가,
 * 부가정보 제출 완료 시 AuthController#signup이 이 값을 세팅한다. 값이 없거나(예전 세션) true면 통과.
 * 정적 리소스 제외는 WebInterceptorConfig의 excludePathPatterns에서 처리한다.
 */
@Component
public class ProfileCompletionInterceptor implements HandlerInterceptor {

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
			throws IOException {
		HttpSession session = request.getSession(false);
		if (session == null || session.getAttribute("userId") == null) {
			return true;
		}
		// true거나 미설정(예전 세션)이면 통과. 명시적으로 false일 때만 막는다.
		if (!Boolean.FALSE.equals(session.getAttribute("profileCompleted"))) {
			return true;
		}
		// 운영자(ADMIN)는 셀프 가입 플로우 자체가 없다.
		if (UserEntity.ROLE_ADMIN.equals(session.getAttribute("role"))) {
			return true;
		}

		String uri = request.getRequestURI();
		if (uri.equals("/auth/signup") || uri.equals("/auth/signup/cancel") || uri.equals("/auth/logout")) {
			return true;
		}

		response.sendRedirect(request.getContextPath() + "/auth/signup");
		return false;
	}
}

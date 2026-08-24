package net.dsa.girigiri.security;

import jakarta.servlet.http.HttpSession;
import net.dsa.girigiri.domain.entity.UserEntity;

/**
 * 추가됨 (2026-08-21) — 왜: 로그인 성공 시 세션(userId/role/viewMode)을 채우고 다음 목적지를 정하는
 * 로직이 OAuth2LoginSuccessHandler와 EmailLoginSuccessHandler에 완전히 똑같이 필요해서(로그인
 * 방식만 다를 뿐 성공 후 처리는 동일) 공용 헬퍼로 뽑았다.
 */
public final class AuthSessionInitializer {

	private AuthSessionInitializer() {
	}

	public static String initSessionAndGetTargetUrl(HttpSession session, UserEntity user) {
		session.setAttribute("userId", user.getId());
		session.setAttribute("role", user.getRole());
		session.setAttribute("viewMode", UserEntity.ROLE_OWNER.equals(user.getRole()) ? "OWNER_MODE" : "USER_MODE");

		return user.isProfileCompleted() ? "/" : "/auth/signup";
	}
}

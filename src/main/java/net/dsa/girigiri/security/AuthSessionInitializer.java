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

		// 추가됨 (2026-09-08) — 왜: role=ADMIN으로 로그인해도 profileCompleted 기준으로만 목적지를
		// 정해서 항상 유저 홈("/")으로 떨어졌다(사용자 리포트로 발견) — 운영자 계정은 애초에 셀프
		// 가입 플로우(회원가입 완료 화면)를 거칠 일이 없으니 profileCompleted 체크보다 먼저 확인해서
		// 곧장 슈퍼어드민 대시보드로 보낸다.
		if (UserEntity.ROLE_ADMIN.equals(user.getRole())) {
			return "/superadmin/dashboard";
		}

		return user.isProfileCompleted() ? "/" : "/auth/signup";
	}
}

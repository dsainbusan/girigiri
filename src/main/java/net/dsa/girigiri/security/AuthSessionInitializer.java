package net.dsa.girigiri.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import net.dsa.girigiri.domain.entity.UserEntity;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;

/**
 * 추가됨 (2026-08-21) — 왜: 로그인 성공 시 세션(userId/role/viewMode)을 채우고 다음 목적지를 정하는
 * 로직이 OAuth2LoginSuccessHandler와 EmailLoginSuccessHandler에 완전히 똑같이 필요해서(로그인
 * 방식만 다를 뿐 성공 후 처리는 동일) 공용 헬퍼로 뽑았다.
 */
public final class AuthSessionInitializer {

	private AuthSessionInitializer() {
	}

	public static String initSessionAndGetTargetUrl(HttpServletRequest request, HttpSession session, UserEntity user) {
		session.setAttribute("userId", user.getId());
		session.setAttribute("role", user.getRole());
		// 로그인 시 기본 도착지가 유저 홈("/app")이므로 초기 viewMode는 USER_MODE로 설정한다.
		session.setAttribute("viewMode", "USER_MODE");
		// 부가정보 입력 여부 — ProfileCompletionInterceptor가 매 요청 DB 조회 대신 이 플래그를 본다.
		session.setAttribute("profileCompleted", user.isProfileCompleted());

		// 추가됨 (2026-09-08) — 왜: role=ADMIN으로 로그인해도 profileCompleted 기준으로만 목적지를
		// 정해서 항상 유저 홈("/app")으로 떨어졌다(사용자 리포트로 발견) — 운영자 계정은 애초에 셀프
		// 가입 플로우(회원가입 완료 화면)를 거칠 일이 없으니 profileCompleted 체크보다 먼저 확인해서
		// 곧장 슈퍼어드민 대시보드로 보낸다.
		if (UserEntity.ROLE_ADMIN.equals(user.getRole())) {
			return "/superadmin/dashboard";
		}

		// 추가됨 (2026-09-22, UI/UX 감사) — "/auth/owner-apply"는 PUBLIC_URLS에 없어서(WebSecurityConfig)
		// 비로그인 상태로 누르면 Spring Security의 anyRequest().authenticated()가 컨트롤러까지
		// 가기도 전에 로그인 화면으로 돌려보낸다 — 이때 Spring Security가 기본으로 원래 요청을
		// HttpSessionRequestCache에 저장해두므로(우리가 따로 뭘 안 심어도), 로그인 성공 후 그
		// 저장된 요청이 owner-apply였는지 확인해서 이미 가입 완료된 사용자는 유저 홈 대신 원래
		// 의도한 입점 신청 화면으로 바로 이어준다. 부가정보 입력이 안 끝난 신규 가입자는 그 단계
		// (/auth/signup)를 건너뛸 수 없어 이 분기 대상이 아니다 — 거기서 "사장님으로 시작"을
		// 직접 고르면 된다(기존 플로우 그대로).
		SavedRequest savedRequest = new HttpSessionRequestCache().getRequest(request, null);
		boolean wantsOwnerApply = savedRequest != null && savedRequest.getRedirectUrl().contains("/auth/owner-apply");

		// 변경됨 (2026-09-17) — 왜: "/"가 마케팅 홈페이지로 바뀌면서 로그인 후 목적지도 "/app"으로.
		if (!user.isProfileCompleted()) {
			return "/auth/signup";
		}
		return wantsOwnerApply ? "/auth/owner-apply" : "/app";
	}
}

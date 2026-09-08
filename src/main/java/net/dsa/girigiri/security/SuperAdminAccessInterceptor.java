package net.dsa.girigiri.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import net.dsa.girigiri.domain.entity.UserEntity;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * "/superadmin/**"는 WebSecurityConfig의 PUBLIC_URLS에 permitAll로 올라가 있었다 — 로그인
 * 자체가 필요 없어서, 익명 요청으로 매장 삭제·회원 강제탈퇴·신고 처리로 예약 취소·회원 전체 CSV
 * 다운로드가 다 가능했다(코드 감사에서 발견). 여기서 role=ADMIN을 확인한다.
 *
 * WebSecurityConfig에서 permitAll을 제거해서 "로그인 자체는 했는지"는 Spring Security가 먼저
 * 걸러주고(비로그인이면 /auth/loginForm으로 리다이렉트), 이 인터셉터는 그다음 단계 — "로그인은
 * 했는데 ADMIN이 아닌" 경우만 처리하면 된다. 권한 체크는 항상 role 기준(viewMode 아님)이라는
 * CLAUDE.md 세션 규칙을 그대로 따른다.
 */
@Component
public class SuperAdminAccessInterceptor implements HandlerInterceptor {

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		HttpSession session = request.getSession(false);
		String role = session == null ? null : (String) session.getAttribute("role");
		if (!UserEntity.ROLE_ADMIN.equals(role)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "슈퍼어드민 권한이 필요해요.");
		}
		return true;
	}
}

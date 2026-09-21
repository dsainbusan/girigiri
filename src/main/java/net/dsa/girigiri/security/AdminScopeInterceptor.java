package net.dsa.girigiri.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import net.dsa.girigiri.domain.entity.UserEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * 추가됨 (2026-09-21) — SuperAdminAccessInterceptor의 반대 방향. 그쪽은 "/superadmin/**"에
 * ADMIN이 아니면 못 들어오게 막지만, 반대로 ADMIN이 유저/매장 화면(지도 앱, 마이페이지, 예약,
 * 점주 대시보드 등)에 들어오는 건 아무도 막고 있지 않았다 — role=ADMIN 세션으로 URL만 직접
 * 치면 그대로 다 보였다. dual-mode 세션 설계(CLAUDE.md)상 ADMIN은 운영자 전용이라 유저/점주
 * 화면에 볼일이 없으므로, 여기서 걸러서 슈퍼어드민 대시보드로 돌려보낸다.
 *
 * 권한 체크는 항상 role 기준(viewMode 아님) — 여기서도 동일하게 role만 본다.
 */
@Component
public class AdminScopeInterceptor implements HandlerInterceptor {

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
			throws IOException {
		HttpSession session = request.getSession(false);
		String role = session == null ? null : (String) session.getAttribute("role");
		if (UserEntity.ROLE_ADMIN.equals(role)) {
			response.sendRedirect(request.getContextPath() + "/superadmin/dashboard");
			return false;
		}
		return true;
	}
}

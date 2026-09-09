package net.dsa.girigiri.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import net.dsa.girigiri.domain.entity.UserEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 점주(OWNER) 계정이 /store/** 화면에 들어오면 세션 viewMode를 OWNER_MODE로 맞춘다 (문창호, 2026-09-08).
 *
 * 버그였던 상황: 점주가 "유저 모드로 전환"한 뒤 헤더 토글을 안 쓰고 URL·뒤로가기·마이페이지 "점주 모드"
 * 링크로 /store/dashboard에 다시 들어오면, 페이지는 점주 모드(dark=true, badge='점주 모드')로 그려지는데
 * session.viewMode는 USER_MODE라 헤더 토글이 "사장님 모드로"라고 모순되게 떴다.
 *
 * role은 절대 안 건드린다(권한 승격 아님). 화면 컨텍스트(/store/**)와 viewMode를 일치시키는 것뿐이다.
 * USER 계정은 애초에 /store/** 접근이 컨트롤러 단에서 막히므로 여기서 따로 다루지 않는다.
 */
@Component
public class ViewModeSyncInterceptor implements HandlerInterceptor {

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		HttpSession session = request.getSession(false);
		if (session == null) {
			return true;
		}
		if (UserEntity.ROLE_OWNER.equals(session.getAttribute("role"))) {
			String uri = request.getRequestURI();
			if (uri.startsWith("/store") || uri.startsWith("/reservation/store")
					|| uri.startsWith("/reservation/incoming") || uri.startsWith("/reservation/pickup")) {
				if (!"OWNER_MODE".equals(session.getAttribute("viewMode"))) {
					session.setAttribute("viewMode", "OWNER_MODE");
				}
			} else if (uri.startsWith("/mypage") || uri.startsWith("/user") || "/".equals(uri)) {
				if (!"USER_MODE".equals(session.getAttribute("viewMode"))) {
					session.setAttribute("viewMode", "USER_MODE");
				}
			}
		}
		return true;
	}
}

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
			} else if (uri.startsWith("/mypage") || uri.startsWith("/user/alerts")
					|| uri.startsWith("/user/settings") || uri.startsWith("/user/support")
					|| uri.startsWith("/user/inquiries")) {
				// 추가됨 (2026-09-22), 확장됨 (2026-09-26) — 왜: 처음엔 "마이" 탭 하나(/mypage)만 예외로
				// 뒀는데, 점주 모드 마이페이지의 알림 설정·고객 지원·회원정보 수정·환경설정 메뉴가 전부
				// /mypage/**·/user/alerts·/user/support(하위 /user/inquiries 포함)·/user/settings라
				// 이 메뉴 중 아무거나 눌렀다가 뒤로가기로 돌아와도 이미 session.viewMode가 USER_MODE로
				// 바뀐 뒤라 마이페이지가 유저용 레이아웃(하단 탭바 포함)으로 뒤바뀌어 있었다 — "버튼
				// 눌렀다가 뒤로가기하면 일반 모드로 전환돼버린다"는 버그 리포트. 이 화면들은 실제로
				// 점주·일반 계정이 똑같이 쓰는 공통 "내 계정" 화면이라(강노은/송채현이 role로 내부
				// 분기만 하지 화면 자체를 나누지 않음) 모드를 강제로 바꿀 이유가 없다 — 지금 모드를
				// 그대로 둔다. 진짜 "손님으로서" 하는 활동(찜·가계부·내 리뷰·예약내역 등)은 아래
				// 분기에서 여전히 USER_MODE로 맞춘다 — 그건 실제로 유저 역할 전환이 맞는 동작이다.
			} else if (uri.startsWith("/user") || "/app".equals(uri)) {
				if (!"USER_MODE".equals(session.getAttribute("viewMode"))) {
					session.setAttribute("viewMode", "USER_MODE");
				}
			}
		}
		return true;
	}
}

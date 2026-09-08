package net.dsa.girigiri.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * 코드 감사(2026-09-08)에서 CSRF가 전역 비활성(WebSecurityConfig의 .csrf(disable))이라는 게
 * 발견됐다 — 세션 쿠키만으로 인증되는 모든 상태변경 엔드포인트가 크로스사이트 폼 제출에 노출된
 * 상태였다. 특히 그때 같이 발견된 "/superadmin/** 인증 우회"와 겹치면 파괴적 액션까지 크로스사이트로
 * 트리거될 수 있었다.
 *
 * 정식 CSRF 토큰 방어(_csrf 히든 필드를 앱 전체 폼에 넣는 것)는 템플릿 수십 개를 건드리는 큰
 * 변경이라 이번 범위에서는 보류하고, 훨씬 가벼운 미티게이션만 먼저 넣는다: 상태변경 요청(GET/HEAD/
 * OPTIONS가 아닌 요청)의 Origin(없으면 Referer) 헤더가 우리 호스트가 아니면 막는다.
 *
 * 완전한 CSRF 방어는 아니다 — Origin/Referer가 둘 다 없는 요청은 통과시킨다(오탐으로 정상 사용자를
 * 막는 것보다, 흔한 공격 시나리오를 막는 데 집중). 하지만 공격자 페이지에서 실행되는 폼 제출/fetch는
 * 브라우저가 Origin(또는 Referer)을 공격자 도메인으로 자동으로 채우므로, 그런 요청은 이걸로 막힌다.
 */
@Component
public class OriginCheckFilter extends OncePerRequestFilter {

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String method = request.getMethod();
		if ("GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method)) {
			filterChain.doFilter(request, response);
			return;
		}

		String originHeader = request.getHeader("Origin");
		String sourceHeader = (originHeader != null && !originHeader.isBlank()) ? originHeader : request.getHeader("Referer");

		if (sourceHeader != null && !isSameHost(sourceHeader, request)) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN, "요청 출처를 확인할 수 없어요.");
			return;
		}

		filterChain.doFilter(request, response);
	}

	private boolean isSameHost(String originOrReferer, HttpServletRequest request) {
		try {
			String host = new URI(originOrReferer).getHost();
			return host != null && host.equalsIgnoreCase(request.getServerName());
		} catch (URISyntaxException e) {
			// 헤더 값이 URL 형식이 아니면 판단할 수 없다는 뜻 — 막지 않는다(오탐 방지, 위 클래스 주석 참고).
			return true;
		}
	}
}

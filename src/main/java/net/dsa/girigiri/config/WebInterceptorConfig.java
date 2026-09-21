package net.dsa.girigiri.config;

import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.security.AdminScopeInterceptor;
import net.dsa.girigiri.security.LoginRequiredInterceptor;
import net.dsa.girigiri.security.ProfileCompletionInterceptor;
import net.dsa.girigiri.security.ViewModeSyncInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 인터셉터 등록 (문창호, 2026-09-08). 정적 리소스 매핑은 강노은의 WebMvcConfig에 있고,
 * 여기는 인터셉터만 담당해서 파일 소유를 분리한다. WebMvcConfigurer는 여러 개 있어도 다 적용된다.
 *
 * 추가됨 (2026-09-08, 코드 감사) — LoginRequiredInterceptor(security/LoginRequired 참고) 등록.
 * 전체 경로에 걸어도 실제로 막는 건 @LoginRequired가 붙은 메서드뿐이라 안전하다.
 */
@Configuration
@RequiredArgsConstructor
public class WebInterceptorConfig implements WebMvcConfigurer {

	private final ViewModeSyncInterceptor viewModeSyncInterceptor;
	private final LoginRequiredInterceptor loginRequiredInterceptor;
	private final ProfileCompletionInterceptor profileCompletionInterceptor;
	private final AdminScopeInterceptor adminScopeInterceptor;

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		// 추가됨 (2026-09-21) — 운영자(ADMIN)가 유저/점주 화면에 URL로 직접 들어오는 걸 막는다.
		// ViewModeSyncInterceptor와 같은 경로 집합(유저/점주 앱 화면 전체)에 건다 — 여기가
		// "유저·매장이 보는 화면"의 실질적인 정의라 그대로 재사용.
		registry.addInterceptor(adminScopeInterceptor)
				.addPathPatterns("/store/**", "/reservation/**", "/mypage/**", "/user/**", "/app");
		registry.addInterceptor(viewModeSyncInterceptor)
				.addPathPatterns("/store/**", "/reservation/**", "/mypage/**", "/user/**", "/app");
		registry.addInterceptor(loginRequiredInterceptor)
				.addPathPatterns("/**");
		// 부가정보 미입력 사용자 가드 — 정적 리소스·인증 콜백은 제외(제외 안 하면 signup 화면 CSS까지 리다이렉트됨).
		registry.addInterceptor(profileCompletionInterceptor)
				.addPathPatterns("/**")
				.excludePathPatterns("/css/**", "/js/**", "/images/**", "/upload/**",
						"/error", "/error/**", "/favicon.ico", "/oauth2/**", "/login/**");
	}
}

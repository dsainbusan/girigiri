package net.dsa.girigiri.config;

import lombok.RequiredArgsConstructor;
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

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(viewModeSyncInterceptor)
				.addPathPatterns("/store/**", "/reservation/**", "/mypage/**", "/user/**", "/");
		registry.addInterceptor(loginRequiredInterceptor)
				.addPathPatterns("/**");
		// 부가정보 미입력 사용자 가드 — 정적 리소스·인증 콜백은 제외(제외 안 하면 signup 화면 CSS까지 리다이렉트됨).
		registry.addInterceptor(profileCompletionInterceptor)
				.addPathPatterns("/**")
				.excludePathPatterns("/css/**", "/js/**", "/images/**", "/upload/**",
						"/error", "/error/**", "/favicon.ico", "/oauth2/**", "/login/**");
	}
}

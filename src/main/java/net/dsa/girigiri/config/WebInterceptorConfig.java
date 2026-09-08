package net.dsa.girigiri.config;

import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.security.ViewModeSyncInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 인터셉터 등록 (문창호, 2026-09-08). 정적 리소스 매핑은 강노은의 WebMvcConfig에 있고,
 * 여기는 인터셉터만 담당해서 파일 소유를 분리한다. WebMvcConfigurer는 여러 개 있어도 다 적용된다.
 */
@Configuration
@RequiredArgsConstructor
public class WebInterceptorConfig implements WebMvcConfigurer {

	private final ViewModeSyncInterceptor viewModeSyncInterceptor;

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(viewModeSyncInterceptor)
				.addPathPatterns("/store/**");
	}
}

package net.dsa.girigiri.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;

/**
 * 시큐리티 환경설정 클래스
 *
 * TODO(송보미): 카카오/네이버 client 등록 후 .oauth2Login(...) 추가,
 *   AuthenticatedUserDetailsService 빈 추가,
 *   role(불변)/viewMode(가변) 세션 로직 연결 (CLAUDE.md "인증 & 권한 설계" 참고).
 *   그 전까지는 UserDetailsService 빈이 없어 Spring Boot가 기동 시 콘솔에
 *   임시 로그인 계정(user / 생성된 비밀번호)을 로그로 남긴다 — 정상 동작이다.
 */
@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class WebSecurityConfig {

	// 로그인 없이 접근 가능한 경로
	private static final List<String> PUBLIC_URLS = List.of(
			"/"
			, "/favicon.ico"
			, "/css/**"
			, "/js/**"
			, "/images/**"
			, "/auth/loginForm"
			, "/error/**"
			// TODO(송보미): 아래 두 경로는 디자인 통일성 확인용 데모 화면이라 임시로 공개.
			//   실 데이터/권한 연동 시 role 기준 접근 제어로 교체할 것.
			, "/store/**"
			, "/product/**"
			, "/reservation/**"   // TODO(송채현) 로그인 전이라 임시 공개. 로그인 붙으면 로그인한 사용자만 접근하도록 되돌릴 것.
			// TODO(송보미): 개발 참고용 스타일가이드 페이지. 운영 배포 전 dev 프로필 한정 노출 등으로 교체할 것.
			, "/styleguide"
	);

	@Bean
	protected SecurityFilterChain config(HttpSecurity http) throws Exception {
		http
				// 개발 단계: CORS/CSRF/기본 로그인창 비활성화
				.csrf(AbstractHttpConfigurer::disable)
				.cors(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)

				.authorizeHttpRequests(author -> author
						.requestMatchers(PUBLIC_URLS.toArray(String[]::new)).permitAll()
						.anyRequest().authenticated()
				)

				.formLogin(formLogin -> formLogin
						.loginPage("/auth/loginForm")
						.usernameParameter("id")
						.passwordParameter("password")
						.loginProcessingUrl("/auth/login")
						.defaultSuccessUrl("/", true)
						.permitAll()
				)

				.logout(logout -> logout
						.logoutUrl("/auth/logout")
						.invalidateHttpSession(true)
						.deleteCookies("JSESSIONID")
						.logoutSuccessUrl("/")
				);

		return http.build();
	}

	@Bean
	public BCryptPasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}

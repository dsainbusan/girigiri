package net.dsa.girigiri.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenDecoderFactory;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;

/**
 * 시큐리티 환경설정 클래스
 *
 * 변경됨 (2026-08-21) — 왜: dev 브랜치에는 OAuth2 소셜 로그인(구글/카카오/라인)이 아직 merge된 적이
 * 없어(f5549c4의 form-login 스텁 그대로였음) master 브랜치에서 이미 완성된 구현을 그대로 포팅했다.
 * 자체 회원가입 없이 구글/카카오/라인 소셜 로그인만 사용한다 (사장님 계정 포함).
 * 최초 로그인 시 계정 자동 생성은 CustomOAuth2UserService, 세션(userId/role) 반영은
 * OAuth2LoginSuccessHandler가 담당한다.
 *
 * TODO(송보미): role(불변)/viewMode(가변) 세션 로직 중 viewMode·storeId 연결
 *   (사장님 전환 플로우)은 아직 없음 (CLAUDE.md "인증 & 권한 설계" 참고).
 */
@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class WebSecurityConfig {

	private final CustomOAuth2UserService customOAuth2UserService;
	// 추가됨 — 왜: LINE은 openid 스코프가 필수라 구글/카카오와 다른 OIDC 로그인 경로를 타는데,
	// 이 경로는 userService가 아니라 별도의 oidcUserService로 지정해야 우리 회원 자동 생성 로직이 호출된다.
	private final CustomOidcUserService customOidcUserService;
	private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
	// 추가됨 (2026-08-21) — 왜: 이메일+비밀번호 로그인(formLogin) 추가하면서 필요.
	private final EmailLoginSuccessHandler emailLoginSuccessHandler;

	// 로그인 없이 접근 가능한 경로
	private static final List<String> PUBLIC_URLS = List.of(
			"/"
			, "/favicon.ico"
			, "/css/**"
			, "/js/**"
			, "/images/**"
			, "/auth/loginForm"
			, "/error/**"
			// 추가됨 — 왜: 로그인 전 사용자가 접근하는 소셜 로그인 인가 요청(/oauth2/authorization/**)과
			// provider 콜백(/login/oauth2/code/**) 경로. 인증 전 단계라 permitAll 없이는 로그인 자체가 불가능해서 추가.
			, "/oauth2/**"
			, "/login/oauth2/**"
			// TODO(송보미): 아래 두 경로는 디자인 통일성 확인용 데모 화면이라 임시로 공개.
			//   실 데이터/권한 연동 시 role 기준 접근 제어로 교체할 것.
			, "/store/**"
			, "/product/**"
			, "/user/products/**"   // 강노은: 상품 상세 화면. 로그인 붙으면 위 두 줄과 함께 정리할 것.
			, "/user/search"        // 강노은: 검색·필터 결과 화면.
			, "/user/stores/**"     // 강노은: 가게 상세 화면(둘러보기는 로그인 없이).
			, "/reservation/**"   // TODO(송채현) 로그인 전이라 임시 공개. 로그인 붙으면 로그인한 사용자만 접근하도록 되돌릴 것.
			// TODO(송보미): 개발 참고용 스타일가이드 페이지. 운영 배포 전 dev 프로필 한정 노출 등으로 교체할 것.
			, "/styleguide"
			// 추가됨 (2026-08-21) — 왜: 이메일 가입/로그인 화면. 로그인 전 단계라 permitAll 없이는
			// 접근 자체가 막힌다(/auth/emailLogin은 formLogin의 loginProcessingUrl이기도 하다).
			, "/auth/emailSignup"
			, "/auth/emailLogin"
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

				// formLogin을 대체하는 소셜 로그인 설정.
				// userInfoEndpoint: 구글/카카오/라인에서 받은 유저 정보로 최초 로그인 시 계정 자동 생성(가입 겸용) 처리
				// successHandler: 로그인 성공 후 세션에 userId/role 등을 심어주는 역할 (formLogin엔 없던 로직)
				.oauth2Login(oauth2 -> oauth2
						.loginPage("/auth/loginForm")
						.userInfoEndpoint(userInfo -> userInfo
								.userService(customOAuth2UserService)
								// 추가됨 — 왜: LINE(openid 스코프)은 이 oidcUserService를 통해서만
								// 우리 CustomOidcUserService(회원 자동 생성)가 호출된다. userService만 있으면
								// LINE 로그인 시 Spring 기본 OidcUserService가 대신 쓰여서 DB에 계정이 안 만들어지고,
								// OAuth2LoginSuccessHandler가 principal을 캐스팅할 때도 실패한다.
								.oidcUserService(customOidcUserService))
						.successHandler(oAuth2LoginSuccessHandler)
						.failureUrl("/auth/loginForm?error")
				)

				// 추가됨 (2026-08-21) — 왜: 소셜 계정 없이도 가입할 수 있는 이메일+비밀번호 로그인.
				// oauth2Login과 나란히 등록해도 무방 — Spring Security는 한 필터체인에 여러 인증
				// 방식을 동시에 둘 수 있다. UserDetailsService(EmailUserDetailsService)와
				// PasswordEncoder 빈이 있으면 Spring Boot가 DaoAuthenticationProvider를 자동 구성한다.
				.formLogin(form -> form
						.loginPage("/auth/loginForm")
						.loginProcessingUrl("/auth/emailLogin")
						.usernameParameter("email")
						.passwordParameter("password")
						.successHandler(emailLoginSuccessHandler)
						.failureUrl("/auth/emailLogin?error")
				)

				.logout(logout -> logout
						.logoutUrl("/auth/logout")
						.invalidateHttpSession(true)
						.deleteCookies("JSESSIONID")
						.logoutSuccessUrl("/")
				);

		return http.build();
	}

	// 추가됨 (2026-08-21) — 왜: 이메일 회원가입 시 비밀번호 암호화 저장(AuthController#emailSignup),
	// 로그인 시 검증(EmailUserDetailsService 기반 DaoAuthenticationProvider) 양쪽에서 필요.
	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	// 추가됨 — 왜: LINE 로그인 시도 중 "[invalid_id_token] Signed JWT rejected:
	// Another algorithm expected, or no matching key(s) found" 에러 발생. LINE 채널이 ID 토큰을
	// RS256(공개키 서명)이 아니라 HS256(채널 시크릿을 대칭키로 쓰는 방식)으로 발급하고 있는데,
	// Spring 기본 OidcIdTokenDecoderFactory는 jwk-set-uri 기반 RS256 검증만 시도해서 실패했다.
	// registrationId가 "line"이면 HS256으로 검증하도록 알고리즘 결정 로직을 오버라이드한다.
	@Bean
	public JwtDecoderFactory<ClientRegistration> idTokenDecoderFactory() {
		OidcIdTokenDecoderFactory idTokenDecoderFactory = new OidcIdTokenDecoderFactory();
		idTokenDecoderFactory.setJwsAlgorithmResolver(clientRegistration ->
				"line".equals(clientRegistration.getRegistrationId()) ? MacAlgorithm.HS256 : SignatureAlgorithm.RS256);
		return idTokenDecoderFactory;
	}
}

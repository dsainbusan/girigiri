package net.dsa.girigiri.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 추가됨 (2026-09-23, 코드 감사) — 원래 WebSecurityConfig 안에 있던 PasswordEncoder 빈을 분리했다.
 * 왜: OAuth2LoginSuccessHandler가 계정 연동 병합(AuthService)을 쓰게 되면서
 * WebSecurityConfig(OAuth2LoginSuccessHandler 생성자 주입) → OAuth2LoginSuccessHandler(AuthService)
 * → AuthService(PasswordEncoder) → PasswordEncoder 빈이 WebSecurityConfig 안에 있어 그 인스턴스화가
 * 다시 필요 → BeanCurrentlyInCreationException(순환 참조)이 났다. PasswordEncoder처럼 다른 빈들이
 * 널리 의존하는 저수준 빈은 별도 설정 클래스로 빼서 이런 순환을 원천적으로 막는다.
 */
@Configuration
public class PasswordEncoderConfig {

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}

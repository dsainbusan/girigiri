package net.dsa.girigiri.security;

import lombok.Getter;
import net.dsa.girigiri.domain.entity.UserEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * 추가됨 (2026-08-21) — 왜: 이메일+비밀번호 로그인 인증 주체. OAuth2UserPrincipal/OidcUserPrincipal과
 * 같은 역할(UserEntity 보관 + role 기반 권한 부여)이지만, Spring Security의 formLogin 경로는
 * UserDetails 구현체를 요구해서 별도로 만든다. UserPrincipal을 같이 구현해서 OAuth2LoginSuccessHandler와
 * 동일한 방식(AuthSessionInitializer)으로 세션을 채울 수 있게 한다.
 */
@Getter
public class EmailUserPrincipal implements UserDetails, UserPrincipal {

	private final UserEntity user;

	public EmailUserPrincipal(UserEntity user) {
		this.user = user;
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole()));
	}

	@Override
	public String getPassword() {
		return user.getPassword();
	}

	@Override
	public String getUsername() {
		return user.getEmail();
	}
}

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

	// 추가됨 (2026-09-08) — 왜: 코드 감사에서 "회원 정지"가 실제로 로그인을 막지 않는다는 게
	// 발견됐다(SuperAdminMemberService.suspend()가 status를 SUSPENDED로 바꾸긴 하는데, 로그인
	// 경로 어디서도 이 값을 안 읽고 있었음). DaoAuthenticationProvider는 인증 성공 판정 전에
	// isEnabled()를 확인해서 false면 DisabledException을 던지고 formLogin의 failureUrl로 보낸다 —
	// Spring Security의 표준 훅이라 여기 하나만 고치면 이메일 로그인 경로는 해결된다.
	@Override
	public boolean isEnabled() {
		return !UserEntity.STATUS_SUSPENDED.equals(user.getStatus());
	}
}

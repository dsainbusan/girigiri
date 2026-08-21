package net.dsa.girigiri.security;

import lombok.Getter;
import net.dsa.girigiri.domain.entity.UserEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 추가됨 (2026-08-20) — 왜: LINE 로그인은 openid 스코프가 필수라 Spring Security가 OIDC 로그인
 * 경로(oidcUserService)로 처리하는데, 이 경로는 반환값이 OAuth2User가 아니라 OidcUser여야 한다.
 * OAuth2UserPrincipal과 거의 같은 역할이지만(UserEntity 보관 + role 기반 권한 부여), OidcUser
 * 인터페이스가 요구하는 getIdToken()/getUserInfo()/getClaims()는 Spring이 이미 검증까지 마치고
 * 만들어준 원본 OidcUser(delegate)에 그대로 위임한다.
 */
@Getter
public class OidcUserPrincipal implements OidcUser, UserPrincipal {

	private final UserEntity user;
	private final OidcUser delegate;

	public OidcUserPrincipal(UserEntity user, OidcUser delegate) {
		this.user = user;
		this.delegate = delegate;
	}

	@Override
	public Map<String, Object> getClaims() {
		return delegate.getClaims();
	}

	@Override
	public OidcUserInfo getUserInfo() {
		return delegate.getUserInfo();
	}

	@Override
	public OidcIdToken getIdToken() {
		return delegate.getIdToken();
	}

	@Override
	public Map<String, Object> getAttributes() {
		return delegate.getAttributes();
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole()));
	}

	@Override
	public String getName() {
		return delegate.getName();
	}
}

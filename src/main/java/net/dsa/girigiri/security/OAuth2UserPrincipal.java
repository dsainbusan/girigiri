package net.dsa.girigiri.security;

import lombok.Getter;
import net.dsa.girigiri.domain.entity.UserEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 로그인 세션에 올라가는 인증 주체 — 구글/카카오처럼 openid 스코프가 없는 일반 OAuth2 로그인 경로용.
 * CLAUDE.md 세션 구조({userId, role, viewMode, storeId}) 중 userId/role은 user에서 바로 꺼내 쓰고,
 * viewMode/storeId는 로그인 성공 핸들러(OAuth2LoginSuccessHandler)에서 세션에 별도 저장한다.
 *
 * 변경됨 (2026-08-20) — 왜: LINE(OIDC 경로)은 별도의 OidcUserPrincipal을 쓰는데,
 * OAuth2LoginSuccessHandler가 로그인 방식에 상관없이 UserEntity만 꺼낼 수 있도록 UserPrincipal 구현.
 */
@Getter
public class OAuth2UserPrincipal implements OAuth2User, UserPrincipal {

	private final UserEntity user;
	private final Map<String, Object> attributes;
	private final String nameAttributeKey;

	public OAuth2UserPrincipal(UserEntity user, Map<String, Object> attributes, String nameAttributeKey) {
		this.user = user;
		this.attributes = attributes;
		this.nameAttributeKey = nameAttributeKey;
	}

	@Override
	public Map<String, Object> getAttributes() {
		return attributes;
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole()));
	}

	@Override
	public String getName() {
		return String.valueOf(attributes.get(nameAttributeKey));
	}
}

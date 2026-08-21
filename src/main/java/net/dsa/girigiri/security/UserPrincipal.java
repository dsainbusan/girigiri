package net.dsa.girigiri.security;

import net.dsa.girigiri.domain.entity.UserEntity;

/**
 * 추가됨 (2026-08-20) — 왜: 구글/카카오는 openid 스코프가 없어 일반 OAuth2 로그인 경로
 * (OAuth2UserPrincipal)를 타지만, LINE은 openid 스코프가 필수라 Spring Security가 무조건
 * OIDC 로그인 경로(OidcUserPrincipal)로 처리한다. 두 경로의 principal 타입이 서로 달라서
 * OAuth2LoginSuccessHandler가 구체 타입으로 캐스팅하면 LINE 로그인 시 ClassCastException이
 * 난다 — 공통 인터페이스로 뽑아서 principal 타입에 상관없이 UserEntity만 꺼내 쓰게 한다.
 */
public interface UserPrincipal {

	UserEntity getUser();
}

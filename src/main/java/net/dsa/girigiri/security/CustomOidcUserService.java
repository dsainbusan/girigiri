package net.dsa.girigiri.security;

import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.entity.UserEntity;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

/**
 * 추가됨 (2026-08-20) — 왜: LINE 로그인 시 호출된다. LINE 등록에 openid 스코프가 있어서
 * Spring Security가 CustomOAuth2UserService(일반 OAuth2 경로) 대신 이 서비스를 자동으로 호출한다
 * (등록 방법은 WebSecurityConfig의 oauth2Login().userInfoEndpoint().oidcUserService() 참고).
 * 계정 자동 생성 로직 자체는 CustomOAuth2UserService와 동일해서 SocialUserProvisioningService를
 * 공유한다 — 여기서 새로 만든 건 "OidcUser를 반환해야 한다"는 타입 요구사항 처리(OidcUserPrincipal)뿐이다.
 */
@Service
@RequiredArgsConstructor
public class CustomOidcUserService extends OidcUserService {

	private final SocialUserProvisioningService userProvisioningService;

	@Override
	public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
		OidcUser oidcUser = super.loadUser(userRequest);

		String registrationId = userRequest.getClientRegistration().getRegistrationId(); // line

		OAuthAttributes attributes = OAuthAttributes.of(registrationId, oidcUser.getAttributes());
		UserEntity user = userProvisioningService.findOrCreate(
				attributes.getProvider(), attributes.getOauthId(), attributes.getNickname(), attributes.getEmail());

		return new OidcUserPrincipal(user, oidcUser);
	}
}

package net.dsa.girigiri.security;

import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.entity.UserEntity;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

/**
 * 구글/카카오 로그인 시 호출된다 (openid 스코프가 없는 일반 OAuth2 로그인 경로).
 * LINE은 openid 스코프가 필수라 Spring Security가 이 서비스가 아니라 CustomOidcUserService를
 * 대신 호출한다 — 변경됨 (2026-08-20): 계정 자동 생성 로직은 두 서비스가 공유하도록
 * SocialUserProvisioningService로 뽑아냈다.
 *
 * 이 프로젝트는 자체 회원가입을 제공하지 않으므로, (oauthProvider, oauthId)로 기존 계정을
 * 찾고 없으면 role=USER로 즉시 자동 생성한다 — 소셜 로그인 자체가 곧 가입이다.
 *
 * 변경됨 (2026-08-21) — 왜: 원래는 role=PENDING으로 만든 뒤 /auth/roleSelect에서 유저가 USER/ADMIN을
 * 직접 골랐는데, 기획이 바뀌어 가입 시 무조건 role=USER로 확정한다. 점주(OWNER)는 별도 신청/승인 폼
 * (운영자가 승인)으로만 될 수 있다.
 *
 * TODO(담당 미정): 점주(OWNER) 승인 시 가게(storeId) 연결·등록 플로우는 아직 없음 —
 *   점주 신청/승인 폼 구현 후 이어붙인다.
 */
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

	private final SocialUserProvisioningService userProvisioningService;

	@Override
	public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
		OAuth2User oAuth2User = super.loadUser(userRequest);

		String registrationId = userRequest.getClientRegistration().getRegistrationId(); // google / kakao
		String userNameAttributeKey = userRequest.getClientRegistration()
				.getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName();

		OAuthAttributes attributes = OAuthAttributes.of(registrationId, oAuth2User.getAttributes());
		UserEntity user = userProvisioningService.findOrCreate(
				attributes.getProvider(), attributes.getOauthId(), attributes.getNickname(), attributes.getEmail());

		return new OAuth2UserPrincipal(user, oAuth2User.getAttributes(), userNameAttributeKey);
	}
}

package net.dsa.girigiri.security;

import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.entity.UserEntity;
import net.dsa.girigiri.repository.UserRepository;
import org.springframework.stereotype.Component;

/**
 * 추가됨 (2026-08-20) — 왜: (oauthProvider, oauthId)로 기존 계정을 찾고 없으면 즉시 자동 생성하는 로직.
 * 원래 CustomOAuth2UserService 안에 있었는데, LINE(OIDC 경로)을 처리할 CustomOidcUserService가
 * 똑같은 로직을 또 필요로 해서 공용으로 뽑아냈다 — 두 서비스가 이 로직을 각자 복붙해서 들고 있으면
 * 나중에 하나만 고치고 하나는 안 고치는 실수가 나기 쉽다.
 *
 * 변경됨 (2026-08-21) — 왜: 원래는 role=PENDING(역할 미선택)으로 만들고 /auth/roleSelect에서 유저가
 * 직접 USER/ADMIN을 고르게 했는데, 기획이 바뀌어 가입 시 무조건 role=USER로 확정한다. 점주(OWNER)는
 * 셀프 선택이 아니라 별도 신청/승인 폼(운영자가 승인)으로만 될 수 있음 — 그 폼은 아직 미구현.
 */
@Component
@RequiredArgsConstructor
public class SocialUserProvisioningService {

	private final UserRepository userRepository;

	// 변경됨 (2026-08-21) — 왜: 회원가입 완료 화면(authView/signup)의 "OO 계정으로 시작해요" 박스에
	// 이메일을 마스킹해서 보여주려면 최초 생성 시점에 email을 같이 저장해둬야 한다.
	public UserEntity findOrCreate(String provider, String oauthId, String nickname, String email) {
		return userRepository.findByOauthProviderAndOauthId(provider, oauthId)
				.orElseGet(() -> userRepository.save(
						UserEntity.builder()
								.oauthProvider(provider)
								.oauthId(oauthId)
								.nickname(nickname)
								.email(email)
								.role(UserEntity.ROLE_USER)
								.build()
				));
	}
}

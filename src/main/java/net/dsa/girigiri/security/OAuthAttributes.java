package net.dsa.girigiri.security;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

/**
 * 구글/카카오/라인마다 다른 사용자 정보 응답 형태를 공통 필드로 정규화한다.
 */
@Getter
public class OAuthAttributes {

	private final Map<String, Object> attributes;
	private final String provider;
	private final String oauthId;
	private final String nickname;
	private final String email;

	@Builder
	private OAuthAttributes(Map<String, Object> attributes, String provider, String oauthId, String nickname, String email) {
		this.attributes = attributes;
		this.provider = provider;
		this.oauthId = oauthId;
		this.nickname = nickname;
		this.email = email;
	}

	public static OAuthAttributes of(String registrationId, Map<String, Object> attributes) {
		return switch (registrationId) {
			case "google" -> ofGoogle(attributes);
			case "kakao" -> ofKakao(attributes);
			case "line" -> ofLine(attributes);
			default -> throw new IllegalArgumentException("지원하지 않는 소셜 로그인입니다: " + registrationId);
		};
	}

	private static OAuthAttributes ofGoogle(Map<String, Object> attributes) {
		return OAuthAttributes.builder()
				.provider("google")
				.oauthId(String.valueOf(attributes.get("sub")))
				.nickname((String) attributes.get("name"))
				.email((String) attributes.get("email"))
				.attributes(attributes)
				.build();
	}

	@SuppressWarnings("unchecked")
	private static OAuthAttributes ofKakao(Map<String, Object> attributes) {
		Map<String, Object> kakaoAccount = (Map<String, Object>) attributes.get("kakao_account");
		Map<String, Object> profile = kakaoAccount != null ? (Map<String, Object>) kakaoAccount.get("profile") : null;

		return OAuthAttributes.builder()
				.provider("kakao")
				.oauthId(String.valueOf(attributes.get("id")))
				.nickname(profile != null ? (String) profile.get("nickname") : null)
				.email(kakaoAccount != null ? (String) kakaoAccount.get("email") : null)
				.attributes(attributes)
				.build();
	}

	private static OAuthAttributes ofLine(Map<String, Object> attributes) {
		return OAuthAttributes.builder()
				.provider("line")
				.oauthId(String.valueOf(attributes.get("sub")))
				.nickname((String) attributes.get("name"))
				.email((String) attributes.get("email"))
				.attributes(attributes)
				.build();
	}
}

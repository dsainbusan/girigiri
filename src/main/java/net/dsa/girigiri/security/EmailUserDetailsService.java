package net.dsa.girigiri.security;

import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.entity.UserEntity;
import net.dsa.girigiri.repository.SocialAccountRepository;
import net.dsa.girigiri.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * 추가됨 (2026-08-21) — 왜: 이메일 로그인 시 Spring Security formLogin이 호출한다. 이메일 가입
 * (AuthController#emailSignup)이 oauthProvider="email", oauthId=이메일 규칙으로 계정을 만들어서
 * 여기서도 같은 규칙(findByOauthProviderAndOauthId)으로 조회한다.
 *
 * 변경됨 (2026-09-22) — 왜: 1:N 소셜 계정 연동 체계에 맞춰 user_social_accounts 테이블을 먼저 조회하고,
 * 하위 호환을 위해 users 테이블 fallback을 지원한다.
 */
@Service
@RequiredArgsConstructor
public class EmailUserDetailsService implements UserDetailsService {

	private final UserRepository userRepository;
	private final SocialAccountRepository socialAccountRepository;

	@Override
	public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
		// 추가됨 (2026-09-24, 코드 감사) — 예전엔 SocialAccountEntity::getUser()를 바로 썼는데,
		// user는 @ManyToOne(LAZY)라 이 시점엔 아직 필드가 안 채워진 Hibernate 프록시가 돌아온다.
		// /auth/emailLogin은 Spring Security 필터가 컨트롤러까지 가지 않고 자체 처리해서
		// JPA open-in-view의 세션 유지 혜택을 못 받는데, 그 프록시를 나중에 EmailUserPrincipal#isEnabled()에서
		// 여는 순간(이미 세션이 닫힌 뒤) LazyInitializationException("no session")이 났다. getId()는
		// 프록시에서도 DB 접근 없이 바로 읽히니(생성할 때부터 알고 있는 값) 안전하게 쓰고,
		// 실제 엔티티는 findById로 완전히 채워서 가져온다.
		UserEntity user = socialAccountRepository.findByProviderAndProviderId("email", email)
				.map(sa -> sa.getUser().getId())
				.flatMap(userRepository::findById)
				.or(() -> userRepository.findByOauthProviderAndOauthId("email", email))
				.orElseThrow(() -> new UsernameNotFoundException("가입되지 않은 이메일입니다: " + email));
		return new EmailUserPrincipal(user);
	}
}

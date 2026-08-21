package net.dsa.girigiri.security;

import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.entity.UserEntity;
import net.dsa.girigiri.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * 추가됨 (2026-08-21) — 왜: 이메일 로그인 시 Spring Security formLogin이 호출한다. 이메일 가입
 * (AuthController#emailSignup)이 oauthProvider="email", oauthId=이메일 규칙으로 계정을 만들어서
 * 여기서도 같은 규칙(findByOauthProviderAndOauthId)으로 조회한다.
 */
@Service
@RequiredArgsConstructor
public class EmailUserDetailsService implements UserDetailsService {

	private final UserRepository userRepository;

	@Override
	public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
		UserEntity user = userRepository.findByOauthProviderAndOauthId("email", email)
				.orElseThrow(() -> new UsernameNotFoundException("가입되지 않은 이메일입니다: " + email));
		return new EmailUserPrincipal(user);
	}
}

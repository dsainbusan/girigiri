package net.dsa.girigiri;

import net.dsa.girigiri.controller.AuthController;
import net.dsa.girigiri.controller.MypageController;
import net.dsa.girigiri.domain.entity.SocialAccountEntity;
import net.dsa.girigiri.domain.entity.UserEntity;
import net.dsa.girigiri.repository.SocialAccountRepository;
import net.dsa.girigiri.repository.StoreRepository;
import net.dsa.girigiri.repository.UserRepository;
import net.dsa.girigiri.security.AuthAttemptLimiter;
import net.dsa.girigiri.security.OAuth2LoginSuccessHandler;
import net.dsa.girigiri.security.SocialUserProvisioningService;
import net.dsa.girigiri.security.UserPrincipal;
import net.dsa.girigiri.service.AuthService;
import net.dsa.girigiri.service.LedgerService;
import net.dsa.girigiri.service.MypageService;
import net.dsa.girigiri.service.ReservationService;
import net.dsa.girigiri.service.StoreAccessService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 1:N 멀티 소셜 계정 연동(Account Linking) 체계 단위 테스트
 * - SocialUserProvisioningService: 소셜 테이블 우선 조회 및 자동 프로비저닝
 * - AuthService: 휴대폰 중복 감지(기존 가입수단 조회) 및 소셜 계정 연동/병합
 * - AuthController: /auth/check-phone 비동기 API 및 /auth/link-account 처리
 */
@ExtendWith(MockitoExtension.class)
class SocialAccountLinkTest {

	@Nested
	@DisplayName("SocialUserProvisioningService 테스트")
	class ProvisioningTests {
		@Mock
		private UserRepository userRepository;

		@Mock
		private SocialAccountRepository socialAccountRepository;

		@InjectMocks
		private SocialUserProvisioningService provisioningService;

		@Test
		@DisplayName("1. 소셜 테이블에 이미 연동된 계정이 있으면 해당 유저를 즉시 반환한다")
		void returnsUserFromSocialAccountRepository() {
			UserEntity user = UserEntity.builder().id(10L).nickname("홍길동").build();
			SocialAccountEntity social = SocialAccountEntity.builder().user(user).provider("kakao").providerId("kakao_123").build();

			when(socialAccountRepository.findByProviderAndProviderId("kakao", "kakao_123"))
					.thenReturn(Optional.of(social));

			UserEntity result = provisioningService.findOrCreate("kakao", "kakao_123", "홍길동", "test@kakao.com");

			assertEquals(10L, result.getId());
			verify(userRepository, never()).findByOauthProviderAndOauthId(any(), any());
			verify(userRepository, never()).save(any());
		}

		@Test
		@DisplayName("2. 소셜 테이블에 없지만 users에 존재하는 레거시 계정이면 소셜 테이블에 자동 등록하고 반환한다")
		void autoMigratesLegacyUser() {
			UserEntity legacyUser = UserEntity.builder().id(20L).oauthProvider("naver").oauthId("naver_456").build();

			when(socialAccountRepository.findByProviderAndProviderId("naver", "naver_456"))
					.thenReturn(Optional.empty());
			when(userRepository.findByOauthProviderAndOauthId("naver", "naver_456"))
					.thenReturn(Optional.of(legacyUser));

			UserEntity result = provisioningService.findOrCreate("naver", "naver_456", "네이버유저", "test@naver.com");

			assertEquals(20L, result.getId());
			verify(socialAccountRepository, times(1)).save(any(SocialAccountEntity.class));
		}

		@Test
		@DisplayName("3. 완전히 새로운 소셜 회원이면 users와 user_social_accounts를 모두 생성한다")
		void createsNewUserAndSocialAccount() {
			UserEntity newUser = UserEntity.builder().id(30L).oauthProvider("google").oauthId("google_789").build();

			when(socialAccountRepository.findByProviderAndProviderId("google", "google_789"))
					.thenReturn(Optional.empty());
			when(userRepository.findByOauthProviderAndOauthId("google", "google_789"))
					.thenReturn(Optional.empty());
			when(userRepository.save(any(UserEntity.class))).thenReturn(newUser);

			UserEntity result = provisioningService.findOrCreate("google", "google_789", "구글유저", "test@gmail.com");

			assertEquals(30L, result.getId());
			verify(userRepository, times(1)).save(any(UserEntity.class));
			verify(socialAccountRepository, times(1)).save(any(SocialAccountEntity.class));
		}

		@Test
		@DisplayName("4. 정지된 계정이면 OAuth2AuthenticationException 예외를 던진다")
		void throwsExceptionIfSuspended() {
			UserEntity suspendedUser = UserEntity.builder().id(40L).status(UserEntity.STATUS_SUSPENDED).build();
			SocialAccountEntity social = SocialAccountEntity.builder().user(suspendedUser).provider("line").providerId("line_111").build();

			when(socialAccountRepository.findByProviderAndProviderId("line", "line_111"))
					.thenReturn(Optional.of(social));

			assertThrows(OAuth2AuthenticationException.class, () ->
					provisioningService.findOrCreate("line", "line_111", "라인유저", "test@line.me"));
		}
	}

	@Nested
	@DisplayName("AuthService 계정 연동 테스트")
	class AuthServiceLinkTests {
		@Mock
		private UserRepository userRepository;
		@Mock
		private StoreRepository storeRepository;
		@Mock
		private PasswordEncoder passwordEncoder;
		@Mock
		private SocialAccountRepository socialAccountRepository;

		@InjectMocks
		private AuthService authService;

		@Test
		@DisplayName("1. 휴대폰 번호로 기존 가입 정보 및 소셜 제공자 한글명(네이버 등)을 올바르게 조회한다")
		void findExistingAccountByPhoneReturnsCorrectInfo() {
			UserEntity existing = UserEntity.builder()
					.id(100L)
					.oauthProvider("naver")
					.email("sample@naver.com")
					.phone("010-1234-5678")
					.build();

			when(userRepository.findByPhone("010-1234-5678")).thenReturn(Optional.of(existing));

			Optional<AuthService.ExistingAccountInfo> infoOpt = authService.findExistingAccountByPhone("01012345678", 200L);

			assertTrue(infoOpt.isPresent());
			AuthService.ExistingAccountInfo info = infoOpt.get();
			assertEquals(100L, info.userId());
			assertEquals("naver", info.primaryProvider());
			assertEquals("네이버", info.providerDisplayName());
			assertEquals("samp***@naver.com", info.maskedEmail());
		}

		@Test
		@DisplayName("2. 동일한 사용자가 자신의 번호를 조회하는 경우 중복 충돌로 간주하지 않는다")
		void findExistingAccountByPhoneExcludesCurrentUserId() {
			UserEntity existing = UserEntity.builder()
					.id(100L)
					.oauthProvider("naver")
					.phone("010-1234-5678")
					.build();

			when(userRepository.findByPhone("010-1234-5678")).thenReturn(Optional.of(existing));

			Optional<AuthService.ExistingAccountInfo> infoOpt = authService.findExistingAccountByPhone("010-1234-5678", 100L);

			assertTrue(infoOpt.isEmpty());
		}

		@Test
		@DisplayName("3. linkSocialAccountAfterReauth는 임시 계정의 소셜 정보를 기존 회원에게 연동하고 임시 계정을 삭제한다")
		void linkSocialAccountAfterReauthSuccessfullyMerges() {
			UserEntity pendingUser = UserEntity.builder()
					.id(200L)
					.oauthProvider("kakao")
					.oauthId("kakao_new")
					.profileCompleted(false)
					.build();

			UserEntity targetUser = UserEntity.builder()
					.id(100L)
					.oauthProvider("naver")
					.oauthId("naver_old")
					.phone("010-1234-5678")
					.profileCompleted(true)
					.build();

			when(userRepository.findById(200L)).thenReturn(Optional.of(pendingUser));
			when(userRepository.findById(100L)).thenReturn(Optional.of(targetUser));

			SocialAccountEntity pendingSocial = SocialAccountEntity.builder()
					.id(1L)
					.user(pendingUser)
					.provider("kakao")
					.providerId("kakao_new")
					.build();
			when(socialAccountRepository.findAllByUserId(200L)).thenReturn(List.of(pendingSocial));
			when(socialAccountRepository.existsByProviderAndProviderId("naver", "naver_old")).thenReturn(true);
			when(socialAccountRepository.existsByProviderAndProviderId("kakao", "kakao_new")).thenReturn(false);

			UserEntity mergedUser = authService.linkSocialAccountAfterReauth(200L, 100L);

			assertEquals(100L, mergedUser.getId());
			verify(userRepository, times(1)).delete(pendingUser);
			verify(userRepository, times(1)).flush();
			verify(socialAccountRepository, times(1)).save(any(SocialAccountEntity.class));
		}

		@Test
		@DisplayName("4. 동일한 소셜 제공자(예: 구글 기존 계정에 다른 구글 계정)를 연동할 때도 정상적으로 병합된다")
		void linkSocialAccountWithSameProviderDifferentAccounts() {
			UserEntity targetUser = UserEntity.builder()
					.id(100L)
					.oauthProvider("google")
					.oauthId("google_account_1")
					.phone("010-1234-5678")
					.profileCompleted(true)
					.build();

			UserEntity pendingUser = UserEntity.builder()
					.id(200L)
					.oauthProvider("google")
					.oauthId("google_account_2")
					.profileCompleted(false)
					.build();

			when(userRepository.findById(200L)).thenReturn(Optional.of(pendingUser));
			when(userRepository.findById(100L)).thenReturn(Optional.of(targetUser));

			SocialAccountEntity pendingSocial = SocialAccountEntity.builder()
					.id(2L)
					.user(pendingUser)
					.provider("google")
					.providerId("google_account_2")
					.build();
			when(socialAccountRepository.findAllByUserId(200L)).thenReturn(List.of(pendingSocial));
			when(socialAccountRepository.existsByProviderAndProviderId("google", "google_account_1")).thenReturn(true);
			when(socialAccountRepository.existsByProviderAndProviderId("google", "google_account_2")).thenReturn(false);

			UserEntity mergedUser = authService.linkSocialAccountAfterReauth(200L, 100L);

			assertEquals(100L, mergedUser.getId());
			verify(userRepository, times(1)).delete(pendingUser);
			verify(socialAccountRepository, times(1)).save(argThat(sa ->
					sa.getProvider().equals("google") && sa.getProviderId().equals("google_account_2") && sa.getUser().getId().equals(100L)));
		}

		@Test
		@DisplayName("4-1. 이미 프로필이 완료된 계정은 연동 시도로 임시 계정 삭제되지 않도록 방어한다")
		void linkSocialAccountThrowsIfPendingUserCompleted() {
			UserEntity completedUser = UserEntity.builder()
					.id(200L)
					.profileCompleted(true)
					.build();

			when(userRepository.findById(200L)).thenReturn(Optional.of(completedUser));

			assertThrows(IllegalStateException.class, () ->
					authService.linkSocialAccountAfterReauth(200L, 100L));
		}

		@Test
		@DisplayName("4-2. (보안) 비밀번호 재인증 경로는 대상 계정의 비밀번호가 일치할 때만 병합한다")
		void linkSocialAccountWithPasswordSucceedsOnMatch() {
			UserEntity pendingUser = UserEntity.builder()
					.id(200L).oauthProvider("kakao").oauthId("kakao_new").profileCompleted(false).build();
			UserEntity targetUser = UserEntity.builder()
					.id(100L).oauthProvider("email").oauthId("target@example.com")
					.email("target@example.com").phone("010-1234-5678").password("ENC_PW").profileCompleted(true).build();

			when(userRepository.findById(200L)).thenReturn(Optional.of(pendingUser));
			when(userRepository.findByPhone("010-1234-5678")).thenReturn(Optional.of(targetUser));
			when(passwordEncoder.matches("correct-pw", "ENC_PW")).thenReturn(true);
			when(socialAccountRepository.findAllByUserId(200L)).thenReturn(List.of());

			UserEntity mergedUser = authService.linkSocialAccountWithPassword(200L, "010-1234-5678", "correct-pw");

			assertEquals(100L, mergedUser.getId());
			verify(userRepository, times(1)).delete(pendingUser);
		}

		@Test
		@DisplayName("4-3. (보안) 비밀번호가 틀리면 병합하지 않고 예외를 던진다 — 전화번호만으로는 연동될 수 없다")
		void linkSocialAccountWithPasswordFailsOnWrongPassword() {
			UserEntity pendingUser = UserEntity.builder()
					.id(200L).oauthProvider("kakao").oauthId("kakao_new").profileCompleted(false).build();
			UserEntity targetUser = UserEntity.builder()
					.id(100L).oauthProvider("email").email("target@example.com")
					.phone("010-1234-5678").password("ENC_PW").profileCompleted(true).build();

			when(userRepository.findById(200L)).thenReturn(Optional.of(pendingUser));
			when(userRepository.findByPhone("010-1234-5678")).thenReturn(Optional.of(targetUser));
			when(passwordEncoder.matches("wrong-pw", "ENC_PW")).thenReturn(false);

			assertThrows(IllegalArgumentException.class, () ->
					authService.linkSocialAccountWithPassword(200L, "010-1234-5678", "wrong-pw"));

			verify(userRepository, never()).delete(any());
		}

		@Test
		@DisplayName("4-4. (보안) 대상이 소셜 전용 계정(비밀번호 없음)이면 비밀번호 경로 자체를 거부한다")
		void linkSocialAccountWithPasswordRejectsSocialOnlyTarget() {
			UserEntity pendingUser = UserEntity.builder()
					.id(200L).oauthProvider("kakao").oauthId("kakao_new").profileCompleted(false).build();
			UserEntity targetUser = UserEntity.builder()
					.id(100L).oauthProvider("naver").oauthId("naver_old")
					.phone("010-1234-5678").profileCompleted(true).build();

			when(userRepository.findById(200L)).thenReturn(Optional.of(pendingUser));
			when(userRepository.findByPhone("010-1234-5678")).thenReturn(Optional.of(targetUser));

			assertThrows(IllegalStateException.class, () ->
					authService.linkSocialAccountWithPassword(200L, "010-1234-5678", "아무거나"));

			verify(userRepository, never()).delete(any());
		}

		@Test
		@DisplayName("4-5. (보안) 이메일이 다른 provider 계정에 이미 있으면 그 계정 정보를 반환한다 — 새 계정 생성 방지용")
		void findExistingAccountByEmailReturnsInfoForOtherProvider() {
			UserEntity googleUser = UserEntity.builder()
					.id(100L).oauthProvider("google").email("dsy12344@gmail.com").build();
			when(userRepository.findFirstByEmail("dsy12344@gmail.com")).thenReturn(Optional.of(googleUser));

			var info = authService.findExistingAccountByEmail("dsy12344@gmail.com");

			assertTrue(info.isPresent());
			assertEquals(100L, info.get().userId());
			assertEquals("google", info.get().primaryProvider());
		}

		@Test
		@DisplayName("4-6. 이메일이 이미 email provider 계정에 있으면(=일반 중복) findExistingAccountByEmail은 감지하지 않는다")
		void findExistingAccountByEmailIgnoresSameProvider() {
			UserEntity emailUser = UserEntity.builder()
					.id(200L).oauthProvider("email").email("a@a.com").build();
			when(userRepository.findFirstByEmail("a@a.com")).thenReturn(Optional.of(emailUser));

			var info = authService.findExistingAccountByEmail("a@a.com");

			assertTrue(info.isEmpty());
		}

		@Test
		@DisplayName("4-7. linkEmailPasswordToAccount는 소셜 계정에 이메일+비밀번호 로그인 수단을 추가한다")
		void linkEmailPasswordToAccountAddsLoginMethod() {
			UserEntity googleUser = UserEntity.builder()
					.id(100L).oauthProvider("google").oauthId("g1").email("dsy12344@gmail.com").build();
			when(userRepository.findById(100L)).thenReturn(Optional.of(googleUser));
			when(socialAccountRepository.existsByProviderAndProviderId("email", "dsy12344@gmail.com")).thenReturn(false);

			UserEntity result = authService.linkEmailPasswordToAccount(100L, "dsy12344@gmail.com", "ENCODED_PW");

			assertEquals("ENCODED_PW", result.getPassword());
			verify(socialAccountRepository, times(1)).save(argThat(sa ->
					sa.getProvider().equals("email") && sa.getProviderId().equals("dsy12344@gmail.com")));
		}

		@Test
		@DisplayName("5. 소셜 계정이 2개 이상이면 canUnlinkSocialAccount가 true를 반환한다")
		void canUnlinkReturnsTrueWhenMultipleMethods() {
			UserEntity user = UserEntity.builder().id(100L).build();
			when(userRepository.findById(100L)).thenReturn(Optional.of(user));
			when(socialAccountRepository.findAllByUserId(100L)).thenReturn(List.of(
					SocialAccountEntity.builder().id(1L).provider("google").providerId("g1").build(),
					SocialAccountEntity.builder().id(2L).provider("kakao").providerId("k1").build()
			));

			assertTrue(authService.canUnlinkSocialAccount(100L));
		}

		@Test
		@DisplayName("6. 소셜 계정이 1개뿐이고 비밀번호가 없으면 canUnlinkSocialAccount가 false를 반환한다")
		void canUnlinkReturnsFalseWhenOnlyOneMethod() {
			UserEntity user = UserEntity.builder().id(100L).oauthProvider("google").build();
			when(userRepository.findById(100L)).thenReturn(Optional.of(user));
			when(socialAccountRepository.findAllByUserId(100L)).thenReturn(List.of(
					SocialAccountEntity.builder().id(1L).provider("google").providerId("g1").build()
			));

			assertFalse(authService.canUnlinkSocialAccount(100L));
		}

		@Test
		@DisplayName("7. 소셜 계정이 1개뿐일 때 연동 해제 시도 시 IllegalStateException이 발생한다")
		void unlinkSocialThrowsWhenOnlyOneMethod() {
			UserEntity user = UserEntity.builder().id(100L).oauthProvider("google").build();
			when(userRepository.findById(100L)).thenReturn(Optional.of(user));
			when(socialAccountRepository.findAllByUserId(100L)).thenReturn(List.of(
					SocialAccountEntity.builder().id(1L).provider("google").providerId("g1").build()
			));

			assertThrows(IllegalStateException.class, () ->
					authService.unlinkSocialAccount(100L, 1L));
		}

		@Test
		@DisplayName("8. 소셜 계정이 2개일 때 연동 해제 성공 및 대표 제공자가 갱신된다")
		void unlinkSocialSucceedsAndUpdatesPrimaryProvider() {
			UserEntity user = UserEntity.builder()
					.id(100L)
					.oauthProvider("google")
					.oauthId("g1")
					.build();
			SocialAccountEntity targetSocial = SocialAccountEntity.builder()
					.id(1L)
					.user(user)
					.provider("google")
					.providerId("g1")
					.build();
			SocialAccountEntity remainingSocial = SocialAccountEntity.builder()
					.id(2L)
					.user(user)
					.provider("kakao")
					.providerId("k1")
					.build();

			when(userRepository.findById(100L)).thenReturn(Optional.of(user));
			when(socialAccountRepository.findAllByUserId(100L))
					.thenReturn(List.of(targetSocial, remainingSocial))
					.thenReturn(List.of(remainingSocial));
			when(socialAccountRepository.findById(1L)).thenReturn(Optional.of(targetSocial));

			authService.unlinkSocialAccount(100L, 1L);

			verify(socialAccountRepository, times(1)).delete(targetSocial);
			assertEquals("kakao", user.getOauthProvider());
			assertEquals("k1", user.getOauthId());
		}
	}

	@Nested
	@DisplayName("AuthController 엔드포인트 테스트")
	class AuthControllerTests {
		@Mock
		private AuthService authService;
		@Mock
		private AuthAttemptLimiter authAttemptLimiter;

		@InjectMocks
		private AuthController authController;

		@Test
		@DisplayName("1. GET /auth/check-phone API가 중복된 번호일 때 연동 정보를 응답한다")
		void checkPhoneReturnsTakenAndLinkedInfo() {
			MockHttpSession session = new MockHttpSession();
			session.setAttribute("userId", 50L);

			when(authService.isValidPhone("010-1234-5678")).thenReturn(true);
			when(authService.findExistingAccountByPhone("010-1234-5678", 50L))
					.thenReturn(Optional.of(new AuthService.ExistingAccountInfo(10L, "naver", "네이버", "test***@naver.com")));

			ResponseEntity<Map<String, Object>> response = authController.checkPhone("010-1234-5678", session);

			assertEquals(200, response.getStatusCode().value());
			Map<String, Object> body = response.getBody();
			assertNotNull(body);
			assertEquals(true, body.get("taken"));
			assertEquals(true, body.get("linked"));
			assertEquals("네이버", body.get("provider"));
		}

		@Test
		@DisplayName("2. POST /auth/link-account 처리 시 (비밀번호 검증 통과 후) 타겟 회원의 세션으로 갱신 후 /app으로 리다이렉트한다")
		void linkAccountUpdatesSessionAndRedirects() {
			MockHttpSession session = new MockHttpSession();
			session.setAttribute("userId", 50L);
			MockHttpServletRequest request = new MockHttpServletRequest();
			MockHttpServletResponse response = new MockHttpServletResponse();

			UserEntity targetUser = UserEntity.builder()
					.id(10L)
					.role(UserEntity.ROLE_USER)
					.profileCompleted(true)
					.build();

			when(authService.linkSocialAccountWithPassword(50L, "010-1234-5678", "correct-pw")).thenReturn(targetUser);

			String view = authController.linkAccount("010-1234-5678", "correct-pw", request, response, session);

			assertEquals("redirect:/app", view);
			assertEquals(10L, session.getAttribute("userId"));
			assertEquals(UserEntity.ROLE_USER, session.getAttribute("role"));
			assertEquals(true, session.getAttribute("profileCompleted"));
		}

		@Test
		@DisplayName("3. (보안) 비밀번호 검증에 실패하면 세션을 바꾸지 않고 에러와 함께 되돌아간다")
		void linkAccountDoesNotSwitchSessionOnFailure() {
			MockHttpSession session = new MockHttpSession();
			session.setAttribute("userId", 50L);
			MockHttpServletRequest request = new MockHttpServletRequest();
			MockHttpServletResponse response = new MockHttpServletResponse();

			when(authService.linkSocialAccountWithPassword(50L, "010-1234-5678", "wrong-pw"))
					.thenThrow(new IllegalArgumentException("비밀번호가 일치하지 않습니다."));

			String view = authController.linkAccount("010-1234-5678", "wrong-pw", request, response, session);

			assertEquals("redirect:/auth/signup?error=link_failed", view);
			assertEquals(50L, session.getAttribute("userId"));
		}

		@Test
		@DisplayName("4. (보안) GET /auth/link-account/prepare는 등록된 provider만 허용하고, 그 외엔 연동 상태를 남기지 않는다")
		void prepareLinkReauthRejectsUnknownProvider() {
			MockHttpSession session = new MockHttpSession();
			session.setAttribute("userId", 200L);

			String view = authController.prepareLinkReauth("010-1234-5678", "not-a-real-provider", session);

			assertEquals("redirect:/auth/signup", view);
			assertNull(session.getAttribute("linkPendingSourceUserId"));
			assertNull(session.getAttribute("linkPendingPhone"));
		}

		@Test
		@DisplayName("5. GET /auth/link-account/prepare는 화이트리스트 provider면 연동 대기 정보를 세션에 남기고 소셜 로그인으로 보낸다")
		void prepareLinkReauthAcceptsWhitelistedProvider() {
			MockHttpSession session = new MockHttpSession();
			session.setAttribute("userId", 200L);

			String view = authController.prepareLinkReauth("010-1234-5678", "kakao", session);

			assertEquals("redirect:/oauth2/authorization/kakao", view);
			assertEquals(200L, session.getAttribute("linkPendingSourceUserId"));
			assertEquals("010-1234-5678", session.getAttribute("linkPendingPhone"));
		}
	}

	@Nested
	@DisplayName("OAuth2LoginSuccessHandler 소셜 재인증 병합 테스트 — 핵심 보안 속성: 전화번호가 일치할 때만, 그것도 실제 재로그인 성공 후에만 병합된다")
	class OAuth2LoginSuccessHandlerTests {
		@Mock
		private AuthService authService;

		@InjectMocks
		private OAuth2LoginSuccessHandler successHandler;

		private UserPrincipal principalOf(UserEntity user) {
			return () -> user;
		}

		@Test
		@DisplayName("1. 세션에 연동 대기 정보가 있고 방금 로그인한 계정의 전화번호가 일치하면 병합을 수행한다")
		void mergesWhenPhoneMatches() throws Exception {
			UserEntity reauthUser = UserEntity.builder().id(100L).role(UserEntity.ROLE_USER)
					.phone("010-1234-5678").profileCompleted(true).build();
			UserEntity mergedUser = UserEntity.builder().id(100L).role(UserEntity.ROLE_USER)
					.phone("010-1234-5678").profileCompleted(true).build();

			MockHttpServletRequest request = new MockHttpServletRequest();
			MockHttpServletResponse response = new MockHttpServletResponse();
			MockHttpSession session = new MockHttpSession();
			session.setAttribute("linkPendingSourceUserId", 200L);
			session.setAttribute("linkPendingPhone", "010-1234-5678");
			request.setSession(session);

			Authentication auth = mock(Authentication.class);
			when(auth.getPrincipal()).thenReturn(principalOf(reauthUser));
			when(authService.linkSocialAccountAfterReauth(200L, 100L)).thenReturn(mergedUser);

			successHandler.onAuthenticationSuccess(request, response, auth);

			verify(authService, times(1)).linkSocialAccountAfterReauth(200L, 100L);
			assertNull(session.getAttribute("linkPendingSourceUserId"));
			assertNull(session.getAttribute("linkPendingPhone"));
		}

		@Test
		@DisplayName("2. (보안) 방금 로그인한 계정의 전화번호가 연동 대기 중이던 번호와 다르면 절대 병합하지 않는다")
		void doesNotMergeWhenPhoneMismatches() throws Exception {
			UserEntity reauthUser = UserEntity.builder().id(101L).role(UserEntity.ROLE_USER)
					.phone("010-9999-0000").profileCompleted(true).build();

			MockHttpServletRequest request = new MockHttpServletRequest();
			MockHttpServletResponse response = new MockHttpServletResponse();
			MockHttpSession session = new MockHttpSession();
			session.setAttribute("linkPendingSourceUserId", 200L);
			session.setAttribute("linkPendingPhone", "010-1234-5678");
			request.setSession(session);

			Authentication auth = mock(Authentication.class);
			when(auth.getPrincipal()).thenReturn(principalOf(reauthUser));

			successHandler.onAuthenticationSuccess(request, response, auth);

			verify(authService, never()).linkSocialAccountAfterReauth(any(), any());
		}

		@Test
		@DisplayName("3. 연동 대기 세션 정보가 없는 일반 로그인이면 병합 로직에 관여하지 않는다")
		void skipsWhenNoPendingLinkInSession() throws Exception {
			UserEntity user = UserEntity.builder().id(300L).role(UserEntity.ROLE_USER)
					.phone("010-1111-2222").profileCompleted(true).build();

			MockHttpServletRequest request = new MockHttpServletRequest();
			MockHttpServletResponse response = new MockHttpServletResponse();
			request.setSession(new MockHttpSession());

			Authentication auth = mock(Authentication.class);
			when(auth.getPrincipal()).thenReturn(principalOf(user));

			successHandler.onAuthenticationSuccess(request, response, auth);

			verify(authService, never()).linkSocialAccountAfterReauth(any(), any());
		}
	}

	@Nested
	@DisplayName("Mypage 소셜 계정 연동 해제 엔드포인트 테스트")
	class MypageUnlinkTests {
		@Mock
		private MypageService mypageService;
		@Mock
		private AuthService authService;
		@Mock
		private StoreAccessService storeAccessService;
		@Mock
		private ReservationService reservationService;
		@Mock
		private LedgerService ledgerService;

		@InjectMocks
		private MypageController mypageController;

		@Test
		@DisplayName("1. 소셜 계정 연동 해제 성공 시 ?unlinked로 리다이렉트한다")
		void unlinkSocialRedirectsWithUnlinkedParam() {
			MockHttpSession session = new MockHttpSession();
			session.setAttribute("userId", 100L);

			String view = mypageController.unlinkSocial(5L, session);

			verify(authService).unlinkSocialAccount(100L, 5L);
			assertEquals("redirect:/mypage/edit?unlinked", view);
		}

		@Test
		@DisplayName("2. 최소 1개 로그인 수단 제한으로 실패 시 ?unlinkError=minimum으로 리다이렉트한다")
		void unlinkSocialFailsWhenMinimumMethod() {
			MockHttpSession session = new MockHttpSession();
			session.setAttribute("userId", 100L);

			doThrow(new IllegalStateException("최소 1개의 로그인 수단은 유지되어야 합니다."))
					.when(authService).unlinkSocialAccount(100L, 5L);

			String view = mypageController.unlinkSocial(5L, session);

			assertEquals("redirect:/mypage/edit?unlinkError=minimum", view);
		}
	}
}

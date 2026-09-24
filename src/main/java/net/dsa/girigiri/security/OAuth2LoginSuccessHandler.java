package net.dsa.girigiri.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.entity.UserEntity;
import net.dsa.girigiri.service.AuthService;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 소셜 로그인 성공 시 CLAUDE.md 세션 구조(userId, role, viewMode, storeId) 중
 * userId/role을 세션에 채운다.
 *
 * TODO(담당 미정): storeId 연결은 점주(OWNER) 신청/승인 플로우가 갖춰지면 채운다.
 *
 * 변경됨 (2026-08-21) — 왜: role 라우팅 + 유저/점주 모드 분기 작업 시작하면서 viewMode 초기값을 채워넣는다.
 * role은 불변, viewMode는 헤더 토글(AuthController#toggleMode)로 로그인 후에도 바뀔 수 있다(CLAUDE.md
 * "인증 & 권한 설계" 참고). 여기서 이름이 "어드민"이던 옛 표기가 실제로는 점주(OWNER)를 가리키므로
 * ADMIN(운영자)은 이 분기와 무관 — OWNER만 OWNER_MODE로 시작하고 나머지(USER/ADMIN)는 USER_MODE.
 *
 * 변경됨 (2026-08-20) — 왜: LINE 로그인(openid 스코프)은 principal이 OAuth2UserPrincipal이 아니라
 * OidcUserPrincipal이라, 구체 타입으로 캐스팅하면 LINE 로그인에서만 ClassCastException이 났다.
 * 두 타입이 공통으로 구현하는 UserPrincipal 인터페이스로 캐스팅해서 로그인 방식에 상관없이 처리한다.
 *
 * 변경됨 (2026-08-21) — 왜: role=PENDING 개념이 없어지고 가입 시 무조건 role=USER로 확정되므로,
 * "PENDING이면 /auth/roleSelect로" 분기가 더는 필요 없다 — 로그인 성공하면 항상 "/"로 이동.
 *
 * 변경됨 (2026-08-21) — 왜: 최초 로그인 직후엔 닉네임/활동 지역을 아직 입력받지 않았으므로(profileCompleted=false),
 * "/" 대신 회원가입 완료 화면(/auth/signup)으로 보낸다. 이미 프로필을 완성한 재로그인 유저는 그대로 "/"로 이동.
 *
 * 변경됨 (2026-08-21) — 왜: 이메일 로그인(EmailLoginSuccessHandler)에도 완전히 동일한 세션 초기화
 * 로직이 필요해져서 AuthSessionInitializer로 공통 로직을 뽑아냈다.
 *
 * 변경됨 (2026-09-23, 코드 감사) — 왜: 1:N 계정 연동(Account Linking)의 소셜 재인증 경로
 * (AuthController#prepareLinkReauth)가 여기로 돌아온다. "가입 대기 중이던 계정을, 지금 막 실제로
 * 로그인에 성공한 이 계정으로 병합해도 되는지"는 이 로그인이 진짜로 그 전화번호의 주인인지 확인해야만
 * 알 수 있어서(전화번호만으로는 본인 확인이 안 됨 — AuthService.linkSocialAccountWithPassword 주석 참고)
 * 그 판단을 세션 초기화 직전, 여기서 한다.
 */
@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

	private final AuthService authService;

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
			throws IOException, ServletException {
		UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
		UserEntity user = principal.getUser();

		HttpSession session = request.getSession();

		// 계정 연동 소셜 재인증 경로 확인 — 1회용(consume)으로 즉시 제거해 재사용/리플레이를 막는다.
		Object pendingSourceId = session.getAttribute("linkPendingSourceUserId");
		Object pendingPhone = session.getAttribute("linkPendingPhone");
		session.removeAttribute("linkPendingSourceUserId");
		session.removeAttribute("linkPendingPhone");

		if (pendingSourceId instanceof Long sourceId && pendingPhone instanceof String phoneStr
				&& !sourceId.equals(user.getId())
				&& user.getPhone() != null
				&& user.getPhone().equals(AuthService.formatPhone(phoneStr))) {
			// 지금 막 로그인에 성공한 계정(user)이 연동하려던 전화번호의 실제 주인임이 증명됐다 —
			// 가입 대기 중이던 계정(sourceId)의 소셜 정보를 이 계정으로 병합한다. 그 사이 대기 계정이
			// 이미 다른 방식으로 가입을 완료했거나 삭제됐다면(드문 경합) 병합만 건너뛰고 이 로그인
			// 자체는 정상 진행한다 — 로그인이 예외로 깨지면 안 된다.
			try {
				user = authService.linkSocialAccountAfterReauth(sourceId, user.getId());
			} catch (RuntimeException e) {
				logger.info("계정 연동 실패 (소셜 재인증 경로, 로그인은 정상 진행): " + e.getMessage());
			}
		}

		// 추가됨 (2026-09-24) — 이메일 가입 중 "이미 다른 소셜로 가입된 이메일" 재인증 경로 확인.
		// AuthController#prepareEmailSignupLink가 stash해둔 값과, 지금 막 로그인에 성공한 계정의 id가
		// 정확히 같을 때만(=본인 확인 성공) 그 계정에 이메일+비밀번호 로그인을 추가한다.
		Object pendingEmailLinkTargetUserId = session.getAttribute("pendingEmailLinkTargetUserId");
		Object pendingEmailLinkEmail = session.getAttribute("pendingEmailLinkEmail");
		Object pendingEmailLinkPasswordHash = session.getAttribute("pendingEmailLinkPasswordHash");
		session.removeAttribute("pendingEmailLinkTargetUserId");
		session.removeAttribute("pendingEmailLinkEmail");
		session.removeAttribute("pendingEmailLinkPasswordHash");
		session.removeAttribute("pendingEmailLinkProviderRaw");

		if (pendingEmailLinkTargetUserId instanceof Long targetId && pendingEmailLinkEmail instanceof String emailStr
				&& pendingEmailLinkPasswordHash instanceof String passwordHash
				&& targetId.equals(user.getId())) {
			try {
				user = authService.linkEmailPasswordToAccount(targetId, emailStr, passwordHash);
			} catch (RuntimeException e) {
				logger.info("이메일 로그인 추가 실패 (재인증 경로, 로그인은 정상 진행): " + e.getMessage());
			}
		}

		String targetUrl = AuthSessionInitializer.initSessionAndGetTargetUrl(request, session, user);

		clearAuthenticationAttributes(request);
		getRedirectStrategy().sendRedirect(request, response, targetUrl);
	}
}

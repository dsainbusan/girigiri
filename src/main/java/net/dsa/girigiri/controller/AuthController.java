package net.dsa.girigiri.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.domain.entity.UserEntity;
import net.dsa.girigiri.security.AuthAttemptLimiter;
import net.dsa.girigiri.security.AuthSessionInitializer;
import net.dsa.girigiri.security.EmailUserPrincipal;
import net.dsa.girigiri.security.LoginRequired;
import net.dsa.girigiri.security.OAuth2UserPrincipal;
import net.dsa.girigiri.security.OidcUserPrincipal;
import net.dsa.girigiri.service.AuthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

/**
 * 인증 및 회원가입 컨트롤러
 * 담당: 문창호 (WBS 2.1 인증)
 */
@Slf4j
@Controller
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

	private final AuthService authService;
	private final AuthAttemptLimiter authAttemptLimiter;
	private final net.dsa.girigiri.util.EmailOtpClient emailOtpClient;

	// 가입 직후 자동 로그인 시 SecurityContext를 세션에 저장하는 용도(다음 요청 /auth/signup이 인증되게).
	// formLogin이 내부적으로 쓰는 것과 같은 저장소 — 상태가 없어 인스턴스 하나를 공유해도 된다.
	private static final SecurityContextRepository SECURITY_CONTEXT_REPOSITORY = new HttpSessionSecurityContextRepository();

	// 추가됨 — 왜: 회원가입 완료 화면의 "GPS로 활동 지역 채우기" 버튼이 카카오 지오코더(좌표→주소)를
	// 쓴다 — storeView/edit.html·SuperAdminController와 같은 설정 키 재사용.
	@Value("${kakao.map.js-key}")
	private String kakaoMapJsKey;

	@GetMapping("/loginForm")
	public String loginForm() {
		return "authView/loginForm";
	}

	/**
	 * 이메일 회원가입 폼 화면
	 */
	@GetMapping("/emailSignup")
	public String emailSignupForm(@RequestParam(required = false) String email, Model model) {
		model.addAttribute("email", email);
		return "authView/emailSignup";
	}

	/**
	 * 이메일 회원가입 1단계 — 형식·중복 검증 후 이메일 인증번호(OTP)를 발송한다. 계정은 아직 안 만든다.
	 * 변경됨 (2026-09-24, 코드 감사) — 예전엔 이 단계에서 바로 계정을 만들었는데, 그러면 인증 안 된
	 * 남의 이메일로 계정을 선점("스쿼팅")당할 수 있는 결함이 있었다(AuthService.validateEmailSignupInput
	 * 주석 참고). 인증까지 통과해야 실제 계정이 생기도록 순서를 바꿨다.
	 */
	@PostMapping("/emailSignup/send-otp")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> sendEmailSignupOtp(
			@RequestParam String email,
			@RequestParam String password,
			@RequestParam String passwordConfirm,
			HttpSession session) {
		var validation = authService.validateEmailSignupInput(email, password, passwordConfirm);
		if (validation.status() != AuthService.EmailSignupResult.SUCCESS) {
			return ResponseEntity.ok(Map.of("ok", false, "error", validation.status().name()));
		}

		// 추가됨 (2026-09-24, 코드 감사) — 위 검증은 provider="email"끼리만 중복을 본다. 이 이메일이
		// 이미 다른 provider(구글/카카오/라인) 계정에 있으면, 새 계정을 또 만드는 대신 그 계정에
		// 이메일+비밀번호 로그인을 "추가"하는 흐름으로 보낸다 — AuthService.findExistingAccountByEmail 참고.
		var existingByEmail = authService.findExistingAccountByEmail(validation.normalizedEmail());
		if (existingByEmail.isPresent()) {
			var info = existingByEmail.get();
			session.setAttribute("pendingEmailLinkTargetUserId", info.userId());
			session.setAttribute("pendingEmailLinkEmail", validation.normalizedEmail());
			session.setAttribute("pendingEmailLinkPasswordHash", authService.encodePassword(password));
			session.setAttribute("pendingEmailLinkProviderRaw", info.primaryProvider());
			return ResponseEntity.ok(Map.of(
					"ok", true,
					"linked", true,
					"provider", info.providerDisplayName(),
					"providerRaw", info.primaryProvider()
			));
		}

		String code = AuthService.generateOtpCode();
		session.setAttribute("pendingEmailSignupEmail", validation.normalizedEmail());
		session.setAttribute("pendingEmailSignupPasswordHash", authService.encodePassword(password));
		session.setAttribute("emailSignupOtpCode", code);
		session.setAttribute("emailSignupOtpExpiresAt", java.time.LocalDateTime.now().plusMinutes(3));

		boolean mailSent = emailOtpClient.sendOtpEmail(validation.normalizedEmail(), code);
		Map<String, Object> body = new java.util.HashMap<>();
		body.put("ok", true);
		body.put("mailSent", mailSent);
		body.put("expiresInSeconds", 180);
		if (!mailSent) {
			log.info("[이메일 가입 인증 폴백] {} 로 보낼 인증번호: {}", validation.normalizedEmail(), code);
			body.put("mockCode", code);
		}
		return ResponseEntity.ok(body);
	}

	/**
	 * 이메일 회원가입 2단계 — 인증번호가 맞아야 이 시점에 실제 계정을 만들고 바로 자동 로그인시킨 뒤
	 * 부가정보 입력 화면(/auth/signup)으로 보낸다. 소셜 로그인의 최초 흐름과 동일하게 맞춘 것이다.
	 */
	@PostMapping("/emailSignup/verify-otp")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> verifyEmailSignupOtp(
			@RequestParam String code,
			HttpServletRequest request,
			HttpServletResponse response,
			HttpSession session) {
		String pendingEmail = (String) session.getAttribute("pendingEmailSignupEmail");
		String pendingPasswordHash = (String) session.getAttribute("pendingEmailSignupPasswordHash");
		String savedCode = (String) session.getAttribute("emailSignupOtpCode");
		java.time.LocalDateTime expiresAt = (java.time.LocalDateTime) session.getAttribute("emailSignupOtpExpiresAt");

		if (pendingEmail == null || pendingPasswordHash == null || savedCode == null || expiresAt == null) {
			return ResponseEntity.ok(Map.of("verified", false, "reason", "not_requested"));
		}
		if (java.time.LocalDateTime.now().isAfter(expiresAt)) {
			return ResponseEntity.ok(Map.of("verified", false, "reason", "expired"));
		}
		if (!savedCode.equals(code)) {
			return ResponseEntity.ok(Map.of("verified", false, "reason", "mismatch"));
		}

		UserEntity user;
		try {
			user = authService.completeEmailSignup(pendingEmail, pendingPasswordHash);
		} catch (IllegalStateException e) {
			return ResponseEntity.ok(Map.of("verified", false, "reason", "duplicate"));
		}
		session.removeAttribute("pendingEmailSignupEmail");
		session.removeAttribute("pendingEmailSignupPasswordHash");
		session.removeAttribute("emailSignupOtpCode");
		session.removeAttribute("emailSignupOtpExpiresAt");

		// 방금 만든 계정은 항상 role=USER·profileCompleted=false라 redirectTo는 결정적으로
		// "/auth/signup"이다(AuthSessionInitializer 참고) — 다음 화면에서 "휴대폰 인증도 필요하다"는
		// 걸 미리 알려주는 안내 배너를 띄우기 위해 쿼리 파라미터를 붙인다(피로감 완화, 2026-09-24).
		String redirectTo = autoLoginAfterSignup(user, request, response) + "?welcome=email_verified";
		return ResponseEntity.ok(Map.of("verified", true, "redirectTo", redirectTo));
	}

	/**
	 * 가입 직후 자동 로그인 — 비밀번호는 방금 검증·저장했으므로 AuthenticationManager를 다시
	 * 돌리지 않고 인증된 토큰을 바로 만든다. SecurityContext를 세션에 저장해야 다음 요청
	 * (/auth/signup, 인증 필요)이 통과한다. 세션의 userId/role/viewMode는 소셜 로그인과 같은
	 * 공용 로직(AuthSessionInitializer)으로 채우고, 그게 알려주는 목적지(최초 가입이므로 /auth/signup)를 돌려준다.
	 */
	private String autoLoginAfterSignup(UserEntity user, HttpServletRequest request, HttpServletResponse response) {
		EmailUserPrincipal principal = new EmailUserPrincipal(user);
		UsernamePasswordAuthenticationToken authentication =
				UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());

		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(authentication);
		SecurityContextHolder.setContext(context);
		SECURITY_CONTEXT_REPOSITORY.saveContext(context, request, response);

		return AuthSessionInitializer.initSessionAndGetTargetUrl(request, request.getSession(true), user);
	}

	/**
	 * 이메일 로그인 폼 화면 — 실제 인증 처리(POST /auth/emailLogin)는 Spring Security
	 * formLogin(WebSecurityConfig)이 가로채므로 여기엔 GET만 있다.
	 */
	@GetMapping("/emailLogin")
	public String emailLoginForm() {
		return "authView/emailLogin";
	}

	/**
	 * 유저 모드 / 점주 모드 전환 토글 — role은 그대로 두고 세션 viewMode만 바꾼다.
	 *
	 * 추가됨 (2026-08-21) — 왜: role 라우팅 + 유저/점주 모드 분기 작업. OWNER 계정만 이 토글을 쓸 수 있다
	 * (common/header.html에서 role==OWNER일 때만 버튼 노출). USER가 이 엔드포인트를 직접 호출해도
	 * role 체크로 막아서 모드가 안 바뀐다 — 권한 승격이 아니라 화면 전환일 뿐이라 role은 절대 안 건드린다.
	 */
	@PostMapping("/mode")
	public String toggleMode(@RequestParam(required = false) String target, HttpSession session) {
		if (!UserEntity.ROLE_OWNER.equals(session.getAttribute("role"))) {
			return "redirect:/app";
		}

		boolean switchToOwner;
		if ("owner".equalsIgnoreCase(target)) {
			switchToOwner = true;
		} else if ("user".equalsIgnoreCase(target)) {
			switchToOwner = false;
		} else {
			boolean isOwnerMode = "OWNER_MODE".equals(session.getAttribute("viewMode"));
			switchToOwner = !isOwnerMode;
		}

		session.setAttribute("viewMode", switchToOwner ? "OWNER_MODE" : "USER_MODE");
		return "redirect:" + (switchToOwner ? "/store/dashboard" : "/");
	}

	/**
	 * 최초 소셜 로그인 직후 회원가입 완료 화면
	 * 닉네임, 활동 지역, 소비자/사장님 이용 모드를 입력받습니다.
	 */
	@LoginRequired
	@GetMapping("/signup")
	public String signupForm(@RequestParam(required = false) String linkedProvider,
	                         @RequestParam(required = false) String linkedProviderRaw,
	                         @RequestParam(required = false) String linkedPhone,
	                         HttpSession session, Model model) {
		Long userId = (Long) session.getAttribute("userId");
		UserEntity user = authService.getUserForSignup(userId);

		model.addAttribute("provider", user.getOauthProvider());
		model.addAttribute("maskedEmail", AuthService.maskEmail(user.getEmail()));
		String rawNick = user.getNickname();
		if (rawNick != null && rawNick.length() > 10) {
			rawNick = rawNick.substring(0, 10);
		}
		model.addAttribute("nickname", rawNick);
		model.addAttribute("phone", linkedPhone != null ? linkedPhone : user.getPhone());
		model.addAttribute("region", user.getRegion());
		model.addAttribute("kakaoMapJsKey", kakaoMapJsKey);
		model.addAttribute("linkedProvider", linkedProvider);
		model.addAttribute("linkedProviderRaw", linkedProviderRaw);
		model.addAttribute("linkedPhone", linkedPhone);
		return "authView/signup";
	}

	/**
	 * 회원가입 정보 저장 및 역할(소비자/사장님) 분기 처리
	 * - 소비자 선택: profileCompleted=true, role=USER 확정 후 메인(/) 이동
	 * - 사장님 선택: 기본 정보 저장 후 점주 입점 신청(/auth/owner-apply)으로 이동
	 */
	@LoginRequired
	@PostMapping("/signup")
	public String signup(@RequestParam String nickname,
	                     @RequestParam(required = false) String phone,
	                     @RequestParam(required = false) String region,
	                     @RequestParam(defaultValue = "USER") String userType,
	                     @RequestParam(defaultValue = "false") boolean termsAgreed,
	                     @RequestParam(defaultValue = "false") boolean privacyAgreed,
	                     @RequestParam(defaultValue = "false") boolean marketingAgreed,
	                     HttpSession session) {
		// 필수 약관(이용약관·개인정보 수집이용) 미동의 — 화면 JS로도 막지만 서버에서도 재검증한다.
		if (!termsAgreed || !privacyAgreed) {
			return "redirect:/auth/signup?error=agree";
		}
		if (!authService.isValidNickname(nickname)) {
			return "redirect:/auth/signup?error=nickname";
		}
		if (!authService.isValidPhone(phone)) {
			return "redirect:/auth/signup?error=phone";
		}
		// 추가됨 (2026-09-24) — 휴대폰 인증(OTP, 목업) 완료 여부 재검증. 화면 JS로도 막지만, 위 두 검증과
		// 같은 이유로 서버에서도 확인한다 — /auth/verify-otp 성공 시에만 세션에 심어지는 값이라
		// 이 값이 이번에 제출한 번호와 정확히 일치할 때만 통과시킨다.
		String otpVerifiedPhone = (String) session.getAttribute("otpVerifiedPhone");
		if (otpVerifiedPhone == null || !otpVerifiedPhone.equals(AuthService.formatPhone(phone))) {
			return "redirect:/auth/signup?error=otp_required";
		}

		Long userId = (Long) session.getAttribute("userId");
		if (authService.isPhoneTaken(phone)) {
			var existing = authService.findExistingAccountByPhone(phone, userId);
			if (existing.isPresent()) {
				String prov = existing.get().providerDisplayName();
				String encProv = java.net.URLEncoder.encode(prov, java.nio.charset.StandardCharsets.UTF_8);
				String encProvRaw = java.net.URLEncoder.encode(existing.get().primaryProvider(), java.nio.charset.StandardCharsets.UTF_8);
				String encPhone = java.net.URLEncoder.encode(phone, java.nio.charset.StandardCharsets.UTF_8);
				return "redirect:/auth/signup?error=phone_linked&linkedProvider=" + encProv
						+ "&linkedProviderRaw=" + encProvRaw + "&linkedPhone=" + encPhone;
			}
			return "redirect:/auth/signup?error=phonedup";
		}

		authService.completeSignup(userId, nickname, phone, region, marketingAgreed);
		session.setAttribute("profileCompleted", true);   // ProfileCompletionInterceptor 통과용

		// 사장님으로 시작 선택 시 점주 입점 신청 페이지로 (거기서 매장 정보 입력 → owner-apply-complete로 마무리).
		if ("OWNER".equalsIgnoreCase(userType)) {
			return "redirect:/auth/owner-apply";
		}
		// 소비자로 시작 선택 시 "가입 완료" 안내 화면으로.
		return "redirect:/auth/signup-complete";
	}

	/**
	 * 휴대폰 번호 중복 및 연동 대상 계정 확인 (AJAX 비동기 검사)
	 * 추가됨 (2026-09-23, 코드 감사) — @LoginRequired 없이 열려 있으면 비로그인 상태에서도 누구나
	 * 임의의 번호를 넣어 가입 여부·가입 수단·마스킹 이메일을 조회할 수 있었다(계정 존재 정찰).
	 * 가입 진행 중(로그인은 됐지만 profileCompleted=false)인 사용자만 쓰는 화면이므로 로그인은 필수다.
	 */
	@LoginRequired
	@GetMapping("/check-phone")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> checkPhone(
			@RequestParam String phone,
			HttpSession session) {
		Long currentUserId = (Long) session.getAttribute("userId");
		if (!authService.isValidPhone(phone)) {
			return ResponseEntity.ok(Map.of("valid", false, "taken", false));
		}
		var existing = authService.findExistingAccountByPhone(phone, currentUserId);
		if (existing.isPresent()) {
			var info = existing.get();
			return ResponseEntity.ok(Map.of(
					"valid", true,
					"taken", true,
					"linked", true,
					"provider", info.providerDisplayName(),
					"providerRaw", info.primaryProvider(),
					"maskedEmail", info.maskedEmail() != null ? info.maskedEmail() : ""
			));
		}
		return ResponseEntity.ok(Map.of("valid", true, "taken", false));
	}

	/**
	 * 휴대폰 인증번호(OTP) 발송 — 목업.
	 * 추가됨 (2026-09-24) — 실제 SMS API(사업자 계약 필요)는 이번 범위 밖이라, 서버가 만든 코드를
	 * 실제로 문자 발송하는 대신 응답에 그대로 실어 화면에 "테스트 환경" 배너로 보여준다. 오타·타인
	 * 번호 입력을 걸러주는 최소한의 "그 번호를 지금 받을 수 있는지" 확인이지, 통신사 실명인증은 아니다.
	 */
	@LoginRequired
	@PostMapping("/send-otp")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> sendOtp(@RequestParam String phone, HttpSession session) {
		if (!authService.isValidPhone(phone)) {
			return ResponseEntity.ok(Map.of("sent", false));
		}
		String formattedPhone = AuthService.formatPhone(phone);
		String code = AuthService.generateOtpCode();
		session.setAttribute("otpPhone", formattedPhone);
		session.setAttribute("otpCode", code);
		session.setAttribute("otpExpiresAt", java.time.LocalDateTime.now().plusMinutes(3));
		return ResponseEntity.ok(Map.of("sent", true, "mockCode", code, "expiresInSeconds", 180));
	}

	/**
	 * 휴대폰 인증번호(OTP) 확인 — 목업. 통과 시에만 세션에 otpVerifiedPhone을 남기고,
	 * /auth/signup POST가 그 값과 제출된 번호가 일치하는지 다시 확인한다.
	 */
	@LoginRequired
	@PostMapping("/verify-otp")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> verifyOtp(
			@RequestParam String phone,
			@RequestParam String code,
			HttpSession session) {
		String formattedPhone = AuthService.formatPhone(phone);
		String savedPhone = (String) session.getAttribute("otpPhone");
		String savedCode = (String) session.getAttribute("otpCode");
		java.time.LocalDateTime expiresAt = (java.time.LocalDateTime) session.getAttribute("otpExpiresAt");

		if (savedPhone == null || savedCode == null || expiresAt == null || !savedPhone.equals(formattedPhone)) {
			return ResponseEntity.ok(Map.of("verified", false, "reason", "not_requested"));
		}
		if (java.time.LocalDateTime.now().isAfter(expiresAt)) {
			return ResponseEntity.ok(Map.of("verified", false, "reason", "expired"));
		}
		if (!savedCode.equals(code)) {
			return ResponseEntity.ok(Map.of("verified", false, "reason", "mismatch"));
		}

		session.setAttribute("otpVerifiedPhone", formattedPhone);
		session.removeAttribute("otpCode");
		session.removeAttribute("otpPhone");
		session.removeAttribute("otpExpiresAt");
		return ResponseEntity.ok(Map.of("verified", true));
	}

	// 소셜 재인증 경로(prepareLinkReauth)로 보낼 수 있는 provider만 화이트리스트로 제한한다 —
	// application.properties에 실제로 등록된 registrationId(google/kakao/line)만 대상.
	// 임의 문자열을 그대로 "/oauth2/authorization/" 뒤에 붙이면 오픈 리다이렉트가 될 수 있어 반드시 검증한다.
	private static final java.util.Set<String> LINKABLE_OAUTH_PROVIDERS = java.util.Set.of("google", "kakao", "line");

	/**
	 * 1:N 멀티 소셜 계정 연동 처리 — 비밀번호 재인증 경로.
	 * 대상 계정이 이메일+비밀번호 로그인 계정일 때만 통과한다(AuthService.linkSocialAccountWithPassword
	 * 참고) — 소셜 전용 계정은 여기서 거부되고 prepareLinkReauth(실제 소셜 재로그인)로만 연동 가능하다.
	 */
	@LoginRequired
	@PostMapping("/link-account")
	public String linkAccount(@RequestParam String phone,
	                          @RequestParam(required = false) String password,
	                          HttpServletRequest request,
	                          HttpServletResponse response,
	                          HttpSession session) {
		Long currentUserId = (Long) session.getAttribute("userId");
		if (currentUserId == null) {
			return "redirect:/auth/loginForm";
		}
		UserEntity targetUser;
		try {
			targetUser = authService.linkSocialAccountWithPassword(currentUserId, phone, password);
		} catch (IllegalArgumentException | IllegalStateException e) {
			log.info("계정 연동 실패 (비밀번호 재인증 경로): {}", e.getMessage());
			return "redirect:/auth/signup?error=link_failed";
		}

		// Spring Security Context 갱신
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth != null) {
			Authentication newAuth = null;
			if (auth.getPrincipal() instanceof OAuth2UserPrincipal oAuth2Principal) {
				OAuth2UserPrincipal newPrincipal = new OAuth2UserPrincipal(targetUser, oAuth2Principal.getAttributes(), oAuth2Principal.getNameAttributeKey());
				newAuth = new OAuth2AuthenticationToken(
						newPrincipal, newPrincipal.getAuthorities(), ((OAuth2AuthenticationToken) auth).getAuthorizedClientRegistrationId());
			} else if (auth.getPrincipal() instanceof OidcUserPrincipal oidcPrincipal) {
				OidcUserPrincipal newPrincipal = new OidcUserPrincipal(targetUser, oidcPrincipal.getDelegate());
				newAuth = new OAuth2AuthenticationToken(
						newPrincipal, newPrincipal.getAuthorities(), ((OAuth2AuthenticationToken) auth).getAuthorizedClientRegistrationId());
			} else if (auth.getPrincipal() instanceof EmailUserPrincipal) {
				EmailUserPrincipal newPrincipal = new EmailUserPrincipal(targetUser);
				newAuth = UsernamePasswordAuthenticationToken.authenticated(newPrincipal, null, newPrincipal.getAuthorities());
			}
			if (newAuth != null) {
				SecurityContext context = SecurityContextHolder.createEmptyContext();
				context.setAuthentication(newAuth);
				SecurityContextHolder.setContext(context);
				SECURITY_CONTEXT_REPOSITORY.saveContext(context, request, response);
			}
		}

		// 세션 정보 갱신
		session.setAttribute("userId", targetUser.getId());
		session.setAttribute("role", targetUser.getRole());
		session.setAttribute("profileCompleted", targetUser.isProfileCompleted());
		if (session.getAttribute("viewMode") == null) {
			session.setAttribute("viewMode", "USER_MODE");
		}

		return "redirect:/app";
	}

	/**
	 * 1:N 멀티 소셜 계정 연동 처리 — 소셜 재인증 경로 진입점.
	 * 대상 계정이 소셜 로그인(카카오/구글/라인) 전용이라 비밀번호가 없을 때 이 경로를 탄다.
	 * 여기서는 아직 아무것도 연동하지 않는다 — "나중에 돌아왔을 때 누가 누구를 연동하려는 시도였는지"만
	 * 세션에 남겨두고(linkPendingSourceUserId/linkPendingPhone) 실제 소셜 로그인(/oauth2/authorization/{provider})으로
	 * 브라우저를 그대로 보낸다. 그 로그인이 실제로 성공해서 돌아왔을 때 OAuth2LoginSuccessHandler가
	 * "방금 로그인한 계정의 전화번호가 연동하려던 번호와 일치하는지"까지 확인한 뒤에야 병합을 수행한다 —
	 * 즉 전화번호만으로는 병합이 안 되고, 대상 계정의 실제 소셜 로그인을 한 번 더 완료해야만 한다.
	 */
	@LoginRequired
	@GetMapping("/link-account/prepare")
	public String prepareLinkReauth(@RequestParam String phone,
	                                @RequestParam String provider,
	                                HttpSession session) {
		Long currentUserId = (Long) session.getAttribute("userId");
		if (currentUserId == null) {
			return "redirect:/auth/loginForm";
		}
		String normalizedProvider = provider == null ? "" : provider.toLowerCase();
		if (!LINKABLE_OAUTH_PROVIDERS.contains(normalizedProvider)) {
			return "redirect:/auth/signup";
		}
		session.setAttribute("linkPendingSourceUserId", currentUserId);
		session.setAttribute("linkPendingPhone", phone);
		return "redirect:/oauth2/authorization/" + normalizedProvider;
	}

	/**
	 * 이메일 가입 중 "이미 다른 소셜로 가입된 이메일" 재인증 경로 진입점.
	 * sendEmailSignupOtp가 이미 세션에 stash해둔 pendingEmailLink* 값을 그대로 쓰고, 여기서는 그
	 * provider가 화이트리스트에 있는지만 확인한 뒤 실제 소셜 로그인으로 브라우저를 보낸다. 로그인 전
	 * 상태(계정이 아직 없음)라 @LoginRequired를 걸 수 없다 — 대신 세션에 stash값이 없으면 그냥
	 * 돌려보낸다. 로그인 성공 후엔 OAuth2LoginSuccessHandler가 "방금 로그인한 계정이 stash해둔
	 * targetUserId와 정확히 같은지" 확인한 뒤에만 이메일+비밀번호를 그 계정에 추가한다.
	 */
	@GetMapping("/emailSignup/prepare-link")
	public String prepareEmailSignupLink(HttpSession session) {
		String providerRaw = (String) session.getAttribute("pendingEmailLinkProviderRaw");
		if (providerRaw == null || !LINKABLE_OAUTH_PROVIDERS.contains(providerRaw.toLowerCase())) {
			return "redirect:/auth/emailSignup";
		}
		return "redirect:/oauth2/authorization/" + providerRaw.toLowerCase();
	}

	/**
	 * 가입 취소 — 부가정보 입력을 마치지 않고 나갈 때(상단 뒤로가기·"나가기" 링크).
	 * 방금 만든 미완성 계정을 삭제하고(AuthService.cancelIncompleteSignup) 세션을 정리한 뒤 홈으로.
	 * 소셜 첫 로그인은 로그인 시점에 계정이 생겨서, 그냥 나가면 "약관 동의도 안 한 계정"이 남는다.
	 */
	@PostMapping("/signup/cancel")
	public String cancelSignup(HttpSession session) {
		Long userId = (Long) session.getAttribute("userId");
		if (userId != null) {
			authService.cancelIncompleteSignup(userId);
		}
		session.invalidate();
		return "redirect:/app";
	}

	/**
	 * 가입 완료 안내 화면 (소비자). 버튼을 눌러야 홈으로 나간다.
	 */
	@LoginRequired
	@GetMapping("/signup-complete")
	public String signupComplete(HttpSession session, Model model) {
		Long userId = (Long) session.getAttribute("userId");
		if (userId != null) {
			model.addAttribute("nickname", authService.getUserForSignup(userId).getNickname());
		}
		return "authView/signupComplete";
	}

	/**
	 * 회원 탈퇴 완료 안내 화면. 탈퇴 시 세션이 사라지므로 로그인 없이 접근 가능해야 한다
	 * (WebSecurityConfig PUBLIC_URLS에 등록). MypageController#withdraw가 여기로 리다이렉트한다.
	 */
	@GetMapping("/withdraw-complete")
	public String withdrawComplete() {
		return "authView/withdrawComplete";
	}

	/**
	 * 점주 입점 신청 (추가 정보 입력) 폼 화면
	 */
	@LoginRequired
	@GetMapping("/owner-apply")
	public String ownerApplyForm(HttpSession session, Model model) {
		return "authView/ownerApply";
	}

	/**
	 * 점주 입점 신청서 제출 처리
	 * - 매장 정보 및 사업자 등록번호를 PENDING 상태로 저장
	 * - 운영자(ADMIN, WBS 7.2 송보미)의 심사/승인 후 role=OWNER로 전환됨
	 */
	@LoginRequired
	@PostMapping("/owner-apply")
	public String ownerApply(@RequestParam String storeName,
	                         @RequestParam String businessNumber,
	                         @RequestParam String category,
	                         @RequestParam String address,
	                         @RequestParam String phone,
	                         @RequestParam(required = false) String operatingHours,
	                         HttpSession session) {
		Long userId = (Long) session.getAttribute("userId");
		if (!authService.isOwnerApplyValid(storeName, businessNumber, category, address, phone, operatingHours)) {
			return "redirect:/auth/owner-apply?error";
		}

		StoreEntity store = authService.ownerApply(userId, storeName, businessNumber, category, address, phone, operatingHours);

		session.setAttribute("appliedStoreName", store.getStoreName());
		return "redirect:/auth/owner-apply-complete";
	}

	/**
	 * 점주 입점 신청 완료 안내 화면 (2~3일 심사 소요 안내)
	 */
	@GetMapping("/owner-apply-complete")
	public String ownerApplyComplete(HttpSession session, Model model) {
		String storeName = (String) session.getAttribute("appliedStoreName");
		model.addAttribute("storeName", storeName);
		return "authView/ownerApplyComplete";
	}

	// ---- 이메일(아이디) 찾기 — 휴대폰 번호로 조회 ----

	@GetMapping("/find-email")
	public String findEmailForm() {
		return "authView/findEmail";
	}

	@PostMapping("/find-email")
	public String findEmail(@RequestParam String phone, HttpServletRequest request, Model model) {
		if (!authAttemptLimiter.tryAcquire("find-email:" + request.getRemoteAddr())) {
			model.addAttribute("rateLimited", true);
			return "authView/findEmail";
		}
		if (!authService.isValidPhone(phone)) {
			model.addAttribute("formatError", true);
			return "authView/findEmail";
		}
		model.addAttribute("result", authService.findEmailByPhone(phone));
		return "authView/findEmail";
	}

	// ---- 비밀번호 재설정 — 이메일 + 휴대폰 일치 시 바로 새 비번 설정 ----
	// 세션에 pwResetUserId를 잠깐 담아 "본인 확인됨" 상태를 이어간다(10분 유효).

	private static final long PW_RESET_TTL_MS = 10 * 60 * 1000L;

	@GetMapping("/reset-password")
	public String resetPasswordForm(HttpSession session) {
		clearPwReset(session);
		return "authView/resetPassword";
	}

	@PostMapping("/reset-password/verify")
	public String resetPasswordVerify(@RequestParam String email, @RequestParam String phone,
	                                  HttpServletRequest request, HttpSession session, Model model) {
		if (!authAttemptLimiter.tryAcquire("reset-pw:" + request.getRemoteAddr())) {
			model.addAttribute("rateLimited", true);
			return "authView/resetPassword";
		}
		var userId = authService.verifyForPasswordReset(email, phone);
		if (userId.isEmpty()) {
			log.warn("비밀번호 재설정 본인확인 실패 email={} ip={}", email, request.getRemoteAddr());
			model.addAttribute("verifyError", true);
			model.addAttribute("email", email);
			return "authView/resetPassword";
		}
		session.setAttribute("pwResetUserId", userId.get());
		session.setAttribute("pwResetAt", System.currentTimeMillis());
		model.addAttribute("verified", true);
		return "authView/resetPassword";
	}

	@PostMapping("/reset-password")
	public String resetPassword(@RequestParam String newPassword, @RequestParam String newPasswordConfirm,
	                            HttpSession session, Model model) {
		Long userId = (Long) session.getAttribute("pwResetUserId");
		Long verifiedAt = (Long) session.getAttribute("pwResetAt");
		if (userId == null || verifiedAt == null || System.currentTimeMillis() - verifiedAt > PW_RESET_TTL_MS) {
			clearPwReset(session);
			return "redirect:/auth/reset-password?expired";
		}
		if (!newPassword.equals(newPasswordConfirm)) {
			model.addAttribute("verified", true);
			model.addAttribute("mismatch", true);
			return "authView/resetPassword";
		}
		if (!authService.resetPassword(userId, newPassword)) {
			model.addAttribute("verified", true);
			model.addAttribute("tooShort", true);
			return "authView/resetPassword";
		}
		clearPwReset(session);
		log.info("비밀번호 재설정 완료 userId={}", userId);
		return "redirect:/auth/emailLogin?reset";
	}

	private static void clearPwReset(HttpSession session) {
		session.removeAttribute("pwResetUserId");
		session.removeAttribute("pwResetAt");
	}
}

package net.dsa.girigiri.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.domain.entity.UserEntity;
import net.dsa.girigiri.repository.StoreRepository;
import net.dsa.girigiri.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.regex.Pattern;

/**
 * 인증 및 회원가입 컨트롤러
 * 담당: 문창호 (WBS 2.1 인증)
 */
@Slf4j
@Controller
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

	// 추가됨 (2026-08-21) — 왜: 이메일 가입 시 최소한의 형식 검증용. 완벽한 RFC 5322 검증은 과함 —
	// "무언가@무언가.무언가" 수준만 걸러도 이 프로젝트 단계에선 충분하다.
	private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

	private final UserRepository userRepository;
	private final StoreRepository storeRepository;
	private final PasswordEncoder passwordEncoder;

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
	public String emailSignupForm() {
		return "authView/emailSignup";
	}

	/**
	 * 이메일 회원가입 처리 — 계정만 만들고 자동 로그인은 시키지 않는다(로그인 페이지로 보내서
	 * 방금 입력한 비밀번호로 직접 로그인하게 함 — Spring Security 수동 인증 코드 없이 단순하게 처리).
	 */
	@PostMapping("/emailSignup")
	public String emailSignup(@RequestParam String email,
	                          @RequestParam String password,
	                          @RequestParam String passwordConfirm) {
		String trimmedEmail = email == null ? "" : email.trim();
		if (!EMAIL_PATTERN.matcher(trimmedEmail).matches()) {
			return "redirect:/auth/emailSignup?error=email";
		}
		if (password == null || password.length() < 8) {
			return "redirect:/auth/emailSignup?error=password";
		}
		if (!password.equals(passwordConfirm)) {
			return "redirect:/auth/emailSignup?error=mismatch";
		}
		if (userRepository.findByOauthProviderAndOauthId("email", trimmedEmail).isPresent()) {
			return "redirect:/auth/emailSignup?error=duplicate";
		}

		UserEntity user = UserEntity.builder()
				.oauthProvider("email")
				.oauthId(trimmedEmail)
				.email(trimmedEmail)
				.password(passwordEncoder.encode(password))
				.role(UserEntity.ROLE_USER)
				.build();
		userRepository.save(user);

		return "redirect:/auth/emailLogin?justSignedUp";
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
	public String toggleMode(HttpSession session) {
		if (!UserEntity.ROLE_OWNER.equals(session.getAttribute("role"))) {
			return "redirect:/";
		}

		boolean isOwnerMode = "OWNER_MODE".equals(session.getAttribute("viewMode"));
		session.setAttribute("viewMode", isOwnerMode ? "USER_MODE" : "OWNER_MODE");

		return "redirect:" + (isOwnerMode ? "/" : "/store/dashboard");
	}

	/**
	 * 최초 소셜 로그인 직후 회원가입 완료 화면
	 * 닉네임, 활동 지역, 소비자/사장님 이용 모드를 입력받습니다.
	 */
	@GetMapping("/signup")
	public String signupForm(HttpSession session, Model model) {
		Long userId = (Long) session.getAttribute("userId");
		if (userId == null) {
			return "redirect:/auth/loginForm";
		}

		UserEntity user = userRepository.findById(userId).orElseThrow();

		model.addAttribute("provider", user.getOauthProvider());
		model.addAttribute("maskedEmail", maskEmail(user.getEmail()));
		model.addAttribute("nickname", user.getNickname());
		model.addAttribute("region", user.getRegion());
		model.addAttribute("kakaoMapJsKey", kakaoMapJsKey);
		return "authView/signup";
	}

	/**
	 * 회원가입 정보 저장 및 역할(소비자/사장님) 분기 처리
	 * - 소비자 선택: profileCompleted=true, role=USER 확정 후 메인(/) 이동
	 * - 사장님 선택: 기본 정보 저장 후 점주 입점 신청(/auth/owner-apply)으로 이동
	 */
	@PostMapping("/signup")
	public String signup(@RequestParam String nickname,
	                     @RequestParam(required = false) String region,
	                     @RequestParam(defaultValue = "USER") String userType,
	                     HttpSession session) {
		String trimmedNickname = nickname == null ? "" : nickname.trim();
		if (trimmedNickname.length() < 2 || trimmedNickname.length() > 10) {
			return "redirect:/auth/signup?error";
		}

		Long userId = (Long) session.getAttribute("userId");
		if (userId == null) {
			return "redirect:/auth/loginForm";
		}

		UserEntity user = userRepository.findById(userId).orElseThrow();
		user.setNickname(trimmedNickname);
		user.setRegion(region == null || region.isBlank() ? null : region.trim());
		user.setProfileCompleted(true);
		user.setRole(UserEntity.ROLE_USER); // 승인 전까지는 기본 USER 권한 유지
		userRepository.save(user);

		// 사장님으로 시작 선택 시 점주 입점 신청 페이지로 라우팅
		if ("OWNER".equalsIgnoreCase(userType)) {
			return "redirect:/auth/owner-apply";
		}

		// 소비자로 시작 선택 시 바로 홈 화면으로 이동
		return "redirect:/";
	}

	/**
	 * 점주 입점 신청 (추가 정보 입력) 폼 화면
	 */
	@GetMapping("/owner-apply")
	public String ownerApplyForm(HttpSession session, Model model) {
		Long userId = (Long) session.getAttribute("userId");
		if (userId == null) {
			return "redirect:/auth/loginForm";
		}
		return "authView/ownerApply";
	}

	/**
	 * 점주 입점 신청서 제출 처리
	 * - 매장 정보 및 사업자 등록번호를 PENDING 상태로 저장
	 * - 운영자(ADMIN, WBS 7.2 송보미)의 심사/승인 후 role=OWNER로 전환됨
	 */
	@PostMapping("/owner-apply")
	public String ownerApply(@RequestParam String storeName,
	                         @RequestParam String businessNumber,
	                         @RequestParam String category,
	                         @RequestParam String address,
	                         @RequestParam String phone,
	                         @RequestParam(required = false) String operatingHours,
	                         HttpSession session) {
		Long userId = (Long) session.getAttribute("userId");
		if (userId == null) {
			return "redirect:/auth/loginForm";
		}

		if (storeName == null || storeName.isBlank() ||
				businessNumber == null || businessNumber.isBlank() ||
				category == null || category.isBlank() ||
				address == null || address.isBlank() ||
				phone == null || phone.isBlank()) {
			return "redirect:/auth/owner-apply?error";
		}

		// 기존 신청 건이 있으면 업데이트, 없으면 신규 생성
		StoreEntity store = storeRepository.findByOwnerId(userId)
				.orElseGet(() -> StoreEntity.builder()
						.ownerId(userId)
						.role(UserEntity.ROLE_OWNER)
						.build());

		store.setRole(UserEntity.ROLE_OWNER);
		store.setStoreName(storeName.trim());
		store.setBusinessNumber(businessNumber.trim());
		store.setCategory(category.trim());
		store.setAddress(address.trim());
		store.setPhone(phone.trim());
		store.setOperatingHours(operatingHours != null && !operatingHours.isBlank() ? operatingHours.trim() : null);
		store.setApprovalStatus(StoreEntity.STATUS_PENDING); // 승인 대기 상태

		storeRepository.save(store);

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

	private static String maskEmail(String email) {
		if (email == null || !email.contains("@")) {
			return null;
		}
		String[] parts = email.split("@", 2);
		String local = parts[0];
		String masked = local.length() <= 4 ? local : local.substring(0, 4);
		return masked + "***@" + parts[1];
	}
}

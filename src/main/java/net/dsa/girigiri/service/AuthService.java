package net.dsa.girigiri.service;

import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.entity.SocialAccountEntity;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.domain.entity.UserEntity;
import net.dsa.girigiri.repository.SocialAccountRepository;
import net.dsa.girigiri.repository.StoreRepository;
import net.dsa.girigiri.repository.UserRepository;
import net.dsa.girigiri.util.BusinessNumberUtil;
import net.dsa.girigiri.util.PhoneUtil;
import net.dsa.girigiri.util.StoreHoursUtil;
import net.dsa.girigiri.util.StorePhoneUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 인증 및 회원가입 도메인 서비스 (2026-09-03, 레이어 규칙 2단계).
 *
 * AuthController에 흩어져 있던 Repository 직접 호출·검증·상태 변경 로직을 옮겨온다.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

	// 추가됨 (2026-08-21) — 왜: 이메일 가입 시 최소한의 형식 검증용. 완벽한 RFC 5322 검증은 과함 —
	// "무언가@무언가.무언가" 수준만 걸러도 이 프로젝트 단계에선 충분하다.
	private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

	public enum EmailSignupResult { SUCCESS, INVALID_EMAIL, INVALID_PASSWORD, PASSWORD_MISMATCH, DUPLICATE }

	/** 형식·중복 검증 결과. 계정은 아직 안 만든다 — normalizedEmail은 검증을 통과했든 안 했든 항상 채워진다(실패 화면에 값 유지용). */
	public record EmailSignupValidation(EmailSignupResult status, String normalizedEmail) {}

	private final UserRepository userRepository;
	private final StoreRepository storeRepository;
	private final PasswordEncoder passwordEncoder;
	private final SocialAccountRepository socialAccountRepository;

	/**
	 * 이메일 회원가입 1단계 — 형식·중복만 검증하고 계정은 아직 만들지 않는다.
	 * 변경됨 (2026-09-24, 코드 감사) — 예전엔 이 검증만 통과하면 바로 계정을 만들었는데, 그러면 인증
	 * 안 된 남의 이메일 주소로 계정을 먼저 만들어버릴 수 있었다(그 사람은 인증 코드를 받을 수 없어
	 * 영원히 가입을 못 끝내지만, 그 이메일은 이미 "가입됨"으로 DB에 남아 진짜 주인이 나중에 그
	 * 이메일로 가입하려 해도 중복으로 막히는 결함). 그래서 이메일 인증(OTP)을 먼저 통과시킨 뒤에만
	 * completeEmailSignup으로 실제 계정을 만들도록 순서를 바꿨다 — AuthController#sendEmailSignupOtp 참고.
	 */
	@Transactional(readOnly = true)
	public EmailSignupValidation validateEmailSignupInput(String email, String password, String passwordConfirm) {
		String trimmedEmail = email == null ? "" : email.trim();
		if (!EMAIL_PATTERN.matcher(trimmedEmail).matches()) {
			return new EmailSignupValidation(EmailSignupResult.INVALID_EMAIL, trimmedEmail);
		}
		if (password == null || password.length() < 8) {
			return new EmailSignupValidation(EmailSignupResult.INVALID_PASSWORD, trimmedEmail);
		}
		if (!password.equals(passwordConfirm)) {
			return new EmailSignupValidation(EmailSignupResult.PASSWORD_MISMATCH, trimmedEmail);
		}
		if (userRepository.findByOauthProviderAndOauthId("email", trimmedEmail).isPresent()
				|| socialAccountRepository.existsByProviderAndProviderId("email", trimmedEmail)) {
			return new EmailSignupValidation(EmailSignupResult.DUPLICATE, trimmedEmail);
		}
		return new EmailSignupValidation(EmailSignupResult.SUCCESS, trimmedEmail);
	}

	/** 이메일 인증(OTP) 발송 전용 — 평문 비밀번호를 세션에 남기지 않기 위해 미리 인코딩해서 들고 있는다. */
	public String encodePassword(String rawPassword) {
		return passwordEncoder.encode(rawPassword);
	}

	/**
	 * 이메일 회원가입 2단계 — 이메일 인증(OTP)까지 통과한 뒤에만 호출된다. 이 시점에 실제 계정을 만든다.
	 * 인증 대기 중(발송~확인 사이) 다른 경로로 같은 이메일이 먼저 가입됐을 수 있어 마지막으로 한 번 더
	 * 중복을 확인한다 — DB 유니크 제약(uk_users_oauth)이 최종 방어선이지만, 그전에 사람이 읽을 메시지로 막는다.
	 */
	@Transactional
	public UserEntity completeEmailSignup(String email, String encodedPassword) {
		if (userRepository.findByOauthProviderAndOauthId("email", email).isPresent()
				|| socialAccountRepository.existsByProviderAndProviderId("email", email)) {
			throw new IllegalStateException("이미 가입된 이메일입니다.");
		}

		UserEntity user = UserEntity.builder()
				.oauthProvider("email")
				.oauthId(email)
				.email(email)
				.password(encodedPassword)
				.role(UserEntity.ROLE_USER)
				.build();
		userRepository.save(user);

		// 소셜/이메일 계정 연동 테이블에도 등록
		SocialAccountEntity socialAccount = SocialAccountEntity.builder()
				.user(user)
				.provider("email")
				.providerId(email)
				.connectedEmail(email)
				.build();
		socialAccountRepository.save(socialAccount);

		return user;
	}

	@Transactional(readOnly = true)
	public UserEntity getUserForSignup(Long userId) {
		return findUserOrThrow(userId);
	}

	public boolean isValidNickname(String nickname) {
		String trimmedNickname = nickname == null ? "" : nickname.trim();
		return trimmedNickname.length() >= 2 && trimmedNickname.length() <= 10;
	}

	/** 입력값에서 숫자만 뽑아 휴대폰 형식을 검사한다 (util 위임). */
	public boolean isValidPhone(String phone) {
		return PhoneUtil.isValid(phone);
	}

	/** "01012345678" → "010-1234-5678". 저장·표시는 이 형식으로 통일 (util 위임). */
	public static String formatPhone(String phone) {
		return PhoneUtil.format(phone);
	}

	/** 휴대폰 인증(OTP) 목업용 6자리 코드 생성 (2026-09-24, AuthController#sendOtp 참고). */
	public static String generateOtpCode() {
		return String.format("%06d", java.util.concurrent.ThreadLocalRandom.current().nextInt(1_000_000));
	}

	/**
	 * 회원가입 정보 저장 및 역할(소비자/사장님) 분기 처리 중 저장 부분.
	 * - 소비자 선택: profileCompleted=true, role=USER 확정 (분기 자체는 컨트롤러가 userType으로 처리)
	 * - 약관: 이 메서드가 호출되는 시점 = 필수 약관(이용약관·개인정보) 동의를 이미 통과한 상태라
	 *   terms/privacy는 true로 확정하고, 마케팅 수신만 화면에서 받은 값을 그대로 저장한다.
	 */
	@Transactional
	public void completeSignup(Long userId, String nickname, String phone, String region, boolean marketingAgreed) {
		String trimmedNickname = nickname == null ? "" : nickname.trim();
		UserEntity user = findUserOrThrow(userId);
		user.setNickname(trimmedNickname);
		user.setPhone(formatPhone(phone));
		user.setRegion(region == null || region.isBlank() ? null : region.trim());
		user.setProfileCompleted(true);
		user.setRole(UserEntity.ROLE_USER); // 승인 전까지는 기본 USER 권한 유지

		user.setTermsAgreed(true);
		user.setPrivacyAgreed(true);
		user.setMarketingAgreed(marketingAgreed);
		user.setAgreedAt(LocalDateTime.now());

		userRepository.save(user);
	}

	/** 다른 계정이 이미 이 번호로 가입했는지 (가입 완료 단계에서 컨트롤러가 미리 확인 — 최종 방어는 DB 유니크 제약). */
	@Transactional(readOnly = true)
	public boolean isPhoneTaken(String phone) {
		return userRepository.existsByPhone(formatPhone(phone));
	}

	/** 연동 대상 기존 계정 정보 DTO */
	public record ExistingAccountInfo(Long userId, String primaryProvider, String providerDisplayName, String maskedEmail) {}

	/** 공급자 코드("naver", "kakao", "google", "line", "email")를 한글 친화적 명칭으로 변환 */
	public static String getProviderDisplayName(String provider) {
		if (provider == null) return "소셜";
		return switch (provider.toLowerCase()) {
			case "naver" -> "네이버";
			case "kakao" -> "카카오";
			case "google" -> "구글";
			case "line" -> "라인";
			case "email" -> "이메일";
			default -> provider;
		};
	}

	/**
	 * 해당 휴대폰 번호로 이미 가입을 마친 기존 회원이 존재하는지 확인하고,
	 * 연동 모달에 표시할 정보(가입 수단 명칭 등)를 반환한다.
	 */
	@Transactional(readOnly = true)
	public Optional<ExistingAccountInfo> findExistingAccountByPhone(String phone, Long currentUserId) {
		if (phone == null || !isValidPhone(phone)) {
			return Optional.empty();
		}
		return userRepository.findByPhone(formatPhone(phone))
				.filter(u -> currentUserId == null || !u.getId().equals(currentUserId))
				.map(u -> new ExistingAccountInfo(
						u.getId(),
						u.getOauthProvider(),
						getProviderDisplayName(u.getOauthProvider()),
						maskEmail(u.getEmail())
				));
	}

	/**
	 * 이 이메일로 이미 가입을 마친 "다른 provider" 계정이 있는지 확인한다.
	 * 추가됨 (2026-09-24, 코드 감사) — 이메일 가입의 중복 검사(validateEmailSignupInput)는
	 * provider="email"인 행끼리만 겹치는지 봐서, 구글로 먼저 가입한 이메일로 또 이메일+비밀번호
	 * 가입을 시도하면 완전히 별개인 계정이 하나 더 생겨버리는 구멍이 있었다(같은 사람의 예약·절약
	 * 내역이 계정 두 개로 쪼개짐). provider="email"인 매치는 이미 그 검사에서 걸러지므로, 여기 걸리는
	 * 건 전부 소셜(구글/카카오/라인) 계정이다 — 그래서 재인증도 비밀번호 대조가 아니라 실제 소셜
	 * 재로그인 하나만 있으면 된다(AuthController#prepareEmailSignupLink 참고).
	 */
	@Transactional(readOnly = true)
	public Optional<ExistingAccountInfo> findExistingAccountByEmail(String email) {
		if (email == null || email.isBlank()) {
			return Optional.empty();
		}
		return userRepository.findFirstByEmail(email.trim())
				.filter(u -> !"email".equalsIgnoreCase(u.getOauthProvider()))
				.map(u -> new ExistingAccountInfo(
						u.getId(),
						u.getOauthProvider(),
						getProviderDisplayName(u.getOauthProvider()),
						maskEmail(u.getEmail())
				));
	}

	/**
	 * 소셜 계정에 이메일+비밀번호 로그인 수단을 추가한다 — 실제 소셜 재로그인으로 본인 확인이 끝난
	 * 뒤에만 호출된다(OAuth2LoginSuccessHandler). 새 계정을 만들거나 지우는 게 아니라, 기존 계정에
	 * 로그인 수단 하나를 더 얹는 것뿐이라 linkSocialAccount*와 달리 병합 로직이 필요 없다.
	 */
	@Transactional
	public UserEntity linkEmailPasswordToAccount(Long targetUserId, String email, String encodedPasswordHash) {
		UserEntity target = userRepository.findById(targetUserId)
				.orElseThrow(() -> new IllegalArgumentException("연동 대상 계정을 찾을 수 없습니다."));
		if (UserEntity.STATUS_SUSPENDED.equals(target.getStatus())) {
			throw new IllegalStateException("정지된 계정에는 연동할 수 없습니다.");
		}

		if (!socialAccountRepository.existsByProviderAndProviderId("email", email)) {
			socialAccountRepository.save(SocialAccountEntity.builder()
					.user(target)
					.provider("email")
					.providerId(email)
					.connectedEmail(email)
					.build());
		}
		target.setPassword(encodedPasswordHash);
		if (target.getEmail() == null || target.getEmail().isBlank()) {
			target.setEmail(email);
		}
		userRepository.save(target);
		return target;
	}

	/**
	 * 1:N 멀티 소셜 계정 연동 (Account Linking) — 비밀번호 재인증 경로.
	 * targetUser가 이메일+비밀번호 로그인을 쓰는 계정일 때만 허용한다. 전화번호만으로는 본인 확인이
	 * 되지 않으므로(전화번호는 실명인증 없이 사용자가 그냥 입력한 값 — UserEntity.phone 주석 참고)
	 * 반드시 대상 계정의 실제 비밀번호를 확인한 뒤에만 병합한다.
	 */
	@Transactional
	public UserEntity linkSocialAccountWithPassword(Long currentPendingUserId, String phone, String rawPassword) {
		UserEntity pendingUser = loadPendingUser(currentPendingUserId);
		UserEntity targetUser = loadTargetUserByPhone(phone);

		if (targetUser.getPassword() == null || targetUser.getPassword().isBlank()) {
			throw new IllegalStateException("비밀번호 인증을 지원하지 않는 계정입니다. 해당 계정으로 다시 로그인해 본인 확인이 필요합니다.");
		}
		if (rawPassword == null || !passwordEncoder.matches(rawPassword, targetUser.getPassword())) {
			throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
		}

		return mergeAccounts(pendingUser, targetUser);
	}

	/**
	 * 1:N 멀티 소셜 계정 연동 (Account Linking) — 소셜 재인증 경로.
	 * 브라우저가 대상 계정의 원래 소셜 로그인(카카오/구글/라인)을 다시 완료해서 본인임을 증명한
	 * *이후에만* OAuth2LoginSuccessHandler가 호출한다 — verifiedTargetUserId는 전화번호 대조까지
	 * 마친, 이미 인증된 UserEntity의 id다. 컨트롤러가 사용자 입력(전화번호)만으로 직접 호출할 수
	 * 없도록 이 메서드는 전화번호 조회를 하지 않고 id로만 대상을 찾는다.
	 */
	@Transactional
	public UserEntity linkSocialAccountAfterReauth(Long currentPendingUserId, Long verifiedTargetUserId) {
		UserEntity pendingUser = loadPendingUser(currentPendingUserId);
		UserEntity targetUser = userRepository.findById(verifiedTargetUserId)
				.orElseThrow(() -> new IllegalArgumentException("연동 대상 계정을 찾을 수 없습니다."));

		if (UserEntity.STATUS_SUSPENDED.equals(targetUser.getStatus())) {
			throw new IllegalStateException("정지된 계정에는 연동할 수 없습니다.");
		}

		return mergeAccounts(pendingUser, targetUser);
	}

	private UserEntity loadPendingUser(Long currentPendingUserId) {
		if (currentPendingUserId == null) {
			throw new IllegalArgumentException("로그인된 세션 정보가 없습니다.");
		}
		UserEntity pendingUser = userRepository.findById(currentPendingUserId)
				.orElseThrow(() -> new IllegalArgumentException("가입 대기 중인 계정을 찾을 수 없습니다."));
		if (pendingUser.isProfileCompleted()) {
			throw new IllegalStateException("이미 가입이 완료된 계정은 연동 대상이 아닙니다.");
		}
		return pendingUser;
	}

	private UserEntity loadTargetUserByPhone(String phone) {
		UserEntity targetUser = userRepository.findByPhone(formatPhone(phone))
				.orElseThrow(() -> new IllegalArgumentException("해당 전화번호로 등록된 기존 계정을 찾을 수 없습니다: " + phone));
		if (UserEntity.STATUS_SUSPENDED.equals(targetUser.getStatus())) {
			throw new IllegalStateException("정지된 계정에는 연동할 수 없습니다.");
		}
		return targetUser;
	}

	/** 위 두 진입점이 각자의 본인 확인을 마친 뒤 공유하는 실제 병합 로직. */
	private UserEntity mergeAccounts(UserEntity pendingUser, UserEntity targetUser) {
		if (pendingUser.getId().equals(targetUser.getId())) {
			return targetUser;
		}

		// 1. pendingUser에 연결된 소셜 계정 정보 수집 (행별 구분용 connectedEmail도 함께 — 아니면
		// 마이페이지 목록에서 targetUser의 대표 이메일로 뭉뚱그려져 "같은 계정이 중복으로 뜬다"는
		// 오해를 낳는다. SocialAccountEntity.connectedEmail 주석 참고.)
		List<SocialAccountEntity> pendingSocials = socialAccountRepository.findAllByUserId(pendingUser.getId());
		List<String[]> socialPairs = new ArrayList<>();
		if (pendingSocials.isEmpty() && pendingUser.getOauthProvider() != null && pendingUser.getOauthId() != null) {
			socialPairs.add(new String[]{pendingUser.getOauthProvider(), pendingUser.getOauthId(), pendingUser.getEmail()});
		} else {
			for (SocialAccountEntity sa : pendingSocials) {
				socialPairs.add(new String[]{sa.getProvider(), sa.getProviderId(), sa.getConnectedEmail()});
			}
		}

		// 2. 임시 pendingUser 삭제 (DB 외래키 ON DELETE CASCADE 및 orphanRemoval 동작)
		userRepository.delete(pendingUser);
		userRepository.flush();

		// 3. 기존 targetUser의 기존 소셜 계정 등록 확인 및 새 소셜 정보 연결 저장
		if (targetUser.getOauthProvider() != null && targetUser.getOauthId() != null
				&& !socialAccountRepository.existsByProviderAndProviderId(targetUser.getOauthProvider(), targetUser.getOauthId())) {
			socialAccountRepository.save(SocialAccountEntity.builder()
					.user(targetUser)
					.provider(targetUser.getOauthProvider())
					.providerId(targetUser.getOauthId())
					.connectedEmail(targetUser.getEmail())
					.build());
		}

		for (String[] pair : socialPairs) {
			String provider = pair[0];
			String providerId = pair[1];
			String connectedEmail = pair[2];
			if (!socialAccountRepository.existsByProviderAndProviderId(provider, providerId)) {
				socialAccountRepository.save(SocialAccountEntity.builder()
						.user(targetUser)
						.provider(provider)
						.providerId(providerId)
						.connectedEmail(connectedEmail)
						.build());
			}
		}
		socialAccountRepository.flush();

		return targetUser;
	}

	/**
	 * 회원의 연동된 소셜 계정 목록을 반환한다.
	 * 레거시 유저 등으로 소셜 테이블 행이 없는 경우 자동으로 1건을 보정 등록한다.
	 */
	@Transactional
	public List<SocialAccountEntity> getLinkedSocialAccounts(Long userId) {
		if (userId == null) return List.of();
		UserEntity user = userRepository.findById(userId).orElse(null);
		if (user == null) return List.of();

		// 변경됨 (2026-09-24, 코드 감사) — 예전엔 provider="email"이면 이 자동 보정에서 제외하고
		// mypageView/edit.html이 그 대신 user.password 유무만 보고 별도의 고정 "기본 계정" 줄을
		// 그렸는데, 그러면 구글로 가입한 뒤 이메일 로그인을 나중에 추가한 계정처럼 실제 최초 가입
		// 수단이 구글인데도 "이메일 = 기본 계정"으로 잘못 표시되는 문제가 있었다. 이제 이메일도 똑같이
		// 이 목록에 포함시키고, "기본 계정" 표시는 화면에서 user.oauthProvider/oauthId와 비교해서 정한다.
		List<SocialAccountEntity> socials = socialAccountRepository.findAllByUserId(userId);
		if (socials.isEmpty() && user.getOauthProvider() != null && user.getOauthId() != null) {
			SocialAccountEntity autoCreated = SocialAccountEntity.builder()
					.user(user)
					.provider(user.getOauthProvider())
					.providerId(user.getOauthId())
					.connectedEmail(user.getEmail())
					.build();
			socialAccountRepository.save(autoCreated);
			return List.of(autoCreated);
		}
		socials.sort(Comparator
				.comparingInt((SocialAccountEntity sa) -> providerSortRank(sa.getProvider()))
				.thenComparing(SocialAccountEntity::getId));
		return socials;
	}

	// 추가됨 (2026-09-23) — 마이페이지 "연동된 로그인 계정" 목록 정렬 순서. CLAUDE.md 기준 실제 등록된
	// 소셜 3종(카카오/구글/라인) 순서를 고정하고, 그 외(naver 등 레거시/미지원 값)는 뒤로 보낸다.
	private static final List<String> PROVIDER_SORT_ORDER = List.of("kakao", "google", "line");

	private static int providerSortRank(String provider) {
		int idx = PROVIDER_SORT_ORDER.indexOf(provider == null ? "" : provider.toLowerCase());
		return idx == -1 ? PROVIDER_SORT_ORDER.size() : idx;
	}

	/**
	 * 회원이 소셜 계정 연동을 해제할 수 있는지 검사한다.
	 * 최소 1개의 로그인 수단(소셜 또는 이메일 비밀번호)은 유지되어야 한다.
	 */
	@Transactional(readOnly = true)
	public boolean canUnlinkSocialAccount(Long userId) {
		if (userId == null) return false;
		UserEntity user = userRepository.findById(userId).orElse(null);
		if (user == null) return false;

		List<SocialAccountEntity> socials = socialAccountRepository.findAllByUserId(userId);
		int socialCount = socials.size();
		if (socialCount == 0 && user.getOauthProvider() != null && !"email".equalsIgnoreCase(user.getOauthProvider())) {
			socialCount = 1;
		}
		boolean hasPassword = (user.getPassword() != null && !user.getPassword().isBlank());
		int totalMethods = socialCount + (hasPassword ? 1 : 0);
		return totalMethods > 1;
	}

	/**
	 * 특정 소셜 계정 연동을 해제(삭제)한다.
	 * - 최소 1개 로그인 수단 보장
	 * - 본인 소유 소셜 계정인지 검증
	 * - 대표 제공자(UserEntity.oauthProvider) 해제 시 남은 수단으로 갱신
	 */
	@Transactional
	public void unlinkSocialAccount(Long userId, Long socialAccountId) {
		if (userId == null || socialAccountId == null) {
			throw new IllegalArgumentException("필수 파라미터가 누락되었습니다.");
		}
		UserEntity user = findUserOrThrow(userId);
		if (!canUnlinkSocialAccount(userId)) {
			throw new IllegalStateException("최소 1개의 로그인 수단은 유지되어야 합니다.");
		}

		SocialAccountEntity targetSocial = socialAccountRepository.findById(socialAccountId)
				.orElseThrow(() -> new IllegalArgumentException("해당 소셜 계정 연동 정보를 찾을 수 없습니다."));

		if (!targetSocial.getUser().getId().equals(userId)) {
			throw new IllegalArgumentException("본인의 소셜 계정만 연동 해제할 수 있습니다.");
		}

		String unlinkedProvider = targetSocial.getProvider();
		String unlinkedProviderId = targetSocial.getProviderId();

		socialAccountRepository.delete(targetSocial);
		socialAccountRepository.flush();

		boolean userChanged = false;

		// 추가됨 (2026-09-24, 코드 감사) — 해제한 게 이메일 로그인 수단이면 비밀번호도 같이 지운다.
		// 안 지우면 SocialAccountEntity(provider="email") 행은 없는데 UserEntity.password만 남아서,
		// 마이페이지의 "이메일 로그인" 기본 표시(password != null 기준)가 실제로는 로그인할 수 없는
		// 유령 로그인 수단을 계속 보여주는 상태가 된다.
		if ("email".equalsIgnoreCase(unlinkedProvider)) {
			user.setPassword(null);
			userChanged = true;
		}

		// 만약 해제한 소셜 계정이 UserEntity의 대표 oauthProvider/oauthId와 일치한다면
		// 남아있는 다른 소셜 계정(또는 이메일)으로 대표 제공자 정보를 갱신한다.
		if (unlinkedProvider.equalsIgnoreCase(user.getOauthProvider())
				&& unlinkedProviderId.equals(user.getOauthId())) {
			List<SocialAccountEntity> remaining = socialAccountRepository.findAllByUserId(userId);
			if (!remaining.isEmpty()) {
				SocialAccountEntity nextPrimary = remaining.get(0);
				user.setOauthProvider(nextPrimary.getProvider());
				user.setOauthId(nextPrimary.getProviderId());
				userChanged = true;
			} else if (user.getPassword() != null) {
				user.setOauthProvider("email");
				user.setOauthId(user.getEmail());
				userChanged = true;
			}
		}

		if (userChanged) {
			userRepository.save(user);
		}
	}

	/**
	 * 부가정보 입력을 마치지 않고 가입을 취소 — 방금 만든 계정을 삭제한다.
	 * 소셜 첫 로그인은 로그인 시점에 계정 행이 생기는데(SocialUserProvisioningService), 아직 약관 동의
	 * 전이라 계정을 남길 이유가 없고 브랜드 뉴라 딸린 데이터도 없다.
	 * 안전장치: profile_completed=false + role=USER 일 때만 지운다.
	 */
	@Transactional
	public boolean cancelIncompleteSignup(Long userId) {
		return userRepository.findById(userId)
				.filter(u -> !u.isProfileCompleted() && UserEntity.ROLE_USER.equals(u.getRole()))
				.map(u -> {
					userRepository.deleteById(u.getId());
					return true;
				})
				.orElse(false);
	}

	// ---- 이메일(아이디) 찾기 ----

	/** found=false면 그 번호로 가입한 계정 없음. provider가 "email"이 아니면 소셜 계정(그쪽으로 로그인 안내). */
	public record FindEmailResult(boolean found, String maskedEmail, String provider) {}

	/** 휴대폰 번호로 가입 계정을 찾아 마스킹된 이메일·가입수단을 돌려준다. (SMS 인증 없는 조회 — rate limit은 컨트롤러) */
	@Transactional(readOnly = true)
	public FindEmailResult findEmailByPhone(String phone) {
		return userRepository.findByPhone(formatPhone(phone))
				.map(u -> new FindEmailResult(true, maskEmail(u.getEmail()), u.getOauthProvider()))
				.orElse(new FindEmailResult(false, null, null));
	}

	// ---- 비밀번호 재설정 (이메일 + 휴대폰 일치 시 바로 새 비번 설정) ----
	// 주의: SMS 인증이 없어 "이메일+휴대폰을 아는 사람"이면 재설정할 수 있다(보안상 약함). rate limit + 로깅으로 완화.

	/** 이메일 계정이고 등록된 휴대폰이 일치하면 userId를 돌려준다. 소셜 계정이거나 불일치면 empty. */
	@Transactional(readOnly = true)
	public java.util.Optional<Long> verifyForPasswordReset(String email, String phone) {
		String e = email == null ? "" : email.trim();
		String p = formatPhone(phone);
		return userRepository.findByOauthProviderAndOauthId("email", e)
				.filter(u -> u.getPhone() != null && u.getPhone().equals(p))
				.map(UserEntity::getId);
	}

	/** 새 비밀번호 저장. 8자 미만이면 false. */
	@Transactional
	public boolean resetPassword(Long userId, String newPassword) {
		if (newPassword == null || newPassword.length() < 8) {
			return false;
		}
		UserEntity user = findUserOrThrow(userId);
		user.setPassword(passwordEncoder.encode(newPassword));
		userRepository.save(user);
		return true;
	}

	/** 이메일 로컬파트 앞 4자만 남기고 마스킹. null·형식 이상이면 null. */
	public static String maskEmail(String email) {
		if (email == null || !email.contains("@")) {
			return null;
		}
		String[] parts = email.split("@", 2);
		String local = parts[0];
		String masked = local.length() <= 4 ? local : local.substring(0, 4);
		return masked + "***@" + parts[1];
	}

	public boolean isOwnerApplyValid(String storeName, String businessNumber, String category,
	                                  String address, String phone) {
		return !(storeName == null || storeName.isBlank() ||
				businessNumber == null || businessNumber.isBlank() ||
				category == null || category.isBlank() ||
				address == null || address.isBlank() ||
				phone == null || phone.isBlank())
				&& BusinessNumberUtil.isValid(businessNumber)
				&& StorePhoneUtil.isValid(phone);
	}

	public boolean isOwnerApplyValid(String storeName, String businessNumber, String category,
	                                  String address, String phone, String operatingHours) {
		return isOwnerApplyValid(storeName, businessNumber, category, address, phone)
				&& StoreHoursUtil.isValidFormat(operatingHours);
	}

	/**
	 * 점주 입점 신청서 제출 처리
	 * - 매장 정보 및 사업자 등록번호를 PENDING 상태로 저장
	 * - 기존 신청 건이 있으면 업데이트, 없으면 신규 생성
	 * - 운영자(ADMIN, WBS 7.2 송보미)의 심사/승인 후 role=OWNER로 전환됨
	 */
	@Transactional
	public StoreEntity ownerApply(Long userId, String storeName, String businessNumber, String category,
	                               String address, String phone, String operatingHours) {
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
		String trimmedHours = (operatingHours != null && !operatingHours.isBlank()) ? operatingHours.trim() : null;
		if (trimmedHours != null && !StoreHoursUtil.isValidFormat(trimmedHours)) {
			throw new IllegalArgumentException("영업시간 형식이 올바르지 않아요: " + trimmedHours);
		}
		store.setOperatingHours(trimmedHours);
		store.setApprovalStatus(StoreEntity.STATUS_PENDING); // 승인 대기 상태

		return storeRepository.save(store);
	}

	private UserEntity findUserOrThrow(Long userId) {
		return userRepository.findById(userId).orElseThrow();
	}
}

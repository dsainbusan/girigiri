package net.dsa.girigiri.service;

import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.domain.entity.UserEntity;
import net.dsa.girigiri.repository.StoreRepository;
import net.dsa.girigiri.repository.UserRepository;
import net.dsa.girigiri.util.StoreHoursUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

	/** 가입 결과 + 성공 시 생성된 계정(컨트롤러가 이 계정으로 자동 로그인시킴). 실패면 user는 null. */
	public record EmailSignupOutcome(EmailSignupResult status, UserEntity user) {
		static EmailSignupOutcome fail(EmailSignupResult status) {
			return new EmailSignupOutcome(status, null);
		}
	}

	private final UserRepository userRepository;
	private final StoreRepository storeRepository;
	private final PasswordEncoder passwordEncoder;

	/**
	 * 이메일 회원가입 처리 — 계정을 만들고 생성된 계정을 돌려준다. 컨트롤러가 이 계정으로 곧바로
	 * 자동 로그인시킨 뒤 부가정보 입력(/auth/signup)으로 보낸다.
	 * 변경됨 (2026-09-10) — 왜: 예전엔 계정만 만들고 로그인 화면으로 보내서 사용자가 방금 정한
	 * 비밀번호로 한 번 더 로그인하게 했다. 소셜 로그인의 최초 흐름(로그인 → 바로 /auth/signup)과
	 * 어긋나서, 이메일도 "가입 → 자동 로그인 → 부가정보 입력 → 최종 가입 완료"로 통일한다.
	 */
	@Transactional
	public EmailSignupOutcome emailSignup(String email, String password, String passwordConfirm) {
		String trimmedEmail = email == null ? "" : email.trim();
		if (!EMAIL_PATTERN.matcher(trimmedEmail).matches()) {
			return EmailSignupOutcome.fail(EmailSignupResult.INVALID_EMAIL);
		}
		if (password == null || password.length() < 8) {
			return EmailSignupOutcome.fail(EmailSignupResult.INVALID_PASSWORD);
		}
		if (!password.equals(passwordConfirm)) {
			return EmailSignupOutcome.fail(EmailSignupResult.PASSWORD_MISMATCH);
		}
		if (userRepository.findByOauthProviderAndOauthId("email", trimmedEmail).isPresent()) {
			return EmailSignupOutcome.fail(EmailSignupResult.DUPLICATE);
		}

		UserEntity user = UserEntity.builder()
				.oauthProvider("email")
				.oauthId(trimmedEmail)
				.email(trimmedEmail)
				.password(passwordEncoder.encode(password))
				.role(UserEntity.ROLE_USER)
				.build();
		userRepository.save(user);

		return new EmailSignupOutcome(EmailSignupResult.SUCCESS, user);
	}

	@Transactional(readOnly = true)
	public UserEntity getUserForSignup(Long userId) {
		return findUserOrThrow(userId);
	}

	public boolean isValidNickname(String nickname) {
		String trimmedNickname = nickname == null ? "" : nickname.trim();
		return trimmedNickname.length() >= 2 && trimmedNickname.length() <= 10;
	}

	/**
	 * 회원가입 정보 저장 및 역할(소비자/사장님) 분기 처리 중 저장 부분.
	 * - 소비자 선택: profileCompleted=true, role=USER 확정 (분기 자체는 컨트롤러가 userType으로 처리)
	 */
	@Transactional
	public void completeSignup(Long userId, String nickname, String region) {
		String trimmedNickname = nickname == null ? "" : nickname.trim();
		UserEntity user = findUserOrThrow(userId);
		user.setNickname(trimmedNickname);
		user.setRegion(region == null || region.isBlank() ? null : region.trim());
		user.setProfileCompleted(true);
		user.setRole(UserEntity.ROLE_USER); // 승인 전까지는 기본 USER 권한 유지
		userRepository.save(user);
	}

	public boolean isOwnerApplyValid(String storeName, String businessNumber, String category,
	                                  String address, String phone) {
		return !(storeName == null || storeName.isBlank() ||
				businessNumber == null || businessNumber.isBlank() ||
				category == null || category.isBlank() ||
				address == null || address.isBlank() ||
				phone == null || phone.isBlank());
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

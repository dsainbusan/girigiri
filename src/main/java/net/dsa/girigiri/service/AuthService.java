package net.dsa.girigiri.service;

import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.domain.entity.UserEntity;
import net.dsa.girigiri.repository.StoreRepository;
import net.dsa.girigiri.repository.UserRepository;
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

	private final UserRepository userRepository;
	private final StoreRepository storeRepository;
	private final PasswordEncoder passwordEncoder;

	/**
	 * 이메일 회원가입 처리 — 계정만 만들고 자동 로그인은 시키지 않는다(로그인 페이지로 보내서
	 * 방금 입력한 비밀번호로 직접 로그인하게 함 — Spring Security 수동 인증 코드 없이 단순하게 처리).
	 */
	@Transactional
	public EmailSignupResult emailSignup(String email, String password, String passwordConfirm) {
		String trimmedEmail = email == null ? "" : email.trim();
		if (!EMAIL_PATTERN.matcher(trimmedEmail).matches()) {
			return EmailSignupResult.INVALID_EMAIL;
		}
		if (password == null || password.length() < 8) {
			return EmailSignupResult.INVALID_PASSWORD;
		}
		if (!password.equals(passwordConfirm)) {
			return EmailSignupResult.PASSWORD_MISMATCH;
		}
		if (userRepository.findByOauthProviderAndOauthId("email", trimmedEmail).isPresent()) {
			return EmailSignupResult.DUPLICATE;
		}

		UserEntity user = UserEntity.builder()
				.oauthProvider("email")
				.oauthId(trimmedEmail)
				.email(trimmedEmail)
				.password(passwordEncoder.encode(password))
				.role(UserEntity.ROLE_USER)
				.build();
		userRepository.save(user);

		return EmailSignupResult.SUCCESS;
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
		store.setOperatingHours(operatingHours != null && !operatingHours.isBlank() ? operatingHours.trim() : null);
		store.setApprovalStatus(StoreEntity.STATUS_PENDING); // 승인 대기 상태

		return storeRepository.save(store);
	}

	private UserEntity findUserOrThrow(Long userId) {
		return userRepository.findById(userId).orElseThrow();
	}
}

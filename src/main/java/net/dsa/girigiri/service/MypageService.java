package net.dsa.girigiri.service;

import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.domain.entity.UserArchiveEntity;
import net.dsa.girigiri.domain.entity.UserEntity;
import net.dsa.girigiri.repository.ReservationRepository;
import net.dsa.girigiri.repository.SocialAccountRepository;
import net.dsa.girigiri.repository.UserArchiveRepository;
import net.dsa.girigiri.repository.UserBadgeRepository;
import net.dsa.girigiri.repository.UserRepository;
import net.dsa.girigiri.util.PhoneUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * 마이페이지 및 회원정보 관리 도메인 서비스 (2026-09-03, 레이어 규칙 2단계).
 *
 * MypageController에 흩어져 있던 Repository 직접 호출·검증·상태 변경 로직을 옮겨온다.
 */
@Service
@RequiredArgsConstructor
public class MypageService {

	// 변경됨 (2026-09-08, 코드 감사) — 왜: "ready"(결제완료+매장수락, 픽업 대기)가 빠져 있어서
	// 그 상태의 예약을 가진 회원이 탈퇴할 수 있었다 — ReservationService.INCOMPLETE_STATUSES로
	// 통일(다른 두 곳 SuperAdminMemberService/SuperAdminStoreService도 동일하게 맞춤).
	private static final List<String> INCOMPLETE_RESERVATION_STATUSES = ReservationService.INCOMPLETE_STATUSES;

	private final UserRepository userRepository;
	private final ReservationRepository reservationRepository;
	private final StoreAccessService storeAccessService;
	private final PasswordEncoder passwordEncoder;
	private final UserBadgeRepository userBadgeRepository;
	private final UserArchiveRepository userArchiveRepository;
	private final SocialAccountRepository socialAccountRepository;

	public enum ProfileUpdateResult { SUCCESS, INVALID_NICKNAME, INVALID_PHONE, PHONE_TAKEN }

	public enum PasswordChangeResult { SUCCESS, NOT_EMAIL_ACCOUNT, WRONG_CURRENT, TOO_SHORT }

	@Transactional(readOnly = true)
	public Optional<UserEntity> findUser(Long userId) {
		return userRepository.findById(userId);
	}

	public long calculateDaysJoined(UserEntity user) {
		long daysJoined = 1;
		if (user.getCreatedAt() != null) {
			daysJoined = ChronoUnit.DAYS.between(user.getCreatedAt().toLocalDate(), LocalDate.now()) + 1;
		}
		return daysJoined;
	}

	@Transactional(readOnly = true)
	public Optional<StoreEntity> findOwnedStore(Long userId) {
		return storeAccessService.findMyStore(userId);
	}

	/**
	 * 회원정보 수정 처리 (닉네임·활동지역·휴대폰). 검증 결과를 enum으로 돌려주고 컨트롤러가 리다이렉트를 정한다.
	 * 휴대폰은 비우면 null(미입력)로 두고, 값이 있으면 형식 검사 + 다른 계정이 쓰는 번호인지 확인한다
	 * (uk_users_phone 유니크 제약과 맞춤).
	 */
	@Transactional
	public ProfileUpdateResult updateProfile(Long userId, String nickname, String phone, String region) {
		String trimmedNickname = nickname == null ? "" : nickname.trim();
		if (trimmedNickname.length() < 2 || trimmedNickname.length() > 10) {
			return ProfileUpdateResult.INVALID_NICKNAME;
		}

		String normalizedPhone = null;
		if (phone != null && !phone.isBlank()) {
			if (!PhoneUtil.isValid(phone)) {
				return ProfileUpdateResult.INVALID_PHONE;
			}
			normalizedPhone = PhoneUtil.format(phone);
			boolean takenByOther = userRepository.findByPhone(normalizedPhone)
					.filter(other -> !other.getId().equals(userId))
					.isPresent();
			if (takenByOther) {
				return ProfileUpdateResult.PHONE_TAKEN;
			}
		}

		UserEntity user = userRepository.findById(userId).orElseThrow();
		user.setNickname(trimmedNickname);
		user.setPhone(normalizedPhone);
		user.setRegion(region == null || region.isBlank() ? null : region.trim());
		userRepository.save(user);

		return ProfileUpdateResult.SUCCESS;
	}

	/**
	 * 비밀번호 변경 — 이메일 계정만. 현재 비밀번호가 맞아야 하고 새 비밀번호는 8자 이상.
	 * 소셜 계정(kakao/google/line)은 비밀번호가 없어 대상이 아니다.
	 */
	@Transactional
	public PasswordChangeResult changePassword(Long userId, String currentPassword, String newPassword) {
		UserEntity user = userRepository.findById(userId).orElseThrow();
		if (!"email".equals(user.getOauthProvider()) || user.getPassword() == null) {
			return PasswordChangeResult.NOT_EMAIL_ACCOUNT;
		}
		if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPassword())) {
			return PasswordChangeResult.WRONG_CURRENT;
		}
		if (newPassword == null || newPassword.length() < 8) {
			return PasswordChangeResult.TOO_SHORT;
		}
		user.setPassword(passwordEncoder.encode(newPassword));
		userRepository.save(user);
		return PasswordChangeResult.SUCCESS;
	}

	/**
	 * 미완료 예약(결제대기/진행중, 아직 픽업·취소·노쇼 처리가 안 된 건)이 있으면 탈퇴를 막는다 —
	 * 손님 입장에서 결제만 하고 계정이 사라지면 픽업/환불 처리가 불가능해지고, 점주 입장에서도
	 * 매장에 남은 예약이 붕 뜨기 때문. 그 외 케이스(예: 점주가 매장을 보유한 채 탈퇴)는 기존 그대로
	 * 둔다 — StoreEntity.ownerId 고아 데이터 문제는 FK 매핑/ERD 확정 전이라 별도 논의 필요.
	 */
	@Transactional(readOnly = true)
	public boolean canWithdraw(Long userId) {
		if (reservationRepository.existsByUserIdAndStatusIn(userId, INCOMPLETE_RESERVATION_STATUSES)) {
			return false;
		}

		StoreEntity store = storeAccessService.findMyStore(userId).orElse(null);
		if (store != null
				&& reservationRepository.existsByStoreIdAndStatusIn(store.getId(), INCOMPLETE_RESERVATION_STATUSES)) {
			return false;
		}

		return true;
	}

	/**
	 * 변경됨 (2026-09-22, 보미 피드백 반영 — soft delete 정책) — 왜: 예전엔 여기서 users 로우를
	 * 바로 hard delete했는데, 개인정보보호법상 다른 법령(전자상거래법 등)이 요구하는 보존기간 동안은
	 * 최소한의 식별 정보를 분리보관해야 한다. 이제는 (1) 최소 정보를 user_archive에 남기고
	 * (2) users 로우 자체는 deletedAt만 채워서 소프트 삭제한다 — 실제 물리 삭제는
	 * UserPurgeScheduler가 보존기간이 지난 뒤에 처리한다.
	 *
	 * user_badge는 여전히 여기서 지운다 — users를 참조하는 FK가 없는 건 예전과 같지만, 지금은
	 * users 로우 자체가 (소프트 삭제 상태로) 남아있어서 "고아 데이터 방지" 때문이 아니라, 탈퇴 회원의
	 * 뱃지 기록이 더 이상 어디에도 쓰이지 않는 데이터라 정리 차원에서 지운다.
	 *
	 * 추가됨 (2026-09-28, 코드 리뷰 — 문창호) — 왜: soft delete로 users 로우를 그대로 남기면서
	 * oauth_provider+oauth_id/phone 값도 원본 그대로 둬서, 탈퇴한 사람이 같은 소셜 계정·전화번호로
	 * 다시 가입하려 하면 findByOauthProviderAndOauthId/existsByPhone이 이 탈퇴 행을 그대로 찾아내
	 * "이미 가입됨"/"탈퇴한 계정"으로 막아버렸다(실제 DB로 재현 확인함 — 보존기간 5년 내내 재가입
	 * 불가). 진짜 값은 이미 위에서 user_archive에 남겼으니, users 쪽은 유니크 제약만 피하도록
	 * oauthId를 이 행 전용 더미값으로 바꾸고 phone은 비운다(uk_users_phone은 NULL 여러 개를 허용).
	 */
	@Transactional
	public void withdraw(Long userId) {
		UserEntity user = userRepository.findById(userId).orElseThrow();
		LocalDateTime now = LocalDateTime.now();

		userArchiveRepository.save(UserArchiveEntity.builder()
				.originalUserId(user.getId())
				.oauthProvider(user.getOauthProvider())
				.email(user.getEmail())
				.phone(user.getPhone())
				.joinedAt(user.getCreatedAt())
				.withdrawnAt(now)
				.build());

		userBadgeRepository.deleteByUserId(userId);

		// 추가됨 (2026-09-29, 코드 리뷰 — 문창호) — 왜: oauthId를 더미값으로 바꿔도 재가입이 여전히
		// 막히는 게 실제로 재현됐다. SocialUserProvisioningService.findOrCreate()가 users를 바로
		// 조회하기 전에 1:N 소셜 연동 테이블(user_social_accounts)을 먼저 조회하는데, 거기 남아있는
		// providerId는 원래 구글/카카오 ID 그대로라 탈퇴한 이 계정을 다시 찾아내 버린다. 이 테이블은
		// UserEntity보다 나중에(2026-09-22~24) 추가된 기능이라 soft-delete 패치가 만들어질 때는
		// 존재를 몰랐던 것으로 보인다 — 로그인 연동 정보이므로 탈퇴 시 통째로 지운다.
		socialAccountRepository.deleteAll(socialAccountRepository.findAllByUserId(userId));

		user.setOauthId("withdrawn_" + user.getId());
		user.setPhone(null);
		user.setDeletedAt(now);
		userRepository.save(user);
	}
}

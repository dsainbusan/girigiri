package net.dsa.girigiri.service;

import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.domain.entity.UserEntity;
import net.dsa.girigiri.repository.ReservationRepository;
import net.dsa.girigiri.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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

	private static final List<String> INCOMPLETE_RESERVATION_STATUSES = List.of("pending", "confirmed");

	private final UserRepository userRepository;
	private final ReservationRepository reservationRepository;
	private final StoreAccessService storeAccessService;

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
	 * 회원정보 수정 처리. 닉네임 검증 실패 시 false를 돌려주고, 컨트롤러가 리다이렉트를 결정한다.
	 */
	@Transactional
	public boolean updateProfile(Long userId, String nickname, String region) {
		String trimmedNickname = nickname == null ? "" : nickname.trim();
		if (trimmedNickname.length() < 2 || trimmedNickname.length() > 10) {
			return false;
		}

		UserEntity user = userRepository.findById(userId).orElseThrow();
		user.setNickname(trimmedNickname);
		user.setRegion(region == null || region.isBlank() ? null : region.trim());
		userRepository.save(user);

		return true;
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

	@Transactional
	public void withdraw(Long userId) {
		userRepository.deleteById(userId);
	}
}

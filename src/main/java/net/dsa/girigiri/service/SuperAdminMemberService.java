package net.dsa.girigiri.service;

import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.MemberActivityRowDto;
import net.dsa.girigiri.domain.entity.ComplaintEntity;
import net.dsa.girigiri.domain.entity.InquiryEntity;
import net.dsa.girigiri.domain.entity.NotificationEntity;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.domain.entity.UserArchiveEntity;
import net.dsa.girigiri.domain.entity.UserEntity;
import net.dsa.girigiri.repository.ComplaintRepository;
import net.dsa.girigiri.repository.InquiryRepository;
import net.dsa.girigiri.repository.ReservationRepository;
import net.dsa.girigiri.repository.SocialAccountRepository;
import net.dsa.girigiri.repository.UserArchiveRepository;
import net.dsa.girigiri.repository.UserBadgeRepository;
import net.dsa.girigiri.repository.UserRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 슈퍼어드민 "회원 관리" 도메인 서비스 (2026-09-03, 레이어 규칙 2단계 — SuperAdminController 도메인 분리).
 *
 * SuperAdminController의 회원 관리 관련 Repository 직접 호출·검증·상태 변경 로직을 옮겨온다.
 */
@Service
@RequiredArgsConstructor
public class SuperAdminMemberService {

	// MypageService.withdraw()의 자진 탈퇴와 동일한 가드 — 미완료 예약이 있으면 탈퇴(삭제)를 막는다.
	// (2026-09-08, 코드 감사) ReservationService.INCOMPLETE_STATUSES로 통일 — "ready" 누락 수정.
	private static final List<String> INCOMPLETE_RESERVATION_STATUSES = ReservationService.INCOMPLETE_STATUSES;

	private final UserRepository userRepository;
	private final ReservationRepository reservationRepository;
	private final InquiryRepository inquiryRepository;
	private final ComplaintRepository complaintRepository;
	private final LookupService lookupService;
	private final StoreAccessService storeAccessService;
	private final UserBadgeRepository userBadgeRepository;
	private final UserArchiveRepository userArchiveRepository;
	private final SocialAccountRepository socialAccountRepository;
	// 추가됨 (2026-09-29, 담당: 송보미) — suspend/bulkSuspend에서 정지 사유를 당사자에게 알림으로 전달.
	private final NotificationService notificationService;

	// 변경됨 — 왜: 필터 탭을 "전체/일반 회원/점주 회원/정지 회원"으로 바꿔달라는 요청 — 역할 기준
	// 필터가 USER/ADMIN(운영자)에서 USER(일반 회원)/OWNER(점주 회원)로 바뀌었다. 운영자 계정은 수가
	// 적고 이 화면의 주 관리 대상(소비자·점주)이 아니라서 전용 탭은 뺐다 — "전체"에서는 여전히 보임.
	public String normalizeFilter(String filter) {
		return "USER".equals(filter) || "OWNER".equals(filter) || "SUSPENDED".equals(filter) ? filter : null;
	}

	@Transactional(readOnly = true)
	public List<UserEntity> findFilteredMembers(String q, String normalizedFilter) {
		Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");
		String keyword = q == null ? "" : q.trim();

		List<UserEntity> users = keyword.isEmpty()
				? userRepository.findAll(sort)
				: userRepository.findByNicknameContainingIgnoreCaseOrEmailContainingIgnoreCase(keyword, keyword, sort);

		// 추가됨 (2026-09-22, 보미 피드백 반영 — soft delete 정책) — 왜: withdraw()가 더 이상
		// users 로우를 즉시 지우지 않고 deletedAt만 채우기 때문에, 여기서 걸러주지 않으면 탈퇴한
		// 회원이 회원 목록에 계속 보이게 된다.
		users = users.stream().filter(u -> u.getDeletedAt() == null).toList();

		if ("USER".equals(normalizedFilter)) {
			return users.stream().filter(u -> UserEntity.ROLE_USER.equals(u.getRole())).toList();
		}
		if ("OWNER".equals(normalizedFilter)) {
			return users.stream().filter(u -> UserEntity.ROLE_OWNER.equals(u.getRole())).toList();
		}
		if ("SUSPENDED".equals(normalizedFilter)) {
			return users.stream().filter(u -> UserEntity.STATUS_SUSPENDED.equals(u.getStatus())).toList();
		}
		return users;
	}

	/**
	 * 표 왼쪽 체크박스로 여러 명을 골라 한 번에 정지시키는 일괄 액션.
	 *
	 * 변경됨 (2026-09-29, 담당: 송보미) — 왜: 정지 사유 입력이 없어서 당사자가 왜 정지됐는지 알 방법이
	 * 없었다(코드 감사로 확인). 사유를 DB 컬럼으로 남기지는 않고(스키마 변경 필요 — 팀 합의 필요)
	 * 알림 메시지로 바로 전달한다.
	 */
	@Transactional
	public void bulkSuspend(List<Long> ids, String reason) {
		if (ids != null && !ids.isEmpty()) {
			List<UserEntity> targets = userRepository.findAllById(ids);
			targets.forEach(u -> u.setStatus(UserEntity.STATUS_SUSPENDED));
			userRepository.saveAll(targets);
			targets.forEach(u -> notifySuspended(u.getId(), reason));
		}
	}

	private void notifySuspended(Long userId, String reason) {
		String trimmedReason = reason == null ? "" : reason.trim();
		String message = trimmedReason.isEmpty()
				? "이용이 정지됐어요. 자세한 사유는 1:1 문의로 확인해 주세요."
				: "이용이 정지됐어요. 사유: " + trimmedReason;
		// sourceKey를 안 주는 이유: 같은 회원이 정지→해제→재정지될 수 있어서, id 기준 고정 키를 쓰면
		// 두 번째 정지 알림이 "중복 이벤트"로 오인돼 조용히 씹힌다(NotificationService#createNotification
		// 참고) — 관리자가 직접 누르는 1회성 액션이라 스케줄러의 중복 실행 방지 목적의 dedup이 필요 없다.
		notificationService.createNotification(userId, NotificationEntity.TYPE_ACCOUNT_SUSPENDED, message,
				"/user/support", null);
	}

	@Transactional
	public void bulkUnsuspend(List<Long> ids) {
		if (ids != null && !ids.isEmpty()) {
			List<UserEntity> targets = userRepository.findAllById(ids);
			targets.forEach(u -> u.setStatus(UserEntity.STATUS_ACTIVE));
			userRepository.saveAll(targets);
		}
	}

	/**
	 * "신고자/문의자 상세에 이전에 문의한 거 정리된 리스트도 보여달라"는 요청 — 이 회원이 작성자인
	 * 문의와 신고자인 신고를 하나로 합쳐 최신순으로 보여준다.
	 */
	@Transactional(readOnly = true)
	public List<MemberActivityRowDto> getMemberActivity(Long userId) {
		Sort byNewest = Sort.by(Sort.Direction.DESC, "createdAt");
		List<MemberActivityRowDto> activity = new ArrayList<>();
		for (InquiryEntity i : inquiryRepository.findByUserId(userId, byNewest)) {
			activity.add(new MemberActivityRowDto("문의", i.getTitle(), i.getCreatedAt(), "/superadmin/inquiries/" + i.getId()));
		}
		for (ComplaintEntity c : complaintRepository.findByReporterId(userId, byNewest)) {
			activity.add(new MemberActivityRowDto("환불 신청", c.getReason(), c.getCreatedAt(), "/superadmin/complaints/" + c.getId()));
		}
		activity.sort(Comparator.comparing(MemberActivityRowDto::createdAt, Comparator.nullsLast(Comparator.reverseOrder())));
		return activity;
	}

	/**
	 * 회원정보 수정. 닉네임 검증 실패 시 false를 돌려주고, 컨트롤러가 리다이렉트를 결정한다.
	 */
	@Transactional
	public boolean updateMember(Long id, String nickname, String email, String role, String region) {
		UserEntity user = lookupService.getUser(id);

		boolean validRole = List.of(UserEntity.ROLE_USER, UserEntity.ROLE_OWNER, UserEntity.ROLE_ADMIN).contains(role);
		if (nickname == null || nickname.isBlank() || !validRole) {
			return false;
		}

		user.setNickname(nickname.trim());
		user.setEmail(email == null || email.isBlank() ? null : email.trim());
		user.setRole(role);
		user.setRegion(region == null || region.isBlank() ? null : region.trim());
		userRepository.save(user);
		return true;
	}

	@Transactional
	public void suspend(Long id, String reason) {
		UserEntity user = lookupService.getUser(id);
		user.setStatus(UserEntity.STATUS_SUSPENDED);
		userRepository.save(user);
		notifySuspended(user.getId(), reason);
	}

	@Transactional
	public void unsuspend(Long id) {
		UserEntity user = lookupService.getUser(id);
		user.setStatus(UserEntity.STATUS_ACTIVE);
		userRepository.save(user);
	}

	/**
	 * MypageService#canWithdraw(자진 탈퇴)와 동일한 규칙으로 운영자가 강제 탈퇴시킨다 — 미완료 예약
	 * (본인 예약이거나, 본인이 점주인 매장에 걸린 예약)이 있으면 막는다.
	 */
	@Transactional(readOnly = true)
	public boolean canWithdraw(Long id) {
		if (reservationRepository.existsByUserIdAndStatusIn(id, INCOMPLETE_RESERVATION_STATUSES)) {
			return false;
		}
		StoreEntity ownedStore = storeAccessService.findMyStore(id).orElse(null);
		return ownedStore == null
				|| !reservationRepository.existsByStoreIdAndStatusIn(ownedStore.getId(), INCOMPLETE_RESERVATION_STATUSES);
	}

	/**
	 * 변경됨 (2026-09-22, 보미 피드백 반영 — soft delete 정책) — MypageService#withdraw(자진 탈퇴)와
	 * 동일한 로직으로 맞춘다. 예전엔 여기만 user_badge 정리 없이 바로 hard delete해서 자진 탈퇴
	 * 경로와 동작이 어긋나 있었는데(코드 감사에서는 못 잡았던 부분), 이번에 같이 정리한다.
	 *
	 * 추가됨 (2026-09-28, 코드 리뷰 — 문창호) — MypageService#withdraw와 같은 이유로 oauthId/phone을
	 * 더미값 처리한다 — 강제 탈퇴시킨 회원도 나중에 같은 계정으로 재가입할 수 있어야 한다.
	 */
	@Transactional
	public void withdraw(Long id) {
		UserEntity user = lookupService.getUser(id);
		LocalDateTime now = LocalDateTime.now();

		userArchiveRepository.save(UserArchiveEntity.builder()
				.originalUserId(user.getId())
				.oauthProvider(user.getOauthProvider())
				.email(user.getEmail())
				.phone(user.getPhone())
				.joinedAt(user.getCreatedAt())
				.withdrawnAt(now)
				.build());

		userBadgeRepository.deleteByUserId(id);

		// MypageService#withdraw와 동일한 이유(2026-09-29) — 1:N 소셜 연동 테이블에 남은 옛 providerId가
		// 탈퇴한 계정을 다시 찾아내 재가입을 막는 걸 방지.
		socialAccountRepository.deleteAll(socialAccountRepository.findAllByUserId(id));

		user.setOauthId("withdrawn_" + user.getId());
		user.setPhone(null);
		user.setDeletedAt(now);
		userRepository.save(user);
	}
}

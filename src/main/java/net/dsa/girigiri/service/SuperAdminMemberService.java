package net.dsa.girigiri.service;

import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.MemberActivityRowDto;
import net.dsa.girigiri.domain.entity.ComplaintEntity;
import net.dsa.girigiri.domain.entity.InquiryEntity;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.domain.entity.UserEntity;
import net.dsa.girigiri.repository.ComplaintRepository;
import net.dsa.girigiri.repository.InquiryRepository;
import net.dsa.girigiri.repository.ReservationRepository;
import net.dsa.girigiri.repository.UserRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
	private static final List<String> INCOMPLETE_RESERVATION_STATUSES = List.of("pending", "confirmed");

	private final UserRepository userRepository;
	private final ReservationRepository reservationRepository;
	private final InquiryRepository inquiryRepository;
	private final ComplaintRepository complaintRepository;
	private final LookupService lookupService;
	private final StoreAccessService storeAccessService;

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
	 */
	@Transactional
	public void bulkSuspend(List<Long> ids) {
		if (ids != null && !ids.isEmpty()) {
			List<UserEntity> targets = userRepository.findAllById(ids);
			targets.forEach(u -> u.setStatus(UserEntity.STATUS_SUSPENDED));
			userRepository.saveAll(targets);
		}
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
			activity.add(new MemberActivityRowDto("신고", c.getReason(), c.getCreatedAt(), "/superadmin/complaints/" + c.getId()));
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
	public void suspend(Long id) {
		UserEntity user = lookupService.getUser(id);
		user.setStatus(UserEntity.STATUS_SUSPENDED);
		userRepository.save(user);
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

	@Transactional
	public void withdraw(Long id) {
		userRepository.deleteById(id);
	}
}

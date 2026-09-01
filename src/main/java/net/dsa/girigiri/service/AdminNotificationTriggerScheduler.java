package net.dsa.girigiri.service;

import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.entity.InquiryEntity;
import net.dsa.girigiri.domain.entity.NotificationEntity;
import net.dsa.girigiri.domain.entity.ReservationEntity;
import net.dsa.girigiri.domain.entity.UserEntity;
import net.dsa.girigiri.repository.InquiryRepository;
import net.dsa.girigiri.repository.ReservationRepository;
import net.dsa.girigiri.repository.UserRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 슈퍼어드민 알림 패널(회원/게시판/예약)용 트리거. NotificationTriggerScheduler(강노은, 유저/예약용)와
 * 동일한 방식 — 다른 사람 코드에 훅을 심는 대신 이미 있는 Repository를 주기적으로 읽기만 해서 변화를
 * 감지한다. 관리자 세션/권한 분리가 아직 없어(문창호 작업 전) 수신자는 SuperAdminController#replyToInquiry
 * 와 동일한 스톱갭인 "role=ADMIN인 첫 계정"으로 고정한다 — role 분리가 끝나면 실제 로그인한 운영자
 * 계정들로 교체할 것.
 */
@Component
@RequiredArgsConstructor
public class AdminNotificationTriggerScheduler {

	private static final long SCAN_INTERVAL_MS = 60 * 1000L; // 1분마다

	private final NotificationService notificationService;
	private final UserRepository userRepository;
	private final InquiryRepository inquiryRepository;
	private final ReservationRepository reservationRepository;

	// 이 시각 이후에 생긴 것만 알림 대상으로 본다(회원가입/문의/예약 전부) — 없으면 서버를 켤 때마다
	// 그 전부터 있던 데이터 전부가 한꺼번에 "새로 생겼어요" 알림으로 쏟아진다(NotificationTriggerScheduler의
	// startedAt과 동일한 이유 — sourceKey 중복방지는 "두 번 안 만듦"만 보장하지 "처음에 몰아서 만듦"은
	// 못 막는다).
	private final LocalDateTime startedAt = LocalDateTime.now();

	@Scheduled(fixedRate = SCAN_INTERVAL_MS)
	public void scan() {
		UserEntity admin = userRepository.findFirstByRole(UserEntity.ROLE_ADMIN).orElse(null);
		if (admin == null) {
			return;
		}
		scanNewMembers(admin.getId());
		scanNewInquiries(admin.getId());
		scanNewReservations(admin.getId());
	}

	private void scanNewMembers(Long adminId) {
		for (UserEntity user : userRepository.findAll()) {
			if (user.getCreatedAt() == null || !user.getCreatedAt().isAfter(startedAt)) {
				continue;
			}
			notificationService.createNotification(adminId, NotificationEntity.TYPE_ADMIN_NEW_MEMBER,
					nicknameOrFallback(user) + "님이 신규 가입했습니다.",
					"/superadmin/members/" + user.getId(), "admin_new_member:" + user.getId());
		}
	}

	private void scanNewInquiries(Long adminId) {
		for (InquiryEntity inquiry : inquiryRepository.findAll()) {
			if (inquiry.getCreatedAt() == null || !inquiry.getCreatedAt().isAfter(startedAt)) {
				continue;
			}
			notificationService.createNotification(adminId, NotificationEntity.TYPE_ADMIN_NEW_INQUIRY,
					"새 문의가 등록됐어요: \"" + inquiry.getTitle() + "\"",
					"/superadmin/reports", "admin_new_inquiry:" + inquiry.getId());
		}
	}

	private void scanNewReservations(Long adminId) {
		for (ReservationEntity r : reservationRepository.findByStatusIn(List.of("pending"))) {
			if (r.getReservedAt() == null || !r.getReservedAt().isAfter(startedAt)) {
				continue;
			}
			notificationService.createNotification(adminId, NotificationEntity.TYPE_ADMIN_NEW_RESERVATION,
					"새 예약이 등록됐어요: \"" + productLabel(r) + "\"",
					null, "admin_new_reservation:" + r.getId());
		}
	}

	private String nicknameOrFallback(UserEntity user) {
		return user.getNickname() == null || user.getNickname().isBlank() ? "회원" : user.getNickname();
	}

	private String productLabel(ReservationEntity r) {
		return r.getProductName() == null || r.getProductName().isBlank() ? "예약 상품" : r.getProductName();
	}
}

package net.dsa.girigiri.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dsa.girigiri.domain.entity.CouponEntity;
import net.dsa.girigiri.domain.entity.NotificationEntity;
import net.dsa.girigiri.domain.entity.UserEntity;
import net.dsa.girigiri.repository.CouponRepository;
import net.dsa.girigiri.repository.UserRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 신규 가입 유저에게 웰컴 쿠폰을 자동 발급 — 2026-09-07 신규 (송채현).
 *
 * NotificationTriggerScheduler(강노은)와 동일한 방식: 회원가입 코드(문창호 담당,
 * OAuth2LoginSuccessHandler 등)에 직접 훅을 심지 않고, UserRepository를 주기적으로 "읽기만" 해서
 * 새 유저를 감지한다 — 그쪽 파일은 한 줄도 안 건드린다(팀이 이미 합의한 패턴을 그대로 따름).
 *
 * 중복 발급 방지는 CouponEntity.issuedToUserId 존재 여부로 한다 — 매 스캔마다 전체를 다시 훑어도
 * 이미 발급받은 유저는 걸러지므로 상태를 따로 기억할 필요 없이 항상 안전(idempotent)하다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WelcomeCouponScheduler {

	private static final long SCAN_INTERVAL_MS = 60 * 1000L; // 1분마다
	private static final String COUPON_URL = "/coupons";

	private final UserRepository userRepository;
	private final CouponRepository couponRepository;
	private final CouponService couponService;
	private final NotificationService notificationService;

	// 서버 켜지기 전부터 있던 기존 회원 전체가 한꺼번에 웰컴 쿠폰을 받는 걸 막기 위한 기준 시각
	// (NotificationTriggerScheduler의 startedAt과 동일한 이유).
	private final LocalDateTime startedAt = LocalDateTime.now();

	@Scheduled(fixedRate = SCAN_INTERVAL_MS)
	public void scan() {
		List<UserEntity> freshlyJoined = userRepository.findAll().stream()
				.filter(u -> u.getCreatedAt() != null && u.getCreatedAt().isAfter(startedAt))
				.toList();
		if (freshlyJoined.isEmpty()) {
			return;
		}

		for (UserEntity user : freshlyJoined) {
			if (couponRepository.existsByIssuedToUserIdAndSource(user.getId(), CouponEntity.SOURCE_WELCOME)) {
				continue;
			}
			CouponEntity coupon = couponService.issueWelcomeCoupon(user.getId());
			notificationService.createNotification(user.getId(), NotificationEntity.TYPE_WELCOME_COUPON,
					"가입을 축하해요! 웰컴 쿠폰(" + coupon.getDiscountRate() + "% 할인)이 내 쿠폰함에 들어왔어요.",
					COUPON_URL, "welcome_coupon:" + user.getId());
		}
	}
}

package net.dsa.girigiri.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dsa.girigiri.domain.dto.CouponRowDto;
import net.dsa.girigiri.domain.entity.CouponEntity;
import net.dsa.girigiri.domain.entity.CouponPolicyEntity;
import net.dsa.girigiri.repository.CouponPolicyRepository;
import net.dsa.girigiri.repository.CouponRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 쿠폰 발급/사용/복구 — 2026-09-07 신규, 2026-09-07 재설계 (송채현, WBS "쿠폰 발급/관리").
 * 발급 경로 3가지(웰컴/매장귀책보상/프로모션)의 공통 로직 + 체크아웃 사용/취소 시 복구 로직을 담는다.
 * 캠페인 자체의 CRUD(슈퍼어드민 화면)는 SuperAdminCouponService가 따로 담당한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponService {

	public enum PolicyUpdateResult { SUCCESS, INVALID }

	// 웰컴/매장귀책보상 쿠폰 유효기간(일) — 할인율과 달리 슈퍼어드민 화면에서 아직 안 건드리는 값이라
	// 상수로 유지한다 (2026-09-07, 채채 확인 요청 범위는 "할인율만" 이었음).
	private static final int WELCOME_VALID_DAYS = 30;
	private static final int COMPENSATION_VALID_DAYS = 30;

	// 정책 테이블이 아직 비어있을 때(최초 실행) 만들어주는 기본값 — 기존 하드코딩값과 동일하게 맞췄다.
	private static final int DEFAULT_WELCOME_DISCOUNT_RATE = 10;
	private static final int DEFAULT_COMPENSATION_DISCOUNT_RATE = 15;

	// 정책 테이블은 항상 이 id 하나만 쓴다 (CouponPolicyEntity 클래스 주석 참고).
	private static final Long POLICY_ID = 1L;

	private static final DateTimeFormatter DATE_LABEL = DateTimeFormatter.ofPattern("yyyy-MM-dd");

	private final CouponRepository couponRepository;
	// 추가됨 (2026-09-07, 채채 확인) — 웰컴/매장귀책보상 쿠폰 할인율을 더 이상 코드 상수로 안 박아두고,
	// 슈퍼어드민이 /superadmin/coupons 화면에서 직접 수정할 수 있게 뺐다. "쿠폰은 사장님이 아니라
	// 슈퍼어드민이 만들어서 뿌린다"는 원칙을 프로모션 쿠폰뿐 아니라 이 두 종류에도 동일하게 적용.
	private final CouponPolicyRepository couponPolicyRepository;

	@Transactional(readOnly = true)
	public List<CouponRowDto> listForUser(Long userId) {
		LocalDateTime now = LocalDateTime.now();
		return couponRepository.findByIssuedToUserIdOrderByCreatedAtDesc(userId).stream()
				.map(c -> toRow(c, now))
				.toList();
	}

	/**
	 * 체크아웃 화면의 "쿠폰 사용하기" 선택 목록 전용 — 2026-09-07 추가 (채채 확인, 체크아웃 연동).
	 * listForUser()와 다르게 지금 실제로 고를 수 있는(미사용 + 미만료) 쿠폰만 내려준다 — 이미 쓴
	 * 쿠폰이나 기한 지난 쿠폰까지 드롭다운에 보이면 골랐을 때 어차피 validateForRedeem에서 막혀서
	 * 혼란만 준다.
	 */
	@Transactional(readOnly = true)
	public List<CouponRowDto> listUsableForUser(Long userId) {
		LocalDateTime now = LocalDateTime.now();
		return couponRepository.findByIssuedToUserIdOrderByCreatedAtDesc(userId).stream()
				.filter(c -> !c.isUsed() && c.getExpiresAt().isAfter(now))
				.map(c -> toRow(c, now))
				.toList();
	}

	/** WelcomeCouponScheduler 전용 — 이미 발급했는지(existsByIssuedToUserIdAndSource) 확인은 호출부에서 먼저 한다. */
	@Transactional
	public CouponEntity issueWelcomeCoupon(Long userId) {
		CouponPolicyEntity policy = getOrCreatePolicy();
		CouponEntity coupon = CouponEntity.builder()
				.source(CouponEntity.SOURCE_WELCOME)
				.issuedToUserId(userId)
				.discountRate(policy.getWelcomeDiscountRate())
				.expiresAt(LocalDateTime.now().plusDays(WELCOME_VALID_DAYS))
				.used(false)
				.build();
		couponRepository.save(coupon);
		return coupon;
	}

	/** ReservationService#cancelByStore 전용 — 매장 귀책으로 예약이 취소된 회원에게 보상 쿠폰을 준다. */
	@Transactional
	public CouponEntity issueStoreCompensationCoupon(Long userId, Long sourceReservationId) {
		CouponPolicyEntity policy = getOrCreatePolicy();
		CouponEntity coupon = CouponEntity.builder()
				.source(CouponEntity.SOURCE_STORE_COMPENSATION)
				.issuedToUserId(userId)
				.sourceReservationId(sourceReservationId)
				.discountRate(policy.getCompensationDiscountRate())
				.expiresAt(LocalDateTime.now().plusDays(COMPENSATION_VALID_DAYS))
				.used(false)
				.build();
		couponRepository.save(coupon);
		return coupon;
	}

	/** 슈퍼어드민 화면에서 현재 정책값(웰컴/매장보상 할인율)을 보여줄 때 쓴다. 없으면 기본값으로 만들어서 돌려준다. */
	@Transactional
	public CouponPolicyEntity getOrCreatePolicy() {
		return couponPolicyRepository.findById(POLICY_ID)
				.orElseGet(() -> couponPolicyRepository.save(CouponPolicyEntity.builder()
						.welcomeDiscountRate(DEFAULT_WELCOME_DISCOUNT_RATE)
						.compensationDiscountRate(DEFAULT_COMPENSATION_DISCOUNT_RATE)
						.build()));
	}

	/** 슈퍼어드민이 /superadmin/coupons 화면에서 웰컴/매장보상 할인율을 수정할 때 쓴다. 1~90% 범위만 허용. */
	@Transactional
	public PolicyUpdateResult updatePolicy(Integer welcomeDiscountRate, Integer compensationDiscountRate) {
		if (!isValidRate(welcomeDiscountRate) || !isValidRate(compensationDiscountRate)) {
			return PolicyUpdateResult.INVALID;
		}
		CouponPolicyEntity policy = getOrCreatePolicy();
		policy.setWelcomeDiscountRate(welcomeDiscountRate);
		policy.setCompensationDiscountRate(compensationDiscountRate);
		couponPolicyRepository.save(policy);
		return PolicyUpdateResult.SUCCESS;
	}

	private boolean isValidRate(Integer rate) {
		return rate != null && rate >= 1 && rate <= 90;
	}

	/**
	 * 체크아웃에서 쿠폰을 실제로 썼을 때 호출 — used=true로 표시한다.
	 * (2026-09-07 시점: 체크아웃 UI 자체는 다음 작업 "할인코드 적용/검증"에서 붙인다. 이 메서드는
	 * 그때 ReservationService.prepareReservation()/confirmPayment()에서 호출하면 된다.)
	 */
	@Transactional
	public void markUsed(Long couponId) {
		couponRepository.findById(couponId).ifPresent(coupon -> {
			coupon.setUsed(true);
			couponRepository.save(coupon);
		});
	}

	/**
	 * 쿠폰을 썼던 예약이 취소돼서 다시 쓸 수 있게 되돌린다. 회원 본인 노쇼로 인한 취소는 호출부
	 * (ReservationService)에서 아예 이 메서드를 부르지 않는 방식으로 걸러낸다 — 본인 잘못은 복구 대상이
	 * 아니기 때문. couponId가 null이면(그 예약이 애초에 쿠폰을 안 썼으면) 아무 일도 하지 않는다.
	 */
	@Transactional
	public void restore(Long couponId) {
		if (couponId == null) {
			return;
		}
		// 변경됨 (2026-09-08, 코드 감사) — 왜: ifPresent였을 때는 couponId가 있는데 실제로 그
		// 쿠폰이 DB에 없는 경우(예: 데이터 꼬임/삭제됨) 조용히 아무 일도 안 하고 넘어가서, 복구가
		// 안 됐다는 걸 아무도 모르고 지나갈 수 있었다. 흔한 상황은 아니지만, 최소한 로그는 남긴다.
		couponRepository.findById(couponId).ifPresentOrElse(
				coupon -> {
					coupon.setUsed(false);
					couponRepository.save(coupon);
				},
				() -> log.warn("> [CouponService] 복구하려는 쿠폰을 찾을 수 없어요 - couponId={}", couponId)
		);
	}

	/** 체크아웃에서 이 쿠폰을 지금 쓸 수 있는지 검증 — 본인 소유 + 미사용 + 미만료여야 한다. */
	@Transactional(readOnly = true)
	public CouponEntity validateForRedeem(Long userId, Long couponId) {
		CouponEntity coupon = couponRepository.findByIdAndIssuedToUserId(couponId, userId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "쿠폰을 찾을 수 없어요."));
		if (coupon.isUsed()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미 사용한 쿠폰이에요.");
		}
		if (!coupon.getExpiresAt().isAfter(LocalDateTime.now())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "기한이 지난 쿠폰이에요.");
		}
		return coupon;
	}

	/**
	 * 프로모션 코드를 입력해서 쿠폰을 발급받는다 — 캠페인 CRUD 자체는 SuperAdminCouponService 소관이라
	 * CouponCampaignRepository는 여기서 직접 안 쓰고, SuperAdminCouponService의 조회 메서드를 통해서만 접근한다
	 * (레이어 규칙: "발급받기"는 회원 기능이라 이 서비스에 두되, 캠페인 소유 로직과는 분리).
	 */
	@Transactional
	public CouponEntity claimCampaignCoupon(Long userId, net.dsa.girigiri.domain.entity.CouponCampaignEntity campaign) {
		if (!campaign.isActive() || !campaign.getExpiresAt().isAfter(LocalDateTime.now())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "지금은 받을 수 없는 쿠폰이에요.");
		}
		if (couponRepository.existsByCampaignIdAndIssuedToUserId(campaign.getId(), userId)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 받은 쿠폰이에요.");
		}
		CouponEntity coupon = CouponEntity.builder()
				.source(CouponEntity.SOURCE_PROMOTION)
				.issuedToUserId(userId)
				.campaignId(campaign.getId())
				.discountRate(campaign.getDiscountRate())
				.expiresAt(campaign.getExpiresAt())
				.used(false)
				.build();
		couponRepository.save(coupon);
		return coupon;
	}

	// ---------------------------------------------------------------------

	private CouponRowDto toRow(CouponEntity c, LocalDateTime now) {
		boolean expired = !c.getExpiresAt().isAfter(now);
		String statusLabel;
		if (c.isUsed()) {
			statusLabel = "사용 완료";
		} else if (expired) {
			statusLabel = "기한 만료";
		} else {
			statusLabel = "사용 가능";
		}
		return CouponRowDto.builder()
				.id(c.getId())
				.sourceLabel(sourceLabel(c.getSource()))
				.discountRate(c.getDiscountRate())
				.expiresAtLabel(c.getExpiresAt().toLocalDate().format(DATE_LABEL))
				.used(c.isUsed())
				.expired(expired)
				.statusLabel(statusLabel)
				.build();
	}

	private String sourceLabel(String source) {
		if (CouponEntity.SOURCE_WELCOME.equals(source)) {
			return "웰컴 쿠폰";
		}
		if (CouponEntity.SOURCE_STORE_COMPENSATION.equals(source)) {
			return "매장 취소 보상";
		}
		if (CouponEntity.SOURCE_PROMOTION.equals(source)) {
			return "프로모션";
		}
		return "쿠폰";
	}
}

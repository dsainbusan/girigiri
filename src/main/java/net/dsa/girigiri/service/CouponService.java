package net.dsa.girigiri.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dsa.girigiri.domain.dto.CouponRowDto;
import net.dsa.girigiri.domain.entity.CouponCampaignEntity;
import net.dsa.girigiri.domain.entity.CouponEntity;
import net.dsa.girigiri.domain.entity.CouponPolicyEntity;
import net.dsa.girigiri.repository.CouponCampaignRepository;
import net.dsa.girigiri.repository.CouponPolicyRepository;
import net.dsa.girigiri.repository.CouponRepository;
import net.dsa.girigiri.repository.CouponRegionRepository;
import net.dsa.girigiri.repository.CouponStoreRepository;
import net.dsa.girigiri.repository.StoreRepository;
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
	// 추가됨 (2026-10-01, 매장 지정 쿠폰) — 체크아웃 시 "이 매장에서 쓸 수 있는 쿠폰인지" 확인용.
	private final CouponStoreRepository couponStoreRepository;
	// 추가됨 (2026-10-06, 지역 지정 쿠폰) — 체크아웃에서 "이 매장의 시도가 쿠폰 대상 지역인지" 확인용.
	private final CouponRegionRepository couponRegionRepository;
	private final StoreRepository storeRepository;
	// 추가됨 (2026-10-01, 매장 지정 쿠폰) — claimStoreCampaignCoupon()에서 발급 수량 상한을
	// 동시성 안전하게 확인하기 위해 캠페인 행을 락 걸고 다시 조회한다.
	private final CouponCampaignRepository couponCampaignRepository;
	// 추가됨 (2026-10-07) — 매장 지정 쿠폰은 "이 매장을 찜한 손님"에게만 주는 쿠폰이다
	// (SuperAdminCouponService#createStoreCampaign이 발행 수량 자체를 찜한 손님 수로 제한하는 것과
	// 같은 전제). 그동안 발행 수량만 그 수로 캡을 걸어뒀을 뿐, "찜 안 한 손님도 선착순으로 받을 수
	// 있는" 구멍이 있었다 — findStoreCouponOffers/claimStoreCampaignCoupon 양쪽 다 실제 찜 여부를
	// 확인하도록 좁힌다(StoreDetailController가 이미 쓰는 LikeService.isLiked 재사용).
	private final LikeService likeService;

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
			coupon.setUsedAt(LocalDateTime.now());
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
					coupon.setUsedAt(null);
					couponRepository.save(coupon);
				},
				() -> log.warn("> [CouponService] 복구하려는 쿠폰을 찾을 수 없어요 - couponId={}", couponId)
		);
	}

	/**
	 * 체크아웃에서 이 쿠폰을 지금 쓸 수 있는지 검증 — 본인 소유 + 미사용 + 미만료 + (매장 지정
	 * 쿠폰이면) 이 매장에서 쓸 수 있는지 + 최소 주문 금액까지 확인한다.
	 * 2026-10-01 확장 — storeId/totalPrice 파라미터 추가(매장 지정 쿠폰, 지역별 현황 드릴다운).
	 * scope가 null인 기존 쿠폰(웰컴/매장보상/코드형 프로모션)은 매장 제한이 없어 그대로 통과한다.
	 */
	@Transactional(readOnly = true)
	public CouponEntity validateForRedeem(Long userId, Long couponId, Long storeId, int totalPrice) {
		CouponEntity coupon = couponRepository.findByIdAndIssuedToUserId(couponId, userId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "쿠폰을 찾을 수 없어요."));
		if (coupon.isUsed()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미 사용한 쿠폰이에요.");
		}
		if (!coupon.getExpiresAt().isAfter(LocalDateTime.now())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "기한이 지난 쿠폰이에요.");
		}
		if (CouponCampaignEntity.SCOPE_REGION.equals(coupon.getScope())) {
			// 지역 지정 쿠폰(2026-10-06 발행 기능 추가) — 이 매장이 대상 시도에 있어야 쓸 수 있다.
			// 매장의 sido가 비어 있으면(주소 파싱 실패 등) 대상 여부를 알 수 없으니 막는다.
			String storeSido = storeId == null ? null : storeRepository.findById(storeId).map(net.dsa.girigiri.domain.entity.StoreEntity::getSido).orElse(null);
			if (storeSido == null || !couponRegionRepository.existsByCampaignIdAndSido(coupon.getCampaignId(), storeSido)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이 지역 매장에서는 쓸 수 없는 쿠폰이에요.");
			}
		}
		if (CouponCampaignEntity.SCOPE_STORE.equals(coupon.getScope())
				&& !couponStoreRepository.existsByCampaignIdAndStoreId(coupon.getCampaignId(), storeId)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이 매장에서는 쓸 수 없는 쿠폰이에요.");
		}
		if (coupon.getMinOrderAmount() != null && totalPrice < coupon.getMinOrderAmount()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"최소 주문 금액 " + coupon.getMinOrderAmount() + "원 이상부터 쓸 수 있는 쿠폰이에요.");
		}
		return coupon;
	}

	/**
	 * 쿠폰 할인액 계산 — 정액(AMOUNT)이면 그 금액 그대로(주문 금액을 넘지 않게 캡), 정률(RATE, 기존
	 * 쿠폰 전부 포함)이면 주문 금액의 discountRate%를 적용하고 maxDiscountAmount가 있으면 그만큼
	 * 캡을 씌운다. ReservationService#prepareReservation에서 호출.
	 */
	public int computeDiscount(CouponEntity coupon, int totalPrice) {
		if (CouponCampaignEntity.DISCOUNT_TYPE_AMOUNT.equals(coupon.getDiscountType())) {
			return Math.min(coupon.getDiscountAmount(), totalPrice);
		}
		int amount = totalPrice * coupon.getDiscountRate() / 100;
		if (coupon.getMaxDiscountAmount() != null) {
			amount = Math.min(amount, coupon.getMaxDiscountAmount());
		}
		return Math.min(amount, totalPrice);
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

	/**
	 * 매장 지정 쿠폰(scope=STORE)을 손님용 상세 페이지의 "쿠폰 받기" 버튼으로 받는다 — 2026-10-01
	 * 신규(지역별 현황 드릴다운). 코드 입력이 없다는 점과 발급 수량 상한(issueLimit) 동시성 처리가
	 * claimCampaignCoupon()과 다른 점이라 별도 메서드로 둔다 — 기존 코드형 발급 경로는 그대로 둔다.
	 * findByIdForUpdate로 캠페인 행을 잠가서, 두 손님이 동시에 눌러도 상한을 넘겨 발급되지 않는다
	 * (ProductRepository/StockService의 재고 차감 락과 동일한 패턴).
	 */
	@Transactional
	public CouponEntity claimStoreCampaignCoupon(Long userId, Long campaignId, Long storeId) {
		CouponCampaignEntity campaign = couponCampaignRepository.findByIdForUpdate(campaignId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "쿠폰을 찾을 수 없어요."));
		if (!CouponCampaignEntity.SCOPE_STORE.equals(campaign.getScope())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "받을 수 없는 쿠폰이에요.");
		}
		if (!campaign.isActive() || !campaign.getExpiresAt().isAfter(LocalDateTime.now())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "지금은 받을 수 없는 쿠폰이에요.");
		}
		// 버튼은 찜한 손님에게만 보이지만(findStoreCouponOffers), 그건 화면 쪽 필터일 뿐이라 URL을
		// 직접 쳐서 들어오는 경우까지 막으려면 여기서도 한 번 더 확인해야 한다(신고하기 등 다른
		// 기능들과 동일한 "화면에서 숨기는 것 + 서버에서도 막는 것" 2중 방어 관례).
		// 수정됨 (2026-10-07, 보안 리뷰) — storeId가 폼 hidden input이라 그대로 믿으면 "내가 찜한
		// 다른 아무 매장 id"를 끼워 넣어 이 캠페인과 무관한 매장으로 찜 검사를 통과시킬 수 있었다
		// (campaignId와 storeId가 실제로 연결돼 있는지 확인이 없었음 — IDOR). validateForRedeem이
		// 이미 쓰는 couponStoreRepository.existsByCampaignIdAndStoreId로 먼저 "이 storeId가 정말 이
		// 캠페인 대상인지"를 묶은 다음에 찜 여부를 본다.
		if (!couponStoreRepository.existsByCampaignIdAndStoreId(campaignId, storeId)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "받을 수 없는 쿠폰이에요.");
		}
		if (!likeService.isLiked(userId, storeId)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "이 매장을 찜한 손님만 받을 수 있는 쿠폰이에요.");
		}
		if (couponRepository.existsByCampaignIdAndIssuedToUserId(campaignId, userId)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 받은 쿠폰이에요.");
		}
		if (campaign.getIssueLimit() != null && couponRepository.countByCampaignId(campaignId) >= campaign.getIssueLimit()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "쿠폰이 모두 소진됐어요.");
		}

		CouponEntity coupon = CouponEntity.builder()
				.source(CouponEntity.SOURCE_PROMOTION)
				.issuedToUserId(userId)
				.campaignId(campaign.getId())
				.scope(campaign.getScope())
				.discountType(campaign.getDiscountType())
				.discountRate(campaign.getDiscountRate())
				.discountAmount(campaign.getDiscountAmount())
				.maxDiscountAmount(campaign.getMaxDiscountAmount())
				.minOrderAmount(campaign.getMinOrderAmount())
				.expiresAt(campaign.getExpiresAt())
				.used(false)
				.build();
		couponRepository.save(coupon);
		return coupon;
	}

	/** 손님용 상세 페이지에서 "이 매장에 지금 받을 수 있는 쿠폰이 있는지 + 이미 받았는지" 확인할 때 쓴다. */
	@Transactional(readOnly = true)
	public boolean hasClaimedCampaign(Long userId, Long campaignId) {
		return couponRepository.existsByCampaignIdAndIssuedToUserId(campaignId, userId);
	}

	/**
	 * 손님용 매장 상세 페이지 "쿠폰 받기" 카드 목록 — 2026-10-01 신규. 이 매장을 대상으로 한, 지금
	 * 활성·미만료인 매장 지정 캠페인만 보여준다. userId가 null(비로그인)이면 claimed는 전부 false로
	 * 내려준다 — 버튼을 눌렀을 때 로그인 화면으로 보내는 건 컨트롤러(@LoginRequired)가 처리한다.
	 *
	 * 좁혀짐 (2026-10-07) — 매장 지정 쿠폰은 그 매장을 찜한 손님 전용이라, 찜 안 한 손님(비로그인
	 * 포함)에겐 카드 자체를 안 보여준다. claimStoreCampaignCoupon()의 서버 쪽 확인과 같은 기준
	 * (LikeService.isLiked)이라 "카드는 보이는데 누르면 막히는" 불일치가 없다.
	 */
	@Transactional(readOnly = true)
	public List<net.dsa.girigiri.domain.dto.StoreCouponOfferDto> findStoreCouponOffers(Long storeId, Long userId) {
		if (!likeService.isLiked(userId, storeId)) {
			return List.of();
		}
		LocalDateTime now = LocalDateTime.now();
		return couponStoreRepository.findByStoreId(storeId).stream()
				.map(net.dsa.girigiri.domain.entity.CouponStoreEntity::getCampaignId)
				.distinct()
				.map(id -> couponCampaignRepository.findById(id).orElse(null))
				.filter(java.util.Objects::nonNull)
				.filter(c -> c.isActive() && c.getExpiresAt().isAfter(now))
				.map(c -> net.dsa.girigiri.domain.dto.StoreCouponOfferDto.builder()
						.campaignId(c.getId())
						.discountLabel(CouponCampaignEntity.DISCOUNT_TYPE_AMOUNT.equals(c.getDiscountType())
								? c.getDiscountAmount() + "원 할인"
								: c.getDiscountRate() + "% 할인")
						.expiresAtLabel(c.getExpiresAt().toLocalDate().format(DATE_LABEL) + "까지")
						.claimed(userId != null && hasClaimedCampaign(userId, c.getId()))
						.build())
				.toList();
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
				.discountLabel(CouponCampaignEntity.DISCOUNT_TYPE_AMOUNT.equals(c.getDiscountType())
						? c.getDiscountAmount() + "원 할인" : c.getDiscountRate() + "% 할인")
				.areaLabel(CouponCampaignEntity.SCOPE_REGION.equals(c.getScope())
						? couponRegionRepository.findByCampaignId(c.getCampaignId()).stream()
								.map(net.dsa.girigiri.domain.entity.CouponRegionEntity::getSido)
								.collect(java.util.stream.Collectors.joining("·")) + " 매장에서 사용"
						: null)
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

package net.dsa.girigiri;

import net.dsa.girigiri.domain.entity.CouponCampaignEntity;
import net.dsa.girigiri.domain.entity.CouponEntity;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.repository.CouponCampaignRepository;
import net.dsa.girigiri.repository.CouponPolicyRepository;
import net.dsa.girigiri.repository.CouponRegionRepository;
import net.dsa.girigiri.repository.CouponRepository;
import net.dsa.girigiri.repository.CouponStoreRepository;
import net.dsa.girigiri.repository.StoreRepository;
import net.dsa.girigiri.service.CouponService;
import net.dsa.girigiri.service.LikeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * 매장 지정 쿠폰(scope=STORE) 체크아웃 검증 + 할인 계산 — 2026-10-01 신규
 * (지역별 현황 드릴다운 "완료 조건": 쿠폰 적용 가능 여부 검증, REGION 발행 차단 → 2026-10-06 지역 지정 쿠폰 지원으로 변경).
 * CouponService는 repository가 여러 개라 Mockito 단위 테스트로 격리한다(LedgerServiceBadgeTest와 동일 패턴).
 */
@ExtendWith(MockitoExtension.class)
class CouponStoreScopeTest {

	@Mock
	private CouponRepository couponRepository;
	@Mock
	private CouponPolicyRepository couponPolicyRepository;
	@Mock
	private CouponStoreRepository couponStoreRepository;
	@Mock
	private CouponCampaignRepository couponCampaignRepository;
	// 지역 지정 쿠폰(2026-10-06) — validateForRedeem이 매장의 시도를 coupon_region과 대조한다.
	@Mock
	private CouponRegionRepository couponRegionRepository;
	@Mock
	private StoreRepository storeRepository;
	// 매장 지정 쿠폰 찜 제한(2026-10-07) — claimStoreCampaignCoupon()이 LikeService.isLiked로 확인한다.
	@Mock
	private LikeService likeService;

	@InjectMocks
	private CouponService couponService;

	private CouponEntity storeCoupon(String scope, Long campaignId) {
		return CouponEntity.builder()
				.id(1L)
				.issuedToUserId(100L)
				.campaignId(campaignId)
				.scope(scope)
				.discountRate(10)
				.expiresAt(LocalDateTime.now().plusDays(1))
				.used(false)
				.build();
	}

	@Test
	void 매장_지정_쿠폰은_대상_매장이_아니면_거부된다() {
		CouponEntity coupon = storeCoupon(CouponCampaignEntity.SCOPE_STORE, 50L);
		when(couponRepository.findByIdAndIssuedToUserId(1L, 100L)).thenReturn(Optional.of(coupon));
		when(couponStoreRepository.existsByCampaignIdAndStoreId(50L, 999L)).thenReturn(false);

		ResponseStatusException e = assertThrows(ResponseStatusException.class,
				() -> couponService.validateForRedeem(100L, 1L, 999L, 10000));
		assertTrue(e.getReason().contains("매장에서는"));
	}

	@Test
	void 매장_지정_쿠폰은_대상_매장이면_통과한다() {
		CouponEntity coupon = storeCoupon(CouponCampaignEntity.SCOPE_STORE, 50L);
		when(couponRepository.findByIdAndIssuedToUserId(1L, 100L)).thenReturn(Optional.of(coupon));
		when(couponStoreRepository.existsByCampaignIdAndStoreId(50L, 7L)).thenReturn(true);

		CouponEntity result = couponService.validateForRedeem(100L, 1L, 7L, 10000);
		assertEquals(1L, result.getId());
	}

	// 변경됨 (2026-10-06) — 지역 지정 쿠폰 발행이 생겨서 "REGION은 항상 거부" 대신 "대상 시도의 매장에서만 통과"로 바뀌었다.
	@Test
	void 지역_지정_쿠폰은_대상_시도의_매장이면_통과한다() {
		CouponEntity coupon = storeCoupon(CouponCampaignEntity.SCOPE_REGION, 51L);
		when(couponRepository.findByIdAndIssuedToUserId(1L, 100L)).thenReturn(Optional.of(coupon));
		when(storeRepository.findById(7L)).thenReturn(Optional.of(StoreEntity.builder().id(7L).sido("부산").build()));
		when(couponRegionRepository.existsByCampaignIdAndSido(51L, "부산")).thenReturn(true);

		assertEquals(1L, couponService.validateForRedeem(100L, 1L, 7L, 10000).getId());
	}

	@Test
	void 지역_지정_쿠폰은_대상_시도가_아닌_매장이면_거부된다() {
		CouponEntity coupon = storeCoupon(CouponCampaignEntity.SCOPE_REGION, 51L);
		when(couponRepository.findByIdAndIssuedToUserId(1L, 100L)).thenReturn(Optional.of(coupon));
		when(storeRepository.findById(7L)).thenReturn(Optional.of(StoreEntity.builder().id(7L).sido("서울").build()));
		when(couponRegionRepository.existsByCampaignIdAndSido(51L, "서울")).thenReturn(false);

		ResponseStatusException e = assertThrows(ResponseStatusException.class,
				() -> couponService.validateForRedeem(100L, 1L, 7L, 10000));
		assertTrue(e.getReason().contains("이 지역"));
	}

	@Test
	void 지역_지정_쿠폰은_매장의_시도를_모르면_거부된다() {
		CouponEntity coupon = storeCoupon(CouponCampaignEntity.SCOPE_REGION, 51L);
		when(couponRepository.findByIdAndIssuedToUserId(1L, 100L)).thenReturn(Optional.of(coupon));
		when(storeRepository.findById(7L)).thenReturn(Optional.of(StoreEntity.builder().id(7L).sido(null).build()));

		assertThrows(ResponseStatusException.class, () -> couponService.validateForRedeem(100L, 1L, 7L, 10000));
	}

	@Test
	void 최소_주문_금액_미달이면_거부된다() {
		CouponEntity coupon = CouponEntity.builder()
				.id(1L).issuedToUserId(100L).discountRate(10)
				.minOrderAmount(20000)
				.expiresAt(LocalDateTime.now().plusDays(1)).used(false).build();
		when(couponRepository.findByIdAndIssuedToUserId(1L, 100L)).thenReturn(Optional.of(coupon));

		ResponseStatusException e = assertThrows(ResponseStatusException.class,
				() -> couponService.validateForRedeem(100L, 1L, 7L, 10000));
		assertTrue(e.getReason().contains("최소 주문 금액"));
	}

	@Test
	void REGION_캠페인은_매장지정_발급_경로에서_거부된다() {
		CouponCampaignEntity regionCampaign = CouponCampaignEntity.builder()
				.id(50L).scope(CouponCampaignEntity.SCOPE_REGION)
				.discountType(CouponCampaignEntity.DISCOUNT_TYPE_RATE).discountRate(10)
				.expiresAt(LocalDateTime.now().plusDays(7)).active(true).build();
		when(couponCampaignRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(regionCampaign));

		ResponseStatusException e = assertThrows(ResponseStatusException.class,
				() -> couponService.claimStoreCampaignCoupon(100L, 50L, 7L));
		assertEquals(400, e.getStatusCode().value());
	}

	@Test
	void 발행_수량_상한에_도달하면_추가_발급이_거부된다() {
		CouponCampaignEntity campaign = CouponCampaignEntity.builder()
				.id(50L).scope(CouponCampaignEntity.SCOPE_STORE)
				.discountType(CouponCampaignEntity.DISCOUNT_TYPE_RATE).discountRate(10)
				.issueLimit(5)
				.expiresAt(LocalDateTime.now().plusDays(7)).active(true).build();
		when(couponCampaignRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(campaign));
		when(likeService.isLiked(100L, 7L)).thenReturn(true);
		when(couponRepository.existsByCampaignIdAndIssuedToUserId(50L, 100L)).thenReturn(false);
		when(couponRepository.countByCampaignId(50L)).thenReturn(5L);

		ResponseStatusException e = assertThrows(ResponseStatusException.class,
				() -> couponService.claimStoreCampaignCoupon(100L, 50L, 7L));
		assertTrue(e.getReason().contains("소진"));
	}

	// 추가됨 (2026-10-07) — 매장 지정 쿠폰은 그 매장을 찜한 손님 전용. 찜 안 한 손님은 발행 수량이
	// 남아있어도 거부되는지 확인.
	@Test
	void 매장_지정_쿠폰은_찜하지_않은_손님이면_거부된다() {
		CouponCampaignEntity campaign = CouponCampaignEntity.builder()
				.id(50L).scope(CouponCampaignEntity.SCOPE_STORE)
				.discountType(CouponCampaignEntity.DISCOUNT_TYPE_RATE).discountRate(10)
				.issueLimit(5)
				.expiresAt(LocalDateTime.now().plusDays(7)).active(true).build();
		when(couponCampaignRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(campaign));
		when(likeService.isLiked(100L, 7L)).thenReturn(false);

		ResponseStatusException e = assertThrows(ResponseStatusException.class,
				() -> couponService.claimStoreCampaignCoupon(100L, 50L, 7L));
		assertEquals(403, e.getStatusCode().value());
		assertTrue(e.getReason().contains("찜한"));
	}

	@Test
	void 정액_할인은_주문금액을_넘지_않게_캡핑된다() {
		CouponEntity coupon = CouponEntity.builder()
				.discountType(CouponCampaignEntity.DISCOUNT_TYPE_AMOUNT)
				.discountAmount(5000)
				.build();
		assertEquals(3000, couponService.computeDiscount(coupon, 3000));
		assertEquals(5000, couponService.computeDiscount(coupon, 10000));
	}

	@Test
	void 정률_할인은_최대_할인_금액으로_캡핑된다() {
		CouponEntity coupon = CouponEntity.builder()
				.discountRate(50)
				.maxDiscountAmount(2000)
				.build();
		// 50% of 10000 = 5000, but capped at 2000
		assertEquals(2000, couponService.computeDiscount(coupon, 10000));
	}
}

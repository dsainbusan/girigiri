package net.dsa.girigiri;

import net.dsa.girigiri.domain.entity.CouponCampaignEntity;
import net.dsa.girigiri.domain.entity.CouponEntity;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.domain.entity.UserEntity;
import net.dsa.girigiri.repository.CouponCampaignRepository;
import net.dsa.girigiri.repository.CouponRepository;
import net.dsa.girigiri.repository.StoreRepository;
import net.dsa.girigiri.repository.UserRepository;
import net.dsa.girigiri.service.CouponService;
import net.dsa.girigiri.service.SuperAdminCouponService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 지역 지정 쿠폰 발행(SuperAdminCouponService#createRegionCampaign, 2026-10-06). 지역(시도)이 서로 다른
 * 회원/매장을 테스트가 직접 만들어서 sample-data.sql 내용과 무관하게 돌아간다 — 공용 샘플 회원이 이미
 * 서울에 여럿 있을 수 있어, 발급 수는 "이 테스트가 만든 회원이 받았는가"로만 확인한다. @Transactional로
 * 끝나면 전부 롤백된다.
 */
@SpringBootTest
@Transactional
class RegionCouponServiceTest {

	@Autowired
	private SuperAdminCouponService superAdminCouponService;
	@Autowired
	private CouponService couponService;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private StoreRepository storeRepository;
	@Autowired
	private CouponRepository couponRepository;
	@Autowired
	private CouponCampaignRepository campaignRepository;

	private UserEntity user(String key, String role, String status, String region) {
		return userRepository.save(UserEntity.builder()
				.oauthProvider("test").oauthId("region_coupon_" + key)
				.role(role).status(status).nickname("지역쿠폰테스트" + key).region(region)
				.build());
	}

	private StoreEntity store(String name, String sido) {
		return storeRepository.save(StoreEntity.builder()
				.storeName(name).role("OWNER").approvalStatus(StoreEntity.STATUS_APPROVED).sido(sido)
				.reliabilitySuspensionCount(0).reliabilityBanned(false)
				.build());
	}

	@Test
	void 선택한_시도의_활성_일반회원에게만_쿠폰이_발급된다() {
		UserEntity busanUser = user("busan", UserEntity.ROLE_USER, UserEntity.STATUS_ACTIVE, "부산 해운대구");
		UserEntity seoulUser = user("seoul", UserEntity.ROLE_USER, UserEntity.STATUS_ACTIVE, "서울 중구");
		UserEntity busanSuspended = user("busan-sus", UserEntity.ROLE_USER, UserEntity.STATUS_SUSPENDED, "부산 서구");
		UserEntity busanOwner = user("busan-owner", UserEntity.ROLE_OWNER, UserEntity.STATUS_ACTIVE, "부산 중구");

		var result = superAdminCouponService.createRegionCampaign(List.of("부산"), "RATE", 10, null, 3000, 0, 7, "테스트", 4L, false);

		assertTrue(result.success());
		List<CouponEntity> busanCoupons = couponRepository.findAll().stream()
				.filter(c -> CouponCampaignEntity.SCOPE_REGION.equals(c.getScope())).toList();
		assertTrue(busanCoupons.stream().anyMatch(c -> c.getIssuedToUserId().equals(busanUser.getId())));
		assertFalse(busanCoupons.stream().anyMatch(c -> c.getIssuedToUserId().equals(seoulUser.getId())), "다른 시도 회원은 받지 않는다");
		assertFalse(busanCoupons.stream().anyMatch(c -> c.getIssuedToUserId().equals(busanSuspended.getId())), "정지 회원은 받지 않는다");
		assertFalse(busanCoupons.stream().anyMatch(c -> c.getIssuedToUserId().equals(busanOwner.getId())), "점주 계정은 받지 않는다");
		assertEquals(result.issuedCount(), superAdminCouponService.countRegionRecipients(List.of("부산")));
	}

	@Test
	void 지역_쿠폰은_대상_시도의_매장에서만_쓸_수_있다() {
		UserEntity busanUser = user("redeem", UserEntity.ROLE_USER, UserEntity.STATUS_ACTIVE, "부산 부산진구");
		StoreEntity busanStore = store("부산테스트매장", "부산");
		StoreEntity seoulStore = store("서울테스트매장", "서울");
		StoreEntity noSidoStore = store("시도없는매장", null);

		assertTrue(superAdminCouponService.createRegionCampaign(List.of("부산"), "AMOUNT", null, 1000, null, 0, 7, null, 4L, false).success());
		CouponEntity coupon = couponRepository.findAll().stream()
				.filter(c -> c.getIssuedToUserId().equals(busanUser.getId())).findFirst().orElseThrow();

		assertEquals(coupon.getId(), couponService.validateForRedeem(busanUser.getId(), coupon.getId(), busanStore.getId(), 5000).getId());
		assertThrows(ResponseStatusException.class,
				() -> couponService.validateForRedeem(busanUser.getId(), coupon.getId(), seoulStore.getId(), 5000));
		assertThrows(ResponseStatusException.class,
				() -> couponService.validateForRedeem(busanUser.getId(), coupon.getId(), noSidoStore.getId(), 5000));
	}

	@Test
	void 입력값이_잘못되거나_대상_회원이_없으면_발행되지_않는다() {
		long before = campaignRepository.count();

		assertFalse(superAdminCouponService.createRegionCampaign(List.of(), "RATE", 10, null, null, 0, 7, null, 4L, false).success());
		assertFalse(superAdminCouponService.createRegionCampaign(List.of("없는시도"), "RATE", 10, null, null, 0, 7, null, 4L, false).success());
		assertFalse(superAdminCouponService.createRegionCampaign(List.of("제주"), "RATE", 95, null, null, 0, 7, null, 4L, false).success(), "할인율 90% 초과");
		assertFalse(superAdminCouponService.createRegionCampaign(List.of("제주"), "AMOUNT", null, 0, null, 0, 7, null, 4L, false).success(), "정액 0원");
		assertFalse(superAdminCouponService.createRegionCampaign(List.of("제주"), "RATE", 10, null, null, 0, 0, null, 4L, false).success(), "유효기간 0일");
		// 대상 회원이 한 명도 없는 시도는 발행 자체가 거부된다(공용 샘플 DB에 울산 회원이 있으면 이 검증은 건너뛴다).
		if (superAdminCouponService.countRegionRecipients(List.of("울산")) == 0) {
			assertFalse(superAdminCouponService.createRegionCampaign(List.of("울산"), "RATE", 10, null, null, 0, 7, null, 4L, false).success(), "대상 회원 없음");
		}

		assertEquals(before, campaignRepository.count(), "실패한 요청은 캠페인을 만들지 않는다");
	}
}

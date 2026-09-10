package net.dsa.girigiri;

import net.dsa.girigiri.domain.entity.ProductEntity;
import net.dsa.girigiri.repository.LikeRepository;
import net.dsa.girigiri.repository.ProductRepository;
import net.dsa.girigiri.repository.ReservationRepository;
import net.dsa.girigiri.repository.StoreRepository;
import net.dsa.girigiri.service.HomeService;
import net.dsa.girigiri.service.LikeService;
import net.dsa.girigiri.service.RecommendationService;
import net.dsa.girigiri.service.SearchService;
import net.dsa.girigiri.util.DiscountRateCalculator;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * 검증(더미데이터) — "HomeService/SearchService/LikeService/RecommendationService가 각자
 * discountRate()를 손으로 다시 구현하고 있다"던 중복 지적 수정 확인. 4곳 모두 DiscountRateCalculator
 * .fromPrices()에 위임하도록 고쳤으니, 같은 더미 원가/할인가를 넣었을 때 4곳 + 기준값(Calculator 직접
 * 호출)이 전부 같은 숫자를 내야 한다. private 메서드라 리플렉션으로 직접 호출한다(실제 호출 경로와
 * 동일한 코드 — DB는 안 건드림).
 */
class DiscountRateUnificationTest {

	@Test
	void 네_서비스의_할인율_계산이_전부_DiscountRateCalculator와_같은_값을_낸다() throws Exception {
		// 더미데이터: 원가 10,000원 / 할인가 6,000원 -> 40% 할인
		ProductEntity dummyProduct = ProductEntity.builder()
				.id(1L).storeId(1L).originalPrice(10000).discountedPrice(6000)
				.build();

		int expected = DiscountRateCalculator.fromPrices(10000, 6000);
		assertEquals(40, expected, "기준값 자체가 40%가 아니면 테스트 더미값부터 다시 잡아야 함");

		HomeService homeService = new HomeService(mock(ProductRepository.class), mock(StoreRepository.class),
				mock(ReservationRepository.class));
		SearchService searchService = new SearchService(mock(ProductRepository.class), mock(StoreRepository.class));
		LikeService likeService = new LikeService(mock(LikeRepository.class), mock(StoreRepository.class),
				mock(ProductRepository.class));
		RecommendationService recommendationService = new RecommendationService(mock(ReservationRepository.class),
				mock(ProductRepository.class), mock(StoreRepository.class));

		assertEquals(expected, invokeDiscountRate(homeService, dummyProduct), "HomeService");
		assertEquals(expected, invokeDiscountRate(searchService, dummyProduct), "SearchService");
		assertEquals(expected, invokeDiscountRate(likeService, dummyProduct), "LikeService");
		assertEquals(expected, invokeDiscountRate(recommendationService, dummyProduct), "RecommendationService");
	}

	private int invokeDiscountRate(Object service, ProductEntity product) throws Exception {
		Method m = service.getClass().getDeclaredMethod("discountRate", ProductEntity.class);
		m.setAccessible(true);
		return (int) m.invoke(service, product);
	}
}

package net.dsa.girigiri.service;

import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.StoreCardDto;
import net.dsa.girigiri.domain.entity.ProductEntity;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.repository.ProductRepository;
import net.dsa.girigiri.repository.StoreRepository;
import net.dsa.girigiri.util.DiscountRateCalculator;
import net.dsa.girigiri.util.CategoryDisplayUtil;
import net.dsa.girigiri.util.DistanceUtil;
import net.dsa.girigiri.util.PickupAvailabilityUtil;
import net.dsa.girigiri.util.StoreHoursUtil;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * "검색 · 필터 결과" 화면용 서비스. 홈(HomeService)과 달리 매장당 1개로 줄이지 않고
 * 조건에 맞는 active 상품을 전부 보여준다.
 */
@Service
@RequiredArgsConstructor
public class SearchService {

	private static final String STATUS_ACTIVE = "active";

	private final ProductRepository productRepository;
	private final StoreRepository storeRepository;

	/**
	 * @param keyword    상품명/매장명/카테고리 부분일치(대소문자 무시). null/빈값이면 전체.
	 * @param sort       "discount"(할인율순, 기본) | "price"(가격 낮은순) | "closing"(마감임박순) | "distance"(거리순)
	 * @param priceMin   할인가 하한(원). null이면 하한 없음.
	 * @param priceMax   할인가 상한(원). null이면 상한 없음.
	 * @param pickupNow  true면 "지금 주문해도 오늘 픽업 가능한 매장"만(PickupAvailabilityUtil 기준, 실시간 판단) —
	 *                   아래 pickupFrom/pickupTo 범위와 AND로 함께 적용된다(둘 다 지정하면 둘 다 만족해야 함).
	 * @param pickupFrom 픽업 마감시간(시, 0~24) 하한. null이면 하한 없음. 매장이 lastPickupTime을 아직 설정 안
	 *                   했으면(null) from/to 중 하나라도 지정된 경우 판단 불가로 제외한다.
	 * @param pickupTo   픽업 마감시간(시, 0~24) 상한(미만). null이면 상한 없음.
	 * @param userLat    사용자 위도(브라우저 Geolocation). null이면 거리 계산 생략 — "distance" 정렬을 요청해도
	 *                   할인율순으로 대체된다(sortComparator 참고).
	 * @param userLng    사용자 경도.
	 */
	public List<StoreCardDto> search(String keyword, String sort, Integer priceMin, Integer priceMax,
									  Boolean pickupNow, Integer pickupFrom, Integer pickupTo,
									  Set<Long> likedStoreIds, Double userLat, Double userLng) {
		Map<Long, StoreEntity> storesById = storeRepository.findAll().stream()
				.filter(s -> !StoreEntity.STATUS_SUSPENDED.equals(s.getStatus()))
				.collect(Collectors.toMap(StoreEntity::getId, s -> s));

		String kw = keyword == null ? "" : keyword.trim().toLowerCase();

		List<ProductEntity> filtered = productRepository.findAll().stream()
				.filter(p -> STATUS_ACTIVE.equals(p.getStatus()))
				.filter(p -> p.getRemainingQuantity() != null && p.getRemainingQuantity() > 0)
				.filter(p -> storesById.containsKey(p.getStoreId()))
				.filter(p -> matchesKeyword(p, storesById.get(p.getStoreId()), kw))
				.filter(p -> matchesPriceRange(p, priceMin, priceMax))
				.filter(p -> matchesPickup(storesById.get(p.getStoreId()), pickupNow, pickupFrom, pickupTo))
				.sorted(sortComparator(sort, storesById, userLat, userLng))
				.toList();

		return filtered.stream()
				.map(p -> toCardDto(storesById.get(p.getStoreId()), p, likedStoreIds, userLat, userLng))
				.toList();
	}

	private boolean matchesKeyword(ProductEntity product, StoreEntity store, String kw) {
		if (kw.isEmpty()) {
			return true;
		}
		return containsIgnoreCase(product.getName(), kw)
				|| containsIgnoreCase(store.getStoreName(), kw)
				|| containsIgnoreCase(store.getCategory(), kw);
	}

	private boolean containsIgnoreCase(String value, String kw) {
		return value != null && value.toLowerCase().contains(kw);
	}

	private boolean matchesPriceRange(ProductEntity product, Integer priceMin, Integer priceMax) {
		if (priceMin == null && priceMax == null) {
			return true;
		}
		Integer price = product.getDiscountedPrice();
		if (price == null) {
			return false;
		}
		if (priceMin != null && price < priceMin) {
			return false;
		}
		if (priceMax != null && price > priceMax) {
			return false;
		}
		return true;
	}

	private boolean matchesPickup(StoreEntity store, Boolean pickupNow, Integer pickupFrom, Integer pickupTo) {
		if (store == null) {
			return true;
		}
		if (Boolean.TRUE.equals(pickupNow)) {
			int prepTimeMinutes = store.getPrepTimeMinutes() != null
					? store.getPrepTimeMinutes() : PickupAvailabilityUtil.DEFAULT_PREP_TIME_MINUTES;
			if (!PickupAvailabilityUtil.canOrderNow(LocalDateTime.now(), store.getLastPickupTime(), prepTimeMinutes)) {
				return false;
			}
		}
		if (pickupFrom != null || pickupTo != null) {
			LocalTime lastPickupTime = store.getLastPickupTime();
			if (lastPickupTime == null) {
				return false;   // 마감시간을 매장이 아직 설정 안 했으면 시간대 범위 판단 불가
			}
			int hour = lastPickupTime.getHour();
			if (pickupFrom != null && hour < pickupFrom) {
				return false;
			}
			if (pickupTo != null && hour >= pickupTo) {
				return false;
			}
		}
		return true;
	}

	private Comparator<ProductEntity> sortComparator(String sort, Map<Long, StoreEntity> storesById,
													   Double userLat, Double userLng) {
		if ("price".equals(sort)) {
			return Comparator.comparing(p -> p.getDiscountedPrice() == null ? Integer.MAX_VALUE : p.getDiscountedPrice());
		}
		if ("closing".equals(sort)) {
			// 마감시각이 빠른(=임박한) 상품이 앞으로. 영업시간 정보가 없어 계산 불가한 상품은 맨 뒤로 보낸다.
			return Comparator.comparing(p -> closingTime(storesById.get(p.getStoreId())));
		}
		if ("distance".equals(sort) && userLat != null && userLng != null) {
			// 가까운(=거리값 작은) 상품이 앞으로. 좌표 없는 매장은 맨 뒤로.
			return Comparator.comparingDouble(p -> {
				StoreEntity store = storesById.get(p.getStoreId());
				return DistanceUtil.km(userLat, userLng, store == null ? null : store.getLatitude(),
						store == null ? null : store.getLongitude());
			});
		}
		return Comparator.comparingInt(this::discountRate).reversed();
	}

	private LocalDateTime closingTime(StoreEntity store) {
		if (store == null) {
			return LocalDateTime.MAX;
		}
		LocalDateTime closeAt = StoreHoursUtil.parse(store.getOperatingHours(), StoreHoursUtil.URGENT_THRESHOLD_MINUTES).closeAt();
		return closeAt != null ? closeAt : LocalDateTime.MAX;
	}

	private StoreCardDto toCardDto(StoreEntity store, ProductEntity product, Set<Long> likedStoreIds,
									Double userLat, Double userLng) {
		StoreHoursUtil.ClosingInfo closingInfo = StoreHoursUtil.parse(store.getOperatingHours(), StoreHoursUtil.URGENT_THRESHOLD_MINUTES);
		String distance = DistanceUtil.label(DistanceUtil.km(userLat, userLng, store.getLatitude(), store.getLongitude()));

		return StoreCardDto.builder()
				.id(product.getId())
				.storeId(store.getId())
				.thumbText(product.getName() == null || product.getName().isBlank()
						? "?" : product.getName().substring(0, 1))
				.thumbColor(thumbColor(store.getCategory()))
				.imageUrl(store.getImageUrl())
				.name(store.getStoreName())
				.category(store.getCategory())
				.distance(distance)
				.origPrice(formatWon(product.getOriginalPrice()))
				.salePrice(formatWon(product.getDiscountedPrice()))
				.discountRate("-" + discountRate(product) + "%")
				.leftLabel(closingInfo.label())
				.urgent(closingInfo.urgent())
				.liked(likedStoreIds.contains(store.getId()))
				.build();
	}

	// 변경됨 (2026-09-08, 코드 감사) — DiscountRateCalculator로 위임(4곳 중복 중 하나). 계산식은
	// 한 글자도 안 바꿈.
	private int discountRate(ProductEntity product) {
		return DiscountRateCalculator.fromPrices(product.getOriginalPrice(), product.getDiscountedPrice());
	}

	private String formatWon(Integer price) {
		return price == null ? "0원" : String.format("%,d원", price);
	}

	// 변경됨 (2026-09-08, 코드 감사) — CategoryDisplayUtil로 위임(6곳 넘게 중복돼 있던 것 중 하나,
	// StoreProductController 사본에만 있던 "카페/디저트"·"도시락/샐러드" 변형 인식도 같이 딸려온다).
	private String thumbColor(String category) {
		return CategoryDisplayUtil.thumbColor(category);
	}
}

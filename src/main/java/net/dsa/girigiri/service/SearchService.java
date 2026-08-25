package net.dsa.girigiri.service;

import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.StoreCardDto;
import net.dsa.girigiri.domain.entity.ProductEntity;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.repository.ProductRepository;
import net.dsa.girigiri.repository.StoreRepository;
import net.dsa.girigiri.util.StoreHoursUtil;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
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
	private static final long URGENT_THRESHOLD_MINUTES = 60;

	private final ProductRepository productRepository;
	private final StoreRepository storeRepository;

	/**
	 * @param keyword     상품명/매장명/카테고리 부분일치(대소문자 무시). null/빈값이면 전체.
	 * @param sort        "discount"(할인율순, 기본) | "price"(가격 낮은순) | "closing"(마감임박순) | "distance"(거리순)
	 * @param priceBucket "under5000" | "5000to10000" | "over10000" | null(전체)
	 * @param userLat     사용자 위도(브라우저 Geolocation). null이면 거리 계산 생략 — "distance" 정렬을 요청해도
	 *                    할인율순으로 대체된다(sortComparator 참고).
	 * @param userLng     사용자 경도.
	 */
	public List<StoreCardDto> search(String keyword, String sort, String priceBucket, Set<Long> likedStoreIds,
									  Double userLat, Double userLng) {
		Map<Long, StoreEntity> storesById = storeRepository.findAll().stream()
				.collect(Collectors.toMap(StoreEntity::getId, s -> s));

		String kw = keyword == null ? "" : keyword.trim().toLowerCase();

		List<ProductEntity> filtered = productRepository.findAll().stream()
				.filter(p -> STATUS_ACTIVE.equals(p.getStatus()))
				.filter(p -> p.getRemainingQuantity() != null && p.getRemainingQuantity() > 0)
				.filter(p -> storesById.containsKey(p.getStoreId()))
				.filter(p -> matchesKeyword(p, storesById.get(p.getStoreId()), kw))
				.filter(p -> matchesPriceBucket(p, priceBucket))
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

	private boolean matchesPriceBucket(ProductEntity product, String priceBucket) {
		if (priceBucket == null || priceBucket.isBlank()) {
			return true;
		}
		Integer price = product.getDiscountedPrice();
		if (price == null) {
			return false;
		}
		return switch (priceBucket) {
			case "under5000" -> price <= 5000;
			case "5000to10000" -> price > 5000 && price <= 10000;
			case "over10000" -> price > 10000;
			default -> true;
		};
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
			return Comparator.comparingDouble(p -> distanceKm(storesById.get(p.getStoreId()), userLat, userLng));
		}
		return Comparator.comparingInt(this::discountRate).reversed();
	}

	private LocalDateTime closingTime(StoreEntity store) {
		if (store == null) {
			return LocalDateTime.MAX;
		}
		LocalDateTime closeAt = StoreHoursUtil.parse(store.getOperatingHours(), URGENT_THRESHOLD_MINUTES).closeAt();
		return closeAt != null ? closeAt : LocalDateTime.MAX;
	}

	/**
	 * 두 좌표 사이 직선 거리(km) — Haversine 공식. 좌표가 없는 매장은 Double.MAX_VALUE로 보내
	 * 거리순 정렬에서 맨 뒤로 가게 한다.
	 */
	private double distanceKm(StoreEntity store, double userLat, double userLng) {
		if (store == null || store.getLatitude() == null || store.getLongitude() == null) {
			return Double.MAX_VALUE;
		}
		double earthRadiusKm = 6371.0;
		double dLat = Math.toRadians(store.getLatitude() - userLat);
		double dLng = Math.toRadians(store.getLongitude() - userLng);
		double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
				+ Math.cos(Math.toRadians(userLat)) * Math.cos(Math.toRadians(store.getLatitude()))
				* Math.sin(dLng / 2) * Math.sin(dLng / 2);
		double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
		return earthRadiusKm * c;
	}

	/** "350m" | "1.2km" 형태 라벨. 좌표가 없어 계산 불가하면 빈 문자열. */
	private String distanceLabel(StoreEntity store, Double userLat, Double userLng) {
		if (userLat == null || userLng == null || store == null
				|| store.getLatitude() == null || store.getLongitude() == null) {
			return "";
		}
		double km = distanceKm(store, userLat, userLng);
		return km < 1 ? Math.round(km * 1000) + "m" : String.format("%.1fkm", km);
	}

	private StoreCardDto toCardDto(StoreEntity store, ProductEntity product, Set<Long> likedStoreIds,
									Double userLat, Double userLng) {
		StoreHoursUtil.ClosingInfo closingInfo = StoreHoursUtil.parse(store.getOperatingHours(), URGENT_THRESHOLD_MINUTES);

		return StoreCardDto.builder()
				.id(product.getId())
				.storeId(store.getId())
				.thumbText(product.getName() == null || product.getName().isBlank()
						? "?" : product.getName().substring(0, 1))
				.thumbColor(thumbColor(store.getCategory()))
				.name(store.getStoreName())
				.category(store.getCategory())
				.distance(distanceLabel(store, userLat, userLng))
				.origPrice(formatWon(product.getOriginalPrice()))
				.salePrice(formatWon(product.getDiscountedPrice()))
				.discountRate("-" + discountRate(product) + "%")
				.leftLabel(closingInfo.label())
				.urgent(closingInfo.urgent())
				.liked(likedStoreIds.contains(store.getId()))
				.build();
	}

	private int discountRate(ProductEntity product) {
		if (product.getOriginalPrice() == null || product.getOriginalPrice() == 0 || product.getDiscountedPrice() == null) {
			return 0;
		}
		return (int) Math.round(100.0 * (product.getOriginalPrice() - product.getDiscountedPrice()) / product.getOriginalPrice());
	}

	private String formatWon(Integer price) {
		return price == null ? "0원" : String.format("%,d원", price);
	}

	private String thumbColor(String category) {
		if (category == null) {
			return "var(--c-line-weak)";
		}
		return switch (category) {
			case "베이커리" -> "var(--c-accent-weak)";
			case "카페" -> "var(--c-info-weak)";
			case "반찬", "도시락" -> "var(--c-primary-weak)";
			default -> "var(--c-line-weak)";
		};
	}
}

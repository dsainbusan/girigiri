package net.dsa.girigiri.service;

import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.RecommendationSectionDto;
import net.dsa.girigiri.domain.dto.StoreCardDto;
import net.dsa.girigiri.domain.entity.ProductEntity;
import net.dsa.girigiri.domain.entity.ReservationEntity;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.repository.ProductRepository;
import net.dsa.girigiri.repository.ReservationRepository;
import net.dsa.girigiri.repository.StoreRepository;
import net.dsa.girigiri.util.DistanceUtil;
import net.dsa.girigiri.util.StoreHoursUtil;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 강노은: 홈 화면 "개인화 추천 섹션" (WBS 4.0 탐색·검색, 맨 마지막 순서로 미뤄뒀던 항목).
 *
 * 룰: 이 사용자가 픽업까지 완료(=진짜 구매)한 예약들의 매장 카테고리를 집계해서 가장 많이 산
 * 카테고리를 뽑고, 그 카테고리의 active(+재고 있음) 상품 중 할인율 높은 순으로 추천한다.
 *
 * 신규 유저(구매 이력 없음) / 로그인 안 한 사용자 / 그 카테고리에 지금 마감세일이 없는 경우는
 * "별도 동작"으로 전체 활성 상품 중 할인율 상위를 보여준다(완전히 숨기지 않고 폴백) — 결과물
 * 요구사항의 "신규 유저 별도 동작"에 해당. 품절(재고 0) 상품은 애초에 후보에서 제외한다.
 *
 * 리뷰에서 지적된 것들 반영: 매장당 하나로 중복 제거(HomeService와 동일 패턴), 메인 목록
 * (storeCards)에 이미 나온 매장은 제외, 거리/마감임박 정보를 실제 값으로 채움(전엔 항상 빈값이었음).
 */
@Service
@RequiredArgsConstructor
public class RecommendationService {

	private static final String STATUS_ACTIVE = "active";
	private static final long URGENT_THRESHOLD_MINUTES = 60;
	// 픽업 완료 = 실제로 구매가 확정된 건 (ReviewService#canWriteReview와 같은 기준).
	// 결제만 하고 아직 안 찾아간 예약(confirmed/ready)은 취소될 수도 있어서 "구매 이력"에서 뺀다.
	private static final String STATUS_PICKED = "picked";
	private static final int MAX_RECOMMENDATIONS = 4;

	private final ReservationRepository reservationRepository;
	private final ProductRepository productRepository;
	private final StoreRepository storeRepository;

	/**
	 * @param excludeStoreIds 메인 "내 주변 마감세일" 목록에 이미 나온 매장 id들 — 같은 가게가
	 *                        홈 화면에 두 번 뜨지 않게 후보에서 미리 뺀다.
	 */
	public RecommendationSectionDto getRecommendations(Long userId, Set<Long> likedStoreIds,
														 Set<Long> excludeStoreIds, Double userLat, Double userLng) {
		// 강노은: HomeService처럼 매장당 할인율 1위 상품 하나만 남긴다 — 안 그러면 한 가게 상품이
		// 추천 4칸을 다 차지할 수 있다(리뷰 지적 사항).
		Map<Long, ProductEntity> bestProductByStoreId = productRepository.findAll().stream()
				.filter(p -> STATUS_ACTIVE.equals(p.getStatus()))
				.filter(p -> p.getRemainingQuantity() != null && p.getRemainingQuantity() > 0)
				.filter(p -> !excludeStoreIds.contains(p.getStoreId()))
				.collect(Collectors.toMap(
						ProductEntity::getStoreId,
						product -> product,
						(a, b) -> discountRate(a) >= discountRate(b) ? a : b
				));

		Map<Long, StoreEntity> storesById = storeRepository.findAllById(bestProductByStoreId.keySet()).stream()
				.collect(Collectors.toMap(StoreEntity::getId, store -> store));

		String purchasedCategory = topPurchasedCategory(userId);

		List<Map.Entry<Long, ProductEntity>> candidates = bestProductByStoreId.entrySet().stream()
				.filter(entry -> storesById.containsKey(entry.getKey()))
				.toList();
		List<Map.Entry<Long, ProductEntity>> byCategory = purchasedCategory == null
				? candidates
				: candidates.stream()
						.filter(entry -> purchasedCategory.equals(storesById.get(entry.getKey()).getCategory()))
						.toList();

		// 그 카테고리에 지금 마감세일 상품이 하나도 없으면 전체 풀로 폴백 — 최종적으로 어떤 카테고리
		// 기준으로 추천했는지는 별도 변수(topCategory)로 둬서 위 필터 람다에는 영향 없게 한다.
		String topCategory = purchasedCategory;
		List<Map.Entry<Long, ProductEntity>> finalCandidates = byCategory;
		if (finalCandidates.isEmpty()) {
			finalCandidates = candidates;
			topCategory = null;
		}

		List<StoreCardDto> cards = finalCandidates.stream()
				.sorted(Comparator.comparingInt((Map.Entry<Long, ProductEntity> entry) -> discountRate(entry.getValue())).reversed())
				.limit(MAX_RECOMMENDATIONS)
				.map(entry -> toCardDto(storesById.get(entry.getKey()), entry.getValue(), likedStoreIds, userLat, userLng))
				.toList();

		String title = topCategory != null
				? topCategory + " 좋아하시는군요, 이건 어때요?"
				: "지금 인기있는 마감세일";
		return new RecommendationSectionDto(title, cards);
	}

	/** 픽업 완료 이력이 있는 매장들의 카테고리 중 가장 많이 등장한 것. 이력이 없으면(신규 유저) null. */
	private String topPurchasedCategory(Long userId) {
		if (userId == null) {
			return null;
		}
		List<ReservationEntity> picked = reservationRepository
				.findByUserIdAndStatusInOrderByReservedAtDesc(userId, List.of(STATUS_PICKED));
		if (picked.isEmpty()) {
			return null;
		}

		Map<Long, StoreEntity> storesById = storeRepository.findAllById(
						picked.stream().map(ReservationEntity::getStoreId).distinct().toList())
				.stream()
				.collect(Collectors.toMap(StoreEntity::getId, store -> store));

		return picked.stream()
				.map(r -> storesById.get(r.getStoreId()))
				.filter(Objects::nonNull)
				.map(StoreEntity::getCategory)
				.filter(Objects::nonNull)
				// 강노은: 동점(카테고리별 구매 횟수가 같음)일 때 매번 다른 게 뽑히지 않게(HashMap 순회
				// 순서 비결정적) 카테고리명으로 2차 정렬해서 항상 같은 결과가 나오게 한다(리뷰 지적 사항).
				.collect(Collectors.groupingBy(category -> category, Collectors.counting()))
				.entrySet().stream()
				.max(Map.Entry.<String, Long>comparingByValue().thenComparing(entry -> entry.getKey(), Comparator.reverseOrder()))
				.map(Map.Entry::getKey)
				.orElse(null);
	}

	// 강노은: HomeService/SearchService와 같은 작은 헬퍼들 — 지금까지도 서비스마다 각자 들고 있던
	// 컨벤션(공유 클래스로 뺄 정도는 아니라고 판단)을 그대로 따른다. 거리 계산만 리뷰 지적으로
	// DistanceUtil로 통합했다(세 서비스가 각자 베끼던 부분이라 그 부분만 공유 유틸로 뺌).
	private StoreCardDto toCardDto(StoreEntity store, ProductEntity product, Set<Long> likedStoreIds,
									Double userLat, Double userLng) {
		StoreHoursUtil.ClosingInfo closingInfo = StoreHoursUtil.parse(store.getOperatingHours(), URGENT_THRESHOLD_MINUTES);
		String distance = DistanceUtil.label(DistanceUtil.km(userLat, userLng, store.getLatitude(), store.getLongitude()));

		return StoreCardDto.builder()
				.id(product.getId())
				.storeId(store.getId())
				.thumbText(product.getName() == null || product.getName().isBlank()
						? "?" : product.getName().substring(0, 1))
				.thumbColor(thumbColor(store.getCategory()))
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

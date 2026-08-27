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
 */
@Service
@RequiredArgsConstructor
public class RecommendationService {

	private static final String STATUS_ACTIVE = "active";
	// 픽업 완료 = 실제로 구매가 확정된 건 (ReviewService#canWriteReview와 같은 기준).
	// 결제만 하고 아직 안 찾아간 예약(confirmed/ready)은 취소될 수도 있어서 "구매 이력"에서 뺀다.
	private static final String STATUS_PICKED = "picked";
	private static final int MAX_RECOMMENDATIONS = 4;

	private final ReservationRepository reservationRepository;
	private final ProductRepository productRepository;
	private final StoreRepository storeRepository;

	public RecommendationSectionDto getRecommendations(Long userId, Set<Long> likedStoreIds) {
		List<ProductEntity> activePool = productRepository.findAll().stream()
				.filter(p -> STATUS_ACTIVE.equals(p.getStatus()))
				.filter(p -> p.getRemainingQuantity() != null && p.getRemainingQuantity() > 0)
				.toList();

		Map<Long, StoreEntity> storesById = storeRepository.findAllById(
						activePool.stream().map(ProductEntity::getStoreId).distinct().toList())
				.stream()
				.collect(Collectors.toMap(StoreEntity::getId, store -> store));

		String purchasedCategory = topPurchasedCategory(userId);

		List<ProductEntity> candidates = purchasedCategory == null
				? activePool
				: activePool.stream()
						.filter(p -> purchasedCategory.equals(categoryOf(storesById, p)))
						.toList();

		// 그 카테고리에 지금 마감세일 상품이 하나도 없으면 전체 풀로 폴백 — 최종적으로 어떤 카테고리
		// 기준으로 추천했는지는 별도 변수(topCategory)로 둬서 위 필터 람다에는 영향 없게 한다.
		String topCategory = purchasedCategory;
		if (candidates.isEmpty()) {
			candidates = activePool;
			topCategory = null;
		}

		List<StoreCardDto> cards = candidates.stream()
				.filter(p -> storesById.containsKey(p.getStoreId()))
				.sorted(Comparator.comparingInt(this::discountRate).reversed())
				.limit(MAX_RECOMMENDATIONS)
				.map(p -> toCardDto(storesById.get(p.getStoreId()), p, likedStoreIds))
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
				.collect(Collectors.groupingBy(category -> category, Collectors.counting()))
				.entrySet().stream()
				.max(Map.Entry.comparingByValue())
				.map(Map.Entry::getKey)
				.orElse(null);
	}

	private String categoryOf(Map<Long, StoreEntity> storesById, ProductEntity product) {
		StoreEntity store = storesById.get(product.getStoreId());
		return store == null ? null : store.getCategory();
	}

	// 강노은: HomeService/SearchService와 같은 작은 헬퍼들 — 지금까지도 서비스마다 각자 들고 있던
	// 컨벤션(공유 클래스로 뺄 정도는 아니라고 판단)을 그대로 따른다.
	private StoreCardDto toCardDto(StoreEntity store, ProductEntity product, Set<Long> likedStoreIds) {
		return StoreCardDto.builder()
				.id(product.getId())
				.storeId(store.getId())
				.thumbText(product.getName() == null || product.getName().isBlank()
						? "?" : product.getName().substring(0, 1))
				.thumbColor(thumbColor(store.getCategory()))
				.name(store.getStoreName())
				.category(store.getCategory())
				.distance("")
				.origPrice(formatWon(product.getOriginalPrice()))
				.salePrice(formatWon(product.getDiscountedPrice()))
				.discountRate("-" + discountRate(product) + "%")
				.leftLabel("")
				.urgent(false)
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

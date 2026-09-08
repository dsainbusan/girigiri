package net.dsa.girigiri.service;

import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.LikedStoreDto;
import net.dsa.girigiri.domain.entity.LikeEntity;
import net.dsa.girigiri.domain.entity.ProductEntity;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.repository.LikeRepository;
import net.dsa.girigiri.repository.ProductRepository;
import net.dsa.girigiri.repository.StoreRepository;
import net.dsa.girigiri.util.DiscountRateCalculator;
import net.dsa.girigiri.util.CategoryDisplayUtil;
import net.dsa.girigiri.util.StoreHoursUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LikeService {

	private static final String STATUS_ACTIVE = "active";

	private final LikeRepository likeRepository;
	private final StoreRepository storeRepository;
	private final ProductRepository productRepository;

	/** 로그인 안 했으면(userId==null) 빈 집합 — 홈/검색 카드에 찜 여부를 표시할 때 쓴다. */
	public Set<Long> getLikedStoreIds(Long userId) {
		if (userId == null) {
			return Set.of();
		}
		// 변경됨 (2026-09-08, 코드 감사) — findAll() + 자바 필터링 대신 DB 쿼리로.
		return likeRepository.findByUserId(userId).stream()
				.map(LikeEntity::getStoreId)
				.collect(Collectors.toSet());
	}

	public boolean isLiked(Long userId, Long storeId) {
		return userId != null && getLikedStoreIds(userId).contains(storeId);
	}

	/** @return 토글 후 상태 (true = 찜한 상태가 됨) */
	@Transactional
	public boolean toggle(Long userId, Long storeId) {
		// 변경됨 (2026-09-08, 코드 감사) — findAll() + 자바 필터링 대신 DB 쿼리로.
		List<LikeEntity> existing = likeRepository.findByUserIdAndStoreId(userId, storeId);

		if (!existing.isEmpty()) {
			likeRepository.deleteAll(existing);
			return false;
		}
		likeRepository.save(LikeEntity.builder().userId(userId).storeId(storeId).build());
		return true;
	}

	/** 찜 목록 화면용: 찜한 매장 전부 — 세일 중이면 대표 상품 정보, 아니면 "세일 없음" 표시. */
	public List<LikedStoreDto> getLikedStores(Long userId) {
		Set<Long> likedStoreIds = getLikedStoreIds(userId);
		if (likedStoreIds.isEmpty()) {
			return List.of();
		}

		Map<Long, StoreEntity> storesById = storeRepository.findAllById(likedStoreIds).stream()
				.collect(Collectors.toMap(StoreEntity::getId, s -> s));

		Map<Long, ProductEntity> bestActiveByStoreId = productRepository.findAll().stream()
				.filter(p -> STATUS_ACTIVE.equals(p.getStatus()))
				.filter(p -> p.getRemainingQuantity() != null && p.getRemainingQuantity() > 0)
				.filter(p -> likedStoreIds.contains(p.getStoreId()))
				.collect(Collectors.toMap(
						ProductEntity::getStoreId,
						p -> p,
						(a, b) -> discountRate(a) >= discountRate(b) ? a : b
				));

		return likedStoreIds.stream()
				.map(storesById::get)
				.filter(java.util.Objects::nonNull)
				.map(store -> toLikedDto(store, bestActiveByStoreId.get(store.getId())))
				.sorted(Comparator.comparing(LikedStoreDto::onSale).reversed())
				.toList();
	}

	private LikedStoreDto toLikedDto(StoreEntity store, ProductEntity product) {
		String thumbText = store.getStoreName() == null || store.getStoreName().isBlank()
				? "?" : store.getStoreName().substring(0, 1);
		String thumbColor = thumbColor(store.getCategory());

		if (product == null) {
			return new LikedStoreDto(store.getId(), store.getStoreName(), store.getCategory(),
					thumbText, thumbColor, false, null, null, null, "지금은 세일 중이 아니에요");
		}

		StoreHoursUtil.ClosingInfo closingInfo = StoreHoursUtil.parse(store.getOperatingHours(), StoreHoursUtil.URGENT_THRESHOLD_MINUTES);
		return new LikedStoreDto(store.getId(), store.getStoreName(), store.getCategory(),
				thumbText, thumbColor, true,
				"-" + discountRate(product) + "%", formatWon(product.getDiscountedPrice()),
				closingInfo.label(), null);
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

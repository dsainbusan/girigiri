package net.dsa.girigiri.service;

import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.LikedStoreDto;
import net.dsa.girigiri.domain.entity.LikeEntity;
import net.dsa.girigiri.domain.entity.ProductEntity;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.repository.LikeRepository;
import net.dsa.girigiri.repository.ProductRepository;
import net.dsa.girigiri.repository.StoreRepository;
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
	private static final long URGENT_THRESHOLD_MINUTES = 60;

	private final LikeRepository likeRepository;
	private final StoreRepository storeRepository;
	private final ProductRepository productRepository;

	/** 로그인 안 했으면(userId==null) 빈 집합 — 홈/검색 카드에 찜 여부를 표시할 때 쓴다. */
	public Set<Long> getLikedStoreIds(Long userId) {
		if (userId == null) {
			return Set.of();
		}
		return likeRepository.findAll().stream()
				.filter(l -> userId.equals(l.getUserId()))
				.map(LikeEntity::getStoreId)
				.collect(Collectors.toSet());
	}

	public boolean isLiked(Long userId, Long storeId) {
		return userId != null && getLikedStoreIds(userId).contains(storeId);
	}

	/** @return 토글 후 상태 (true = 찜한 상태가 됨) */
	@Transactional
	public boolean toggle(Long userId, Long storeId) {
		List<LikeEntity> existing = likeRepository.findAll().stream()
				.filter(l -> userId.equals(l.getUserId()) && storeId.equals(l.getStoreId()))
				.toList();

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

		StoreHoursUtil.ClosingInfo closingInfo = StoreHoursUtil.parse(store.getOperatingHours(), URGENT_THRESHOLD_MINUTES);
		return new LikedStoreDto(store.getId(), store.getStoreName(), store.getCategory(),
				thumbText, thumbColor, true,
				"-" + discountRate(product) + "%", formatWon(product.getDiscountedPrice()),
				closingInfo.label(), null);
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

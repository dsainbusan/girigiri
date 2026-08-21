package net.dsa.girigiri.service;

import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.StoreCardDto;
import net.dsa.girigiri.domain.entity.ProductEntity;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.repository.ProductRepository;
import net.dsa.girigiri.repository.ReservationRepository;
import net.dsa.girigiri.repository.StoreRepository;
import net.dsa.girigiri.util.StoreHoursUtil;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 홈 화면(메인) 조립용 서비스.
 * TODO(강노은): 지금은 매장당 할인율 1위 상품 하나만 대표로 보여준다.
 * 페이징/거리순 정렬은 사용자 위치 연동 이후에 추가할 것.
 */
@Service
@RequiredArgsConstructor
public class HomeService {

	private static final String STATUS_ACTIVE = "active";
	private static final long URGENT_THRESHOLD_MINUTES = 60;

	private final ProductRepository productRepository;
	private final StoreRepository storeRepository;
	private final ReservationRepository reservationRepository;

	/**
	 * 홈 배너용: 오늘 픽업 완료된 예약 건수. ("오늘 N명이 마감 음식을 구했어요")
	 */
	public long getTodayRescueCount() {
		LocalDate today = LocalDate.now();
		return reservationRepository.findAll().stream()
				.filter(r -> "picked".equals(r.getStatus()))
				.filter(r -> r.getPickedAt() != null && r.getPickedAt().toLocalDate().equals(today))
				.count();
	}

	public List<StoreCardDto> getActiveStoreCards(Set<Long> likedStoreIds) {
		Map<Long, ProductEntity> bestProductByStoreId = productRepository.findAll().stream()
				.filter(product -> STATUS_ACTIVE.equals(product.getStatus()))
				.filter(product -> product.getRemainingQuantity() != null && product.getRemainingQuantity() > 0)
				.collect(Collectors.toMap(
						ProductEntity::getStoreId,
						product -> product,
						(a, b) -> discountRate(a) >= discountRate(b) ? a : b
				));

		Map<Long, StoreEntity> storesById = storeRepository.findAllById(bestProductByStoreId.keySet()).stream()
				.collect(Collectors.toMap(StoreEntity::getId, store -> store));

		return bestProductByStoreId.entrySet().stream()
				.filter(entry -> storesById.containsKey(entry.getKey()))
				.map(entry -> toCardDto(storesById.get(entry.getKey()), entry.getValue(), likedStoreIds))
				.sorted(Comparator.comparing(StoreCardDto::isUrgent).reversed())
				.toList();
	}

	private StoreCardDto toCardDto(StoreEntity store, ProductEntity product, Set<Long> likedStoreIds) {
		StoreHoursUtil.ClosingInfo closingInfo = StoreHoursUtil.parse(store.getOperatingHours(), URGENT_THRESHOLD_MINUTES);

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

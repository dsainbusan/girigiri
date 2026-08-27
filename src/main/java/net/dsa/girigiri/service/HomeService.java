package net.dsa.girigiri.service;

import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.StoreCardDto;
import net.dsa.girigiri.domain.entity.ProductEntity;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.repository.ProductRepository;
import net.dsa.girigiri.repository.ReservationRepository;
import net.dsa.girigiri.repository.StoreRepository;
import net.dsa.girigiri.util.DistanceUtil;
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

	/**
	 * 변경됨 (강노은) — 왜: "현재 위치 기준 거리순 카드 목록" 요구사항. userLat/userLng가 있으면(=브라우저
	 * Geolocation 권한을 받은 경우) 카드에 실제 거리 라벨을 채운다.
	 *
	 * 정렬은 위치 유무와 무관하게 "마감임박(urgent) 우선"이 항상 먼저다 — 거리순이라고 마감임박
	 * 우선순위 자체를 지워버리면 이 앱의 핵심 가치(마감임박 긴급구제)가 흐려진다. 위치가 있으면
	 * 그 안에서(같은 임박도 안에서만) 가까운 순으로 2차 정렬한다.
	 *
	 * 리뷰에서 지적된 것들도 여기서 같이 고쳤다: ClosingInfo를 매장당 한 번만 계산해서 정렬 키/카드
	 * 필드에 재사용(전에는 두 번 파싱했음), 거리 계산은 중복돼있던 걸 DistanceUtil로 통합.
	 */
	public List<StoreCardDto> getActiveStoreCards(Set<Long> likedStoreIds, Double userLat, Double userLng) {
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

		Map<Long, StoreHoursUtil.ClosingInfo> closingInfoByStoreId = storesById.entrySet().stream()
				.collect(Collectors.toMap(Map.Entry::getKey,
						e -> StoreHoursUtil.parse(e.getValue().getOperatingHours(), URGENT_THRESHOLD_MINUTES)));

		boolean hasLocation = userLat != null && userLng != null;
		Comparator<Map.Entry<Long, ProductEntity>> comparator = Comparator.comparing(
				(Map.Entry<Long, ProductEntity> entry) -> closingInfoByStoreId.get(entry.getKey()).urgent()).reversed();
		if (hasLocation) {
			comparator = comparator.thenComparingDouble(entry -> {
				StoreEntity store = storesById.get(entry.getKey());
				return DistanceUtil.km(userLat, userLng, store.getLatitude(), store.getLongitude());
			});
		}

		return bestProductByStoreId.entrySet().stream()
				.filter(entry -> storesById.containsKey(entry.getKey()))
				.sorted(comparator)
				.map(entry -> toCardDto(storesById.get(entry.getKey()), entry.getValue(), likedStoreIds,
						closingInfoByStoreId.get(entry.getKey()), userLat, userLng))
				.toList();
	}

	private StoreCardDto toCardDto(StoreEntity store, ProductEntity product, Set<Long> likedStoreIds,
									StoreHoursUtil.ClosingInfo closingInfo, Double userLat, Double userLng) {
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

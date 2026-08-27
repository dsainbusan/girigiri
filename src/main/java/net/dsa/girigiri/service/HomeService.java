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

	/**
	 * 변경됨 (강노은) — 왜: "현재 위치 기준 거리순 카드 목록" 요구사항. userLat/userLng가 있으면(=브라우저
	 * Geolocation 권한을 받은 경우) 거리 오름차순으로 정렬하고 카드에 실제 거리 라벨을 채운다.
	 * 좌표가 없으면(권한 거부/미지원/최초 로드 등) 기존처럼 마감임박(urgent) 우선 정렬로 폴백한다.
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

		boolean hasLocation = userLat != null && userLng != null;
		Comparator<Map.Entry<Long, ProductEntity>> comparator = hasLocation
				? Comparator.comparingDouble(entry -> distanceKm(storesById.get(entry.getKey()), userLat, userLng))
				: Comparator.comparing((Map.Entry<Long, ProductEntity> entry) -> isUrgent(storesById.get(entry.getKey()))).reversed();

		return bestProductByStoreId.entrySet().stream()
				.filter(entry -> storesById.containsKey(entry.getKey()))
				.sorted(comparator)
				.map(entry -> toCardDto(storesById.get(entry.getKey()), entry.getValue(), likedStoreIds, userLat, userLng))
				.toList();
	}

	private boolean isUrgent(StoreEntity store) {
		if (store == null) {
			return false;
		}
		return StoreHoursUtil.parse(store.getOperatingHours(), URGENT_THRESHOLD_MINUTES).urgent();
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

	// 강노은: SearchService와 같은 계산(Haversine) — 두 서비스가 이미 thumbColor/discountRate/formatWon도
	// 각자 들고 있는 것과 같은 이유(작은 헬퍼라 공유 클래스 만들 정도는 아니라고 판단)로 여기도 그대로 둔다.
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

	/** "350m" | "1.2km" 형태 라벨. 좌표가 없어 계산 불가하면 빈 문자열(=카드에서 거리 부분 생략). */
	private String distanceLabel(StoreEntity store, Double userLat, Double userLng) {
		if (userLat == null || userLng == null || store == null
				|| store.getLatitude() == null || store.getLongitude() == null) {
			return "";
		}
		double km = distanceKm(store, userLat, userLng);
		return km < 1 ? Math.round(km * 1000) + "m" : String.format("%.1fkm", km);
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

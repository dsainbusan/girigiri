package net.dsa.girigiri.service;

import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.RegionStoreRowDto;
import net.dsa.girigiri.domain.entity.CouponCampaignEntity;
import net.dsa.girigiri.domain.entity.CouponStoreEntity;
import net.dsa.girigiri.domain.entity.NotificationEntity;
import net.dsa.girigiri.domain.entity.ProductEntity;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.domain.entity.UserEntity;
import net.dsa.girigiri.domain.entity.ReservationEntity;
import net.dsa.girigiri.repository.CouponCampaignRepository;
import net.dsa.girigiri.repository.CouponStoreRepository;
import net.dsa.girigiri.repository.NotificationRepository;
import net.dsa.girigiri.repository.ProductRepository;
import net.dsa.girigiri.repository.ReservationRepository;
import net.dsa.girigiri.repository.StoreRepository;
import net.dsa.girigiri.repository.UserRepository;
import net.dsa.girigiri.util.DashboardPolicy;
import net.dsa.girigiri.util.SellThroughClassifier;
import net.dsa.girigiri.util.StoreHoursUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 지역→매장 드릴다운(통계 대시보드 "지역별 현황" 행 클릭) 도메인 서비스 — 2026-10-01 신규.
 * 운영자가 "소진율 낮은 지역 → 원인 매장 확인 → 쿠폰/제안 조치"를 한 화면에서 끝내는 흐름의
 * 목록 집계를 담당한다. 쿠폰 발행·제안 발송 자체는 SuperAdminCouponService/CouponService가 맡는다.
 */
@Service
@RequiredArgsConstructor
public class SuperAdminRegionService {

	private static final DateTimeFormatter MD_FORMAT = DateTimeFormatter.ofPattern("M/d");

	private final StoreRepository storeRepository;
	private final ProductRepository productRepository;
	private final UserRepository userRepository;
	private final CouponStoreRepository couponStoreRepository;
	private final CouponCampaignRepository couponCampaignRepository;
	private final NotificationRepository notificationRepository;
	// 추가됨 (2026-10-01, 점주 운영 제안) — "할인 시작 시간 앞당기기" 템플릿의 자동 삽입값(마감
	// N분 전 집중 판매) 계산용.
	private final ReservationRepository reservationRepository;
	private final NotificationService notificationService;

	public static final String TEMPLATE_CLOSING_TIME_EARLIER = "CLOSING_TIME_EARLIER";
	public static final String TEMPLATE_PICKUP_TIME_LONGER = "PICKUP_TIME_LONGER";
	public static final String TEMPLATE_PHOTO_DESCRIPTION = "PHOTO_DESCRIPTION";
	public static final String TEMPLATE_BUNDLE_SUGGESTION = "BUNDLE_SUGGESTION";
	public static final String TEMPLATE_REGISTER_ENCOURAGE = "REGISTER_ENCOURAGE";
	public static final String TEMPLATE_CUSTOM = "CUSTOM";

	@Transactional(readOnly = true)
	public List<RegionStoreRowDto> getRegionStores(String sido, int days) {
		LocalDate today = LocalDate.now();
		LocalDate windowStart = today.minusDays(days - 1L);
		LocalDateTime windowStartAt = windowStart.atStartOfDay();
		LocalDateTime windowEndAt = today.plusDays(1).atStartOfDay();

		List<StoreEntity> stores = storeRepository.findBySidoAndApprovalStatus(sido, StoreEntity.STATUS_APPROVED);
		Map<Long, String> ownerNicknames = userRepository.findAllById(
						stores.stream().map(StoreEntity::getOwnerId).filter(Objects::nonNull).distinct().toList())
				.stream().collect(Collectors.toMap(UserEntity::getId, UserEntity::getNickname));

		List<RegionStoreRowDto> rows = stores.stream()
				.map(store -> buildRow(store, windowStartAt, windowEndAt, ownerNicknames))
				.collect(Collectors.toList());

		// 소진율 낮은 순 — "등록 없음"(판정 불가)은 맨 뒤로.
		rows.sort(Comparator
				.comparing((RegionStoreRowDto r) -> r.sellThroughPercent() == null)
				.thenComparing(r -> r.sellThroughPercent() == null ? Integer.MAX_VALUE : r.sellThroughPercent()));
		return rows;
	}

	private RegionStoreRowDto buildRow(StoreEntity store, LocalDateTime windowStartAt, LocalDateTime windowEndAt,
	                                    Map<Long, String> ownerNicknames) {
		List<ProductEntity> products = productRepository.findByStoreId(store.getId()).stream()
				.filter(p -> !"draft".equals(p.getStatus()) && !"skipped".equals(p.getStatus()))
				.filter(p -> p.getRegisteredAt() != null
						&& !p.getRegisteredAt().isBefore(windowStartAt) && p.getRegisteredAt().isBefore(windowEndAt))
				.toList();

		int registered = products.size();
		int sold = (int) products.stream().filter(p -> "sold".equals(p.getStatus())).count();
		int expired = (int) products.stream().filter(p -> "expired".equals(p.getStatus())).count();

		var result = SellThroughClassifier.classify(registered, sold,
				DashboardPolicy.REGION_SAMPLE_SIZE_MIN, DashboardPolicy.REGION_LOW_SELLTHROUGH_PERCENT);

		String pickupTimeLabel = store.getLastPickupTime() != null
				? store.getLastPickupTime().format(DateTimeFormatter.ofPattern("HH:mm"))
				: "제한 없음";

		String lastRegisteredAtLabel = products.stream()
				.map(ProductEntity::getRegisteredAt)
				.filter(Objects::nonNull)
				.max(Comparator.naturalOrder())
				.map(dt -> dt.toLocalDate().format(MD_FORMAT))
				.orElse("-");

		String lastActionLabel = buildLastActionLabel(store);

		boolean needsAttention = "점검 필요".equals(result.statusLabel());

		return new RegionStoreRowDto(
				store.getId(), store.getStoreName(),
				store.getOwnerId() != null ? ownerNicknames.getOrDefault(store.getOwnerId(), "-") : "-",
				registered, sold, expired,
				result.percent(), result.tileClass(), result.statusLabel(),
				pickupTimeLabel, lastRegisteredAtLabel, lastActionLabel, needsAttention);
	}

	/** 마지막 쿠폰 발행일 또는 제안 발송일 중 더 최근 것 — "쿠폰 9/28" / "제안 9/28" / "-". */
	private String buildLastActionLabel(StoreEntity store) {
		LocalDateTime lastCouponAt = couponStoreRepository.findByStoreId(store.getId()).stream()
				.map(CouponStoreEntity::getCampaignId)
				.distinct()
				.map(id -> couponCampaignRepository.findById(id).orElse(null))
				.filter(Objects::nonNull)
				.map(CouponCampaignEntity::getCreatedAt)
				.max(Comparator.naturalOrder())
				.orElse(null);

		LocalDateTime lastSuggestionAt = store.getOwnerId() != null
				? notificationRepository.findTopByUserIdAndTypeOrderByCreatedAtDesc(
						store.getOwnerId(), NotificationEntity.TYPE_OWNER_SELL_SUGGESTION)
						.map(NotificationEntity::getCreatedAt).orElse(null)
				: null;

		if (lastCouponAt == null && lastSuggestionAt == null) {
			return "-";
		}
		if (lastSuggestionAt == null || (lastCouponAt != null && lastCouponAt.isAfter(lastSuggestionAt))) {
			return "쿠폰 " + lastCouponAt.toLocalDate().format(MD_FORMAT);
		}
		return "제안 " + lastSuggestionAt.toLocalDate().format(MD_FORMAT);
	}

	/**
	 * 점주에게 운영 제안을 보낸다 — 2026-10-01 신규. 할인율 상향 제안은 하지 않는다는 원칙에 따라
	 * 노출·시간·구성 개선만 다룬다. 자동 삽입값(마감 N분 전/픽업 시간/사진 없는 상품 수)은 데이터가
	 * 없으면 그 템플릿 자체를 건너뛴다(숫자를 지어내지 않음) — 몇 곳이 실제로 발송됐는지 돌려준다.
	 */
	@Transactional
	public int sendOwnerSuggestions(List<Long> storeIds, String templateKey, String customMessage) {
		if (storeIds == null || storeIds.isEmpty()) {
			return 0;
		}
		int sent = 0;
		for (StoreEntity store : storeRepository.findAllById(storeIds)) {
			if (store.getOwnerId() == null) {
				continue;
			}
			String message = buildSuggestionMessage(store, templateKey, customMessage);
			if (message == null) {
				continue;
			}
			notificationService.createNotification(store.getOwnerId(), NotificationEntity.TYPE_OWNER_SELL_SUGGESTION,
					message, "/store/dashboard",
					"owner_suggestion:" + store.getId() + ":" + System.currentTimeMillis());
			sent++;
		}
		return sent;
	}

	/** null을 돌려주면 "이 매장엔 보낼 데이터가 없다"는 뜻 — 호출부가 그 매장을 건너뛴다. */
	private String buildSuggestionMessage(StoreEntity store, String templateKey, String customMessage) {
		return switch (templateKey) {
			case TEMPLATE_CLOSING_TIME_EARLIER -> buildClosingTimeMessage(store);
			case TEMPLATE_PICKUP_TIME_LONGER -> buildPickupTimeMessage(store);
			case TEMPLATE_PHOTO_DESCRIPTION -> buildPhotoMessage(store);
			case TEMPLATE_BUNDLE_SUGGESTION ->
					"낱개 상품이 많아요. 마감 시간대엔 2~3개를 묶은 구성이 손님 눈에 더 잘 띄어요.";
			case TEMPLATE_REGISTER_ENCOURAGE ->
					"최근 7일 동안 등록한 상품이 없어요. 마감 전 남은 상품을 올려서 손님에게 보여주세요.";
			case TEMPLATE_CUSTOM -> customMessage != null && !customMessage.isBlank() ? customMessage.trim() : null;
			default -> null;
		};
	}

	/** 최근 7일 픽업 완료 건의 "마감 N분 전" 평균 — 데이터(픽업 완료 이력 또는 영업시간)가 없으면 null. */
	private String buildClosingTimeMessage(StoreEntity store) {
		LocalTime closingTime;
		try {
			closingTime = StoreHoursUtil.parseClosingTime(store.getOperatingHours());
		} catch (IllegalArgumentException e) {
			return null;
		}
		LocalDateTime windowStart = LocalDate.now().minusDays(6).atStartOfDay();
		List<ReservationEntity> picked = reservationRepository
				.findByStoreIdAndStatusOrderByPickedAtDesc(store.getId(), "picked").stream()
				.filter(r -> r.getPickedAt() != null && !r.getPickedAt().isBefore(windowStart))
				.toList();
		if (picked.isEmpty()) {
			return null;
		}
		double avgMinutesBeforeClose = picked.stream()
				.mapToLong(r -> Duration.between(r.getPickedAt().toLocalTime(), closingTime).toMinutes())
				.filter(m -> m >= 0)
				.average()
				.orElse(Double.NaN);
		if (Double.isNaN(avgMinutesBeforeClose)) {
			return null;
		}
		int minutes = (int) Math.round(avgMinutesBeforeClose);
		return "최근 7일 판매는 주로 마감 " + minutes + "분 전에 이뤄졌어요. 할인 시작을 앞당기면 더 많은 손님에게 보여요.";
	}

	private String buildPickupTimeMessage(StoreEntity store) {
		if (store.getLastPickupTime() == null) {
			return null;
		}
		String label = store.getLastPickupTime().format(DateTimeFormatter.ofPattern("HH:mm"));
		return "지금 픽업 마감 시간이 " + label + "이에요. 조금 더 늘리면 더 많은 손님이 찾아올 수 있어요.";
	}

	private String buildPhotoMessage(StoreEntity store) {
		long noPhotoCount = productRepository.findByStoreId(store.getId()).stream()
				.filter(p -> !"draft".equals(p.getStatus()) && !"skipped".equals(p.getStatus()))
				.filter(p -> p.getImageUrl() == null || p.getImageUrl().isBlank())
				.count();
		if (noPhotoCount == 0) {
			return null;
		}
		return "사진이 없는 상품이 " + noPhotoCount + "개 있어요. 사진과 설명을 추가하면 손님이 더 쉽게 골라요.";
	}
}

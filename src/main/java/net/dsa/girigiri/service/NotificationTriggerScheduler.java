package net.dsa.girigiri.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dsa.girigiri.domain.entity.LikeEntity;
import net.dsa.girigiri.domain.entity.NotificationEntity;
import net.dsa.girigiri.domain.entity.ProductEntity;
import net.dsa.girigiri.domain.entity.ReservationEntity;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.repository.LikeRepository;
import net.dsa.girigiri.repository.ProductRepository;
import net.dsa.girigiri.repository.ReservationRepository;
import net.dsa.girigiri.repository.StoreRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 강노은: 알림이 실제로 "생기는" 트리거. 상품 등록(김태훈 담당)이나 예약 상태 변경(송채현 담당)
 * 코드에 직접 훅을 심는 대신, 이미 있는 Repository를 주기적으로 "읽기만" 해서 변화를 감지한다 —
 * 두 분 파일은 한 줄도 안 건드린다(팀 논의 후 이 방식으로 결정).
 *
 * 중복 알림 방지는 NotificationEntity.sourceKey로 한다 — 매 스캔마다 대상 전체를 다시 훑어도
 * "이미 알림 만든 사건"은 걸러지기 때문에, 상태를 따로 기억해둘 필요 없이 항상 안전(idempotent)하다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationTriggerScheduler {

	private static final long SCAN_INTERVAL_MS = 60 * 1000L; // 1분마다
	private static final String STATUS_ACTIVE = "active";
	private static final String RESERVATION_URL = "/reservation/my";
	private static final long PICKUP_SOON_MINUTES = 30;

	private final NotificationService notificationService;
	private final ProductRepository productRepository;
	private final StoreRepository storeRepository;
	private final LikeRepository likeRepository;
	private final ReservationRepository reservationRepository;

	// 이 시각 이후 등록된 상품만 "찜한 가게 마감세일 시작" 알림 대상으로 본다. 이게 없으면 서버를
	// 켤 때마다 그 전부터 있던 active 상품 전부가 한꺼번에 "시작했어요" 알림으로 쏟아진다.
	private final LocalDateTime startedAt = LocalDateTime.now();

	@Scheduled(fixedRate = SCAN_INTERVAL_MS)
	public void scan() {
		scanLikedStoreOpen();
		scanReservationConfirmed();
		scanReservationPickupSoon();
		scanReservationNoShow();
	}

	private void scanLikedStoreOpen() {
		List<ProductEntity> freshlyActive = productRepository.findAll().stream()
				.filter(p -> STATUS_ACTIVE.equals(p.getStatus()))
				.filter(p -> p.getRegisteredAt() != null && p.getRegisteredAt().isAfter(startedAt))
				.toList();
		if (freshlyActive.isEmpty()) {
			return;
		}

		Map<Long, StoreEntity> storesById = storeRepository.findAll().stream()
				.collect(Collectors.toMap(StoreEntity::getId, s -> s));
		Map<Long, List<Long>> likerIdsByStoreId = likeRepository.findAll().stream()
				.collect(Collectors.groupingBy(LikeEntity::getStoreId,
						Collectors.mapping(LikeEntity::getUserId, Collectors.toList())));

		for (ProductEntity product : freshlyActive) {
			List<Long> likerIds = likerIdsByStoreId.get(product.getStoreId());
			StoreEntity store = storesById.get(product.getStoreId());
			if (likerIds == null || likerIds.isEmpty() || store == null) {
				continue;
			}
			String message = withSubjectParticle("찜한 " + store.getStoreName()) + " 마감세일을 시작했어요!";
			String linkUrl = "/user/stores/" + store.getId();
			for (Long likerId : likerIds) {
				notificationService.createNotification(likerId, NotificationEntity.TYPE_LIKE_STORE_OPEN,
						message, linkUrl, "like_open:" + likerId + ":" + product.getId());
			}
		}
	}

	private void scanReservationConfirmed() {
		for (ReservationEntity r : reservationRepository.findByStatusIn(List.of("confirmed"))) {
			notificationService.createNotification(r.getUserId(), NotificationEntity.TYPE_RESERVATION_CONFIRMED,
					"\"" + productLabel(r) + "\" 예약이 확정됐어요. 픽업을 기다려주세요.",
					RESERVATION_URL, "reservation_confirmed:" + r.getId());
		}
	}

	private void scanReservationPickupSoon() {
		LocalDateTime now = LocalDateTime.now();
		LocalDateTime soon = now.plusMinutes(PICKUP_SOON_MINUTES);
		// ready = 매장이 수락해서 이제 손님이 와서 픽업하면 되는 상태(ReservationEntity.status 주석 참고).
		for (ReservationEntity r : reservationRepository.findByStatusIn(List.of("ready"))) {
			if (r.getPickupTime() == null || r.getPickupTime().isBefore(now) || r.getPickupTime().isAfter(soon)) {
				continue;
			}
			notificationService.createNotification(r.getUserId(), NotificationEntity.TYPE_RESERVATION_PICKUP_SOON,
					"\"" + productLabel(r) + "\" 픽업 시간이 곧 다가와요.",
					RESERVATION_URL, "pickup_soon:" + r.getId());
		}
	}

	private void scanReservationNoShow() {
		for (ReservationEntity r : reservationRepository.findByStatusIn(List.of("noshowed"))) {
			notificationService.createNotification(r.getUserId(), NotificationEntity.TYPE_RESERVATION_NOSHOW,
					"\"" + productLabel(r) + "\" 예약이 픽업 시간 경과로 노쇼 처리됐어요.",
					RESERVATION_URL, "reservation_noshow:" + r.getId());
		}
	}

	private String productLabel(ReservationEntity r) {
		return r.getProductName() == null || r.getProductName().isBlank() ? "예약 상품" : r.getProductName();
	}

	/** "찜한 다이스키 베이커리" + 이/가 → 받침 있으면 "이", 없으면 "가". 한글이 아니면 그냥 "가"를 붙인다. */
	private String withSubjectParticle(String word) {
		if (word == null || word.isEmpty()) {
			return word;
		}
		char last = word.charAt(word.length() - 1);
		if (last < 0xAC00 || last > 0xD7A3) {
			return word + "가";
		}
		boolean hasBatchim = (last - 0xAC00) % 28 != 0;
		return word + (hasBatchim ? "이" : "가");
	}
}

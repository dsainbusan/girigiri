package net.dsa.girigiri.service;

import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.NotificationRowDto;
import net.dsa.girigiri.domain.entity.NotificationEntity;
import net.dsa.girigiri.domain.entity.NotificationSettingEntity;
import net.dsa.girigiri.repository.NotificationRepository;
import net.dsa.girigiri.repository.NotificationSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

/**
 * 강노은: 인앱 알림함 + 알림 설정. 알림이 실제로 "생기는" 트리거(찜한 가게 마감세일 시작,
 * 예약 상태 변경)는 아직 안 붙었다 — createNotification()은 트리거가 정해지면 호출부만
 * 추가하면 되도록 미리 만들어 둔 것. 지금은 알림함 UI/읽음 처리/설정만 완결적으로 동작한다.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

	private final NotificationRepository notificationRepository;
	private final NotificationSettingRepository notificationSettingRepository;

	public List<NotificationRowDto> getNotifications(Long userId) {
		return notificationRepository.findAll().stream()
				.filter(n -> userId.equals(n.getUserId()))
				.sorted(Comparator.comparing(NotificationEntity::getCreatedAt,
						Comparator.nullsLast(Comparator.reverseOrder())))
				.map(n -> new NotificationRowDto(
						n.getId(),
						icon(n.getType()),
						n.getMessage(),
						n.getLinkUrl(),
						n.isRead(),
						relativeLabel(n.getCreatedAt())
				))
				.toList();
	}

	public int getUnreadCount(Long userId) {
		if (userId == null) {
			return 0;
		}
		return (int) notificationRepository.findAll().stream()
				.filter(n -> userId.equals(n.getUserId()) && !n.isRead())
				.count();
	}

	/** 본인 알림이 아니면 조용히 무시(예외 없이 리턴) — 남의 알림 id를 끼워넣어도 아무 일도 안 일어나게. */
	@Transactional
	public String markRead(Long userId, Long notificationId) {
		NotificationEntity notification = notificationRepository.findById(notificationId).orElse(null);
		if (notification == null || !userId.equals(notification.getUserId())) {
			return null;
		}
		notification.setRead(true);
		return notification.getLinkUrl();
	}

	@Transactional
	public void markAllRead(Long userId) {
		List<NotificationEntity> unread = notificationRepository.findAll().stream()
				.filter(n -> userId.equals(n.getUserId()) && !n.isRead())
				.toList();
		unread.forEach(n -> n.setRead(true));
	}

	/** 트리거가 정해지면 이 메서드를 호출하는 쪽만 새로 생기면 된다. */
	@Transactional
	public void createNotification(Long userId, String type, String message, String linkUrl) {
		NotificationSettingEntity settings = getOrCreateSettings(userId);
		if (!settings.isPushEnabled()) {
			return;
		}
		if (NotificationEntity.TYPE_LIKE_STORE_OPEN.equals(type) && !settings.isLikeAlertEnabled()) {
			return;
		}
		notificationRepository.save(NotificationEntity.builder()
				.userId(userId)
				.type(type)
				.message(message)
				.linkUrl(linkUrl)
				.build());
	}

	public NotificationSettingEntity getOrCreateSettings(Long userId) {
		return notificationSettingRepository.findByUserId(userId)
				.orElseGet(() -> notificationSettingRepository.save(
						NotificationSettingEntity.builder().userId(userId).build()));
	}

	@Transactional
	public void updateSettings(Long userId, boolean pushEnabled, boolean likeAlertEnabled) {
		NotificationSettingEntity settings = getOrCreateSettings(userId);
		settings.setPushEnabled(pushEnabled);
		settings.setLikeAlertEnabled(likeAlertEnabled);
	}

	private String icon(String type) {
		if (type == null) {
			return "🔔";
		}
		return switch (type) {
			case NotificationEntity.TYPE_LIKE_STORE_OPEN -> "🏪";
			case NotificationEntity.TYPE_RESERVATION_CONFIRMED -> "✅";
			case NotificationEntity.TYPE_RESERVATION_PICKUP_SOON -> "⏰";
			case NotificationEntity.TYPE_RESERVATION_NOSHOW -> "⚠️";
			default -> "🔔";
		};
	}

	private String relativeLabel(LocalDateTime createdAt) {
		if (createdAt == null) {
			return "";
		}
		long days = ChronoUnit.DAYS.between(createdAt.toLocalDate(), LocalDate.now());
		if (days <= 0) {
			return "오늘";
		}
		if (days == 1) {
			return "어제";
		}
		return days + "일 전";
	}
}

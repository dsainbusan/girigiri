package net.dsa.girigiri.service;

import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.AdminNotificationRowDto;
import net.dsa.girigiri.domain.dto.NotificationRowDto;
import net.dsa.girigiri.domain.entity.NotificationEntity;
import net.dsa.girigiri.domain.entity.NotificationSettingEntity;
import net.dsa.girigiri.repository.NotificationRepository;
import net.dsa.girigiri.repository.NotificationSettingRepository;
import net.dsa.girigiri.util.SseEmitterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 강노은: 인앱 알림함 + 알림 설정 + 실시간 전송(SSE). 알림이 실제로 "생기는" 트리거는
 * NotificationTriggerScheduler가 담당한다 — 다른 사람 코드(상품 등록·예약 상태 변경)에 훅을
 * 심는 대신, 이미 있는 Repository를 주기적으로 읽기만 해서 변화를 감지하는 방식으로 팀 논의 후 결정했다.
 * 여기 createNotification()은 그 스캐너가 호출하는 진입점 — 설정(push/찜알림 on-off) 확인, 저장,
 * 열려있는 SSE 연결로 실시간 배지 갱신까지 한 번에 처리한다.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

	private final NotificationRepository notificationRepository;
	private final NotificationSettingRepository notificationSettingRepository;
	private final SseEmitterRegistry sseEmitterRegistry;

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

	/**
	 * 추가됨 — 왜: 슈퍼어드민 알림 패널용. getNotifications()와 모양은 같지만, 유저용 상대시간 라벨
	 * ("오늘"/"어제") 대신 패널의 날짜별 그룹 헤더에 쓸 절대 날짜 라벨과, 탭 필터(회원/게시판/예약)에
	 * 쓸 카테고리를 함께 내려준다.
	 */
	public List<AdminNotificationRowDto> getAdminNotifications(Long adminId) {
		return notificationRepository.findAll().stream()
				.filter(n -> adminId.equals(n.getUserId()))
				.sorted(Comparator.comparing(NotificationEntity::getCreatedAt,
						Comparator.nullsLast(Comparator.reverseOrder())))
				.map(n -> new AdminNotificationRowDto(
						n.getId(),
						icon(n.getType()),
						n.getMessage(),
						n.getLinkUrl(),
						n.isRead(),
						adminCategory(n.getType()),
						dateLabel(n.getCreatedAt()),
						timeLabel(n.getCreatedAt())
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

	/** 로그인한 사용자용 SSE 구독. 서버가 이 연결을 붙잡아뒀다가 새 알림이 생기면 밀어준다. */
	public SseEmitter subscribe(Long userId) {
		return sseEmitterRegistry.register(userId);
	}

	/** 본인 알림이 아니면 조용히 무시(예외 없이 리턴) — 남의 알림 id를 끼워넣어도 아무 일도 안 일어나게. */
	@Transactional
	public String markRead(Long userId, Long notificationId) {
		NotificationEntity notification = notificationRepository.findById(notificationId).orElse(null);
		if (notification == null || !userId.equals(notification.getUserId())) {
			return null;
		}
		notification.setRead(true);
		sseEmitterRegistry.pushUnreadCount(userId, getUnreadCount(userId));
		return notification.getLinkUrl();
	}

	@Transactional
	public void markAllRead(Long userId) {
		List<NotificationEntity> unread = notificationRepository.findAll().stream()
				.filter(n -> userId.equals(n.getUserId()) && !n.isRead())
				.toList();
		unread.forEach(n -> n.setRead(true));
		if (!unread.isEmpty()) {
			sseEmitterRegistry.pushUnreadCount(userId, 0);
		}
	}

	/**
	 * NotificationTriggerScheduler가 호출하는 진입점. sourceKey는 같은 사건에 대한 중복 생성을
	 * 막는 키 — 이미 있으면 조용히 건너뛴다(NotificationEntity.sourceKey 주석 참고).
	 */
	@Transactional
	public void createNotification(Long userId, String type, String message, String linkUrl, String sourceKey) {
		if (sourceKey != null && notificationRepository.existsBySourceKey(sourceKey)) {
			return;
		}
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
				.sourceKey(sourceKey)
				.build());
		sseEmitterRegistry.pushUnreadCount(userId, getUnreadCount(userId));
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
			case NotificationEntity.TYPE_INQUIRY_COMMENT -> "💬";
			case NotificationEntity.TYPE_ADMIN_NEW_MEMBER -> "👤";
			case NotificationEntity.TYPE_ADMIN_NEW_INQUIRY -> "💬";
			case NotificationEntity.TYPE_ADMIN_NEW_RESERVATION -> "📅";
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

	/** 슈퍼어드민 알림 패널의 탭(전체/회원/게시판/예약) 필터용 — 3개 관리자 타입만 분류, 그 외는 필터 대상 밖. */
	private String adminCategory(String type) {
		if (NotificationEntity.TYPE_ADMIN_NEW_MEMBER.equals(type)) {
			return "MEMBER";
		}
		if (NotificationEntity.TYPE_ADMIN_NEW_INQUIRY.equals(type)) {
			return "BOARD";
		}
		if (NotificationEntity.TYPE_ADMIN_NEW_RESERVATION.equals(type)) {
			return "RESERVATION";
		}
		return "";
	}

	private static final DateTimeFormatter ADMIN_DATE_LABEL =
			DateTimeFormatter.ofPattern("MM월 dd일 (E)", Locale.KOREAN);
	private static final DateTimeFormatter ADMIN_TIME_LABEL = DateTimeFormatter.ofPattern("HH:mm");

	private String dateLabel(LocalDateTime createdAt) {
		return createdAt == null ? "" : createdAt.format(ADMIN_DATE_LABEL);
	}

	private String timeLabel(LocalDateTime createdAt) {
		return createdAt == null ? "" : createdAt.format(ADMIN_TIME_LABEL);
	}
}

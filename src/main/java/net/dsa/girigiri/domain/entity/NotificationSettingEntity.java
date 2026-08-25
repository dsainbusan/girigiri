package net.dsa.girigiri.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 강노은: 사용자 1명당 1행. UserEntity(문창호 담당)에 컬럼을 얹지 않고 별도 엔티티로 분리했다 —
 * 다른 사람 파일을 안 건드리고 알림 설정을 완결적으로 둘 수 있어서다. 최초 조회 시 없으면
 * NotificationService#getOrCreateSettings가 기본값(둘 다 true)으로 만들어준다.
 */
@Entity
@Table(name = "notification_setting")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class NotificationSettingEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false, unique = true)
	private Long userId;

	// PUSH 알림 전체 스위치.
	@Builder.Default
	@Column(name = "push_enabled", nullable = false)
	private boolean pushEnabled = true;

	// 찜한 가게 마감세일 시작 알림 스위치.
	@Builder.Default
	@Column(name = "like_alert_enabled", nullable = false)
	private boolean likeAlertEnabled = true;

	@LastModifiedDate
	@Column(name = "updated_at")
	private LocalDateTime updatedAt;
}

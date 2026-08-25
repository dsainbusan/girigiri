package net.dsa.girigiri.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 강노은: 인앱 알림함 1건. 지금은 이 엔티티/알림함 UI/읽음 처리까지만 만든다 — "언제 자동으로
 * 생성되는지"(찜한 가게 마감세일 시작, 예약 상태 변경 등 트리거)는 아직 안 붙였다. 트리거를 실시간
 * WebSocket/SSE로 갈지 폴링으로 갈지, 다른 사람 코드(예약·상품등록)에 훅을 걸지 여부가 아직
 * 정해지지 않아서다 — 정해지면 NotificationService에 생성 메서드만 추가로 붙이면 된다.
 */
@Entity
@Table(name = "notification")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class NotificationEntity {

	// 트리거 붙을 때 쓸 타입 상수. 지금은 목록 아이콘 매핑(NotificationService)에만 쓰인다.
	public static final String TYPE_LIKE_STORE_OPEN = "LIKE_STORE_OPEN";           // 찜한 가게 마감세일 시작
	public static final String TYPE_RESERVATION_CONFIRMED = "RESERVATION_CONFIRMED"; // 예약 확정
	public static final String TYPE_RESERVATION_PICKUP_SOON = "RESERVATION_PICKUP_SOON"; // 픽업 임박
	public static final String TYPE_RESERVATION_NOSHOW = "RESERVATION_NOSHOW";     // 노쇼 처리

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "type", nullable = false, length = 30)
	private String type;

	@Column(name = "message", nullable = false, length = 200)
	private String message;

	// 알림 클릭 시 이동할 곳(가게 상세, 예약 상세 등). 없으면 알림함에 그대로 머문다.
	@Column(name = "link_url", length = 200)
	private String linkUrl;

	// 추가됨 (강노은) — 왜: 알림 트리거를 "다른 사람 코드에 훅 삽입" 대신 "주기적으로 Repository를
	// 읽어서 스캔"하는 방식으로 만들면서, 같은 사건(예: 이 예약의 노쇼 처리)에 대해 스캔할 때마다
	// 알림이 중복 생성되지 않게 막을 방법이 필요했다. "사건 종류:대상id" 형태의 고유 키를 저장해두고,
	// 새로 만들기 전에 이미 있는지만 확인하면 돼서(existsBySourceKey) 스캔 주기가 얼마나 자주 돌든
	// 안전(idempotent)하다. 예: "like_open:7:42", "reservation_noshow:15".
	@Column(name = "source_key", length = 100)
	private String sourceKey;

	@Builder.Default
	@Column(name = "is_read", nullable = false)
	private boolean read = false;

	@CreatedDate
	@Column(name = "created_at", updatable = false)
	private LocalDateTime createdAt;
}

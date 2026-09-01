package net.dsa.girigiri.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 슈퍼어드민이 작성하는 공지사항 (WBS 7.0, 송보미 담당).
 *
 * 추가됨 — 왜: "게시 기간을 정하고 싶다"는 요청으로 publishStartAt/publishEndAt(둘 다 nullable)을
 * 붙였다. published는 그대로 수동 on/off 스위치("게시글 내리기"가 이걸 false로 바꾸는 것)로 남기고,
 * 기간은 published=true일 때만 추가로 적용되는 조건이다 — 즉 실제로 화면에 노출되려면
 * published=true이면서 오늘이 [publishStartAt, publishEndAt] 안에 있어야 한다(둘 다 null이면 무제한).
 * 이 판정 로직을 화면(목록/상세)마다 따로 구현하면 어긋날 수 있어 getDisplayStatus() 하나로 모은다.
 */
@Entity
@Table(name = "notice")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NoticeEntity extends BaseTimeEntity {

	public static final String STATUS_PUBLISHED = "PUBLISHED";   // 지금 실제로 게시중
	public static final String STATUS_SCHEDULED = "SCHEDULED";   // published=true인데 시작일이 아직 안 됨
	public static final String STATUS_EXPIRED = "EXPIRED";       // published=true인데 종료일이 지남
	public static final String STATUS_HIDDEN = "HIDDEN";         // published=false(직접 내림)

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "title", nullable = false, length = 100)
	private String title;

	@Column(name = "content", nullable = false, length = 2000)
	private String content;

	@Column(name = "published", nullable = false)
	private boolean published;

	@Column(name = "publish_start_at")
	private LocalDate publishStartAt;   // null이면 등록/게시 즉시부터

	@Column(name = "publish_end_at")
	private LocalDate publishEndAt;     // null이면 무기한

	public String getDisplayStatus() {
		if (!published) {
			return STATUS_HIDDEN;
		}
		LocalDate today = LocalDate.now();
		if (publishStartAt != null && today.isBefore(publishStartAt)) {
			return STATUS_SCHEDULED;
		}
		if (publishEndAt != null && today.isAfter(publishEndAt)) {
			return STATUS_EXPIRED;
		}
		return STATUS_PUBLISHED;
	}
}

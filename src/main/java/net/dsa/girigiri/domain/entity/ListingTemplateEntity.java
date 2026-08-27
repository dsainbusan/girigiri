package net.dsa.girigiri.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * "오늘의 구제 자동 등록" 템플릿 — 2026-08-26 신규 (WBS/CLAUDE.md엔 없는 기능, 조장 공유 필요).
 *
 * 사장님이 한 번 등록해두면, ListingDraftScheduler가 매일 정해진 요일·시각에 이 템플릿으로
 * "구제 초안"(ProductEntity status='draft')을 만들고 알림을 보낸다. 사장님은 [바로 올리기]
 * 한 번만 누르면 실제 판매(active)로 전환된다.
 *
 * ⚠️ 새 엔티티 — 공통 DB 오너(송보미)에게 스키마 공유/리뷰 필요.
 */
@Entity
@Table(name = "listing_template")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class ListingTemplateEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "store_id", nullable = false)
	private Long storeId;

	@Column(name = "name", nullable = false, length = 100)
	private String name;

	@Column(name = "original_price", nullable = false)
	private Integer originalPrice;

	@Column(name = "image_url", length = 255)
	private String imageUrl;

	@Column(name = "description", length = 500)
	private String description;

	@Column(name = "default_quantity", nullable = false)
	private Integer defaultQuantity;

	// ISO 요일 숫자 CSV. 1=월 … 7=일. 예: "1,2,3,4,5" = 평일만.
	@Column(name = "weekdays", nullable = false, length = 20)
	private String weekdays;

	// 매일 이 시각에 초안 생성 + 알림. (마감 시각보다 1~2시간 앞으로 잡는 걸 권장 — 화면에서 안내)
	@Column(name = "prompt_time", nullable = false)
	private LocalTime promptTime;

	@Builder.Default
	@Column(name = "active", nullable = false)
	private boolean active = true;

	@CreatedDate
	@Column(name = "created_at", updatable = false)
	private LocalDateTime createdAt;

	@LastModifiedDate
	@Column(name = "updated_at")
	private LocalDateTime updatedAt;
}

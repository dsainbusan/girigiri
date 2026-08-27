package net.dsa.girigiri.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "product")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class ProductEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "store_id", nullable = false)
	private Long storeId;

	@Column(name = "name", nullable = false, length = 100)
	private String name;

	@Column(name = "original_price", nullable = false)
	private Integer originalPrice;

	@Column(name = "discounted_price", nullable = false)
	private Integer discountedPrice;

	@Column(name = "quantity", nullable = false)
	private Integer quantity;

	@Column(name = "remaining_quantity", nullable = false)
	private Integer remainingQuantity;

	@Column(name = "image_url", length = 255)
	private String imageUrl;

	@Column(name = "description", length = 500)
	private String description;

	@Column(name = "status", nullable = false, length = 20)
	private String status;   // draft / active / sold / expired / skipped
	                         // draft = "오늘의 구제" 자동 초안 (사장님이 [바로 올리기] 누르면 active).
	                         // skipped = 초안을 "오늘 안 함" 처리 (지우면 스케줄러가 재생성하므로 행은 남긴다).
	                         // draft/skipped 는 홈/검색/대시보드 집계에서 제외된다.

	// "오늘의 구제 자동 등록"이 만든 상품이면 그 출처 id. 수동 등록이면 둘 다 null.
	// 하루에 같은 출처로 초안이 중복 생성되는 걸 막는 dedup 용도.
	@Column(name = "template_id")
	private Long templateId;      // ListingDraftScheduler — 템플릿 방식(POS 없는 매장)

	@Column(name = "menu_item_id")
	private Long menuItemId;      // POS 재고 스냅샷 방식(B안) — 이 초안이 어느 MenuItem에서 나왔는지

	@CreatedDate
	@Column(name = "registered_at", updatable = false)
	private LocalDateTime registeredAt;

	// 상품 정보 수정뿐 아니라 예약/취소로 remainingQuantity가 바뀔 때도 갱신된다 — "사장님이 마지막으로
	// 수정한 시각"이 아니라 목록 정렬·장애 추적용 범용 컬럼으로만 쓸 것.
	@LastModifiedDate
	@Column(name = "updated_at")
	private LocalDateTime updatedAt;
}

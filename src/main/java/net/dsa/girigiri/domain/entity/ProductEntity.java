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
	private String status;   // active / sold / expired

	@CreatedDate
	@Column(name = "registered_at", updatable = false)
	private LocalDateTime registeredAt;

	// 상품 정보 수정뿐 아니라 예약/취소로 remainingQuantity가 바뀔 때도 갱신된다 — "사장님이 마지막으로
	// 수정한 시각"이 아니라 목록 정렬·장애 추적용 범용 컬럼으로만 쓸 것.
	@LastModifiedDate
	@Column(name = "updated_at")
	private LocalDateTime updatedAt;
}

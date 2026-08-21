package net.dsa.girigiri.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
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
}

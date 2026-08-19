package net.dsa.girigiri.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "reservation")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class ReservationEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "product_id", nullable = false)
	private Long productId;

	@Column(name = "store_id", nullable = false)
	private Long storeId;

	@Column(name = "reserved_quantity", nullable = false)
	private Integer reservedQuantity;

	@Column(name = "total_price", nullable = false)
	private Integer totalPrice;

	@Column(name = "pickup_time")
	private LocalDateTime pickupTime;

	@Column(name = "pickup_code", length = 30)
	private String pickupCode;   // QR/픽업 확인 코드

	@Column(name = "status", nullable = false, length = 20)
	private String status;   // pending / confirmed / picked / cancelled / noshowed

	@CreatedDate
	@Column(name = "reserved_at", updatable = false)
	private LocalDateTime reservedAt;

	@Column(name = "picked_at")
	private LocalDateTime pickedAt;
}

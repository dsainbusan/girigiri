package net.dsa.girigiri.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "store")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class StoreEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "login_id", length = 50, unique = true)
	private String loginId;

	@Column(name = "password", length = 100)
	private String password;

	@Column(name = "store_name", nullable = false, length = 50)
	private String storeName;

	@Column(name = "category", length = 30)
	private String category;

	@Column(name = "address", length = 200)
	private String address;

	@Column(name = "latitude")
	private Double latitude;

	@Column(name = "longitude")
	private Double longitude;

	@Column(name = "operating_hours", length = 100)
	private String operatingHours;

	@Column(name = "role", nullable = false, length = 20)
	private String role;   // = ADMIN

	// TODO(송보미): CLAUDE.md 스펙상 Store가 자체 loginId/password/role=ADMIN을 갖는 것과,
	// 세션 설계({ userId, role, viewMode, storeId })가 암시하는 "User가 storeId로 Store를 소유"하는
	// 모델이 서로 어긋난다. ERD 확정 시 둘 중 하나로 정리 필요.
	@Column(name = "owner_id")
	private Long ownerId;

	@CreatedDate
	@Column(name = "created_at", updatable = false)
	private LocalDateTime createdAt;

	@LastModifiedDate
	@Column(name = "updated_at")
	private LocalDateTime updatedAt;
}

package net.dsa.girigiri.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")   // "user"는 MySQL 예약어라 회피
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class UserEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "login_id", length = 50, unique = true)
	private String loginId;

	@Column(name = "password", length = 100)
	private String password;

	@Column(name = "oauth_provider", length = 20)
	private String oauthProvider;   // kakao / naver / null(일반가입)

	@Column(name = "oauth_id", length = 100)
	private String oauthId;

	@Column(name = "role", nullable = false, length = 20)
	private String role;   // USER / ADMIN

	@Column(name = "nickname", length = 30)
	private String nickname;

	@Column(name = "latitude")
	private Double latitude;

	@Column(name = "longitude")
	private Double longitude;

	@CreatedDate
	@Column(name = "created_at", updatable = false)
	private LocalDateTime createdAt;

	@LastModifiedDate
	@Column(name = "updated_at")
	private LocalDateTime updatedAt;
}

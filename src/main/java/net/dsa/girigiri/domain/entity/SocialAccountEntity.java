package net.dsa.girigiri.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 1:N 멀티 소셜 계정 연동(Account Linking) 엔티티.
 * 한 명의 회원(UserEntity)이 카카오, 네이버, 구글, 라인, 이메일 등 여러 개의 소셜 로그인 수단을 가질 수 있다.
 */
@Entity
@Table(name = "user_social_accounts", uniqueConstraints = {
		@UniqueConstraint(name = "uk_social_provider_id", columnNames = {"provider", "provider_id"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class SocialAccountEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private UserEntity user;

	@Column(name = "provider", length = 20, nullable = false)
	private String provider; // google, kakao, line, naver, email

	@Column(name = "provider_id", length = 100, nullable = false)
	private String providerId;

	// 추가됨 (2026-09-23, 코드 감사) — 왜: 마이페이지 "연동된 로그인 계정" 목록이 각 행을 구분할 방법이
	// 없어서 UserEntity.email(계정 전체에 하나뿐인 대표 이메일)을 그대로 보여줬다. 그런데 계정 하나에
	// 같은 provider(예: 구글)로 서로 다른 실제 계정 두 개가 연동될 수 있어서(1:N 연동의 정상 시나리오 —
	// AuthService.linkSocialAccountAfterReauth 참고), 그 경우 두 행이 똑같은 이메일로 보여서 "같은 계정이
	// 중복으로 뜬다"는 오해를 낳았다. 이 컬럼에 그 소셜 계정 고유의 이메일을 저장해두면 행마다 구분이 된다.
	// 카카오/라인처럼 이메일 동의항목이 없는 provider는 null로 남고, 화면은 기존처럼 일반 문구로 대체한다.
	@Column(name = "connected_email", length = 100)
	private String connectedEmail;

	@CreatedDate
	@Column(name = "connected_at", nullable = false, updatable = false)
	private LocalDateTime connectedAt;
}

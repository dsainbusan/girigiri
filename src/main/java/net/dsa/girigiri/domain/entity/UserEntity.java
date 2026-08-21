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

	// 추가됨 (2026-08-21) — 왜: role 문자열 리터럴을 코드 곳곳에 흩어놓지 않고 한 군데서 관리하기 위해.
	// role 모델이 USER/ADMIN 2종에서 USER(일반)/OWNER(점주)/ADMIN(운영자) 3종으로 바뀌면서 신설.
	public static final String ROLE_USER = "USER";
	public static final String ROLE_OWNER = "OWNER";
	public static final String ROLE_ADMIN = "ADMIN";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// 변경됨 (2026-08-21) — 왜: dev 브랜치에는 OAuth2 로그인이 없던 시절의 login_id/password가
	// 남아 있었는데, 자체 회원가입이 없는(CLAUDE.md 3.1) 프로젝트라 항상 비어있게 되는 필드라 제거.
	// oauthProvider/oauthId도 자체가입 경로가 없으므로 항상 값이 채워져 nullable = false로 필수화.
	@Column(name = "oauth_provider", length = 20, nullable = false)
	private String oauthProvider;   // google / kakao / line

	@Column(name = "oauth_id", length = 100, nullable = false)
	private String oauthId;

	// 추가됨 (2026-08-21) — 왜: 소셜 계정 없이도 가입할 수 있도록 이메일+비밀번호 로그인을 추가하면서
	// 신설. 이메일 계정은 oauthProvider="email", oauthId=이메일 값으로 저장해 기존 조회 로직
	// (findByOauthProviderAndOauthId)을 그대로 재사용한다. 소셜 계정은 계속 null로 남는다.
	@Column(name = "password", length = 100)
	private String password;

	@Column(name = "role", nullable = false, length = 20)
	private String role;   // USER / OWNER / ADMIN

	@Column(name = "nickname", length = 30)
	private String nickname;

	// 추가됨 (2026-08-21) — 왜: 회원가입 완료 화면(authView/signup)에서 "OO 계정으로 시작해요" 박스에
	// 마스킹해서 보여주기 위해 필요. 구글은 scope에 email이 있어 항상 채워지지만, 카카오/라인은 이메일
	// 동의항목 승인 전이라 scope에서 뺐다(application.properties 참고) — 그 경우 이 값은 null로 남는다.
	@Column(name = "email", length = 100)
	private String email;

	// 추가됨 (2026-08-21) — 왜: 회원가입 완료 화면에서 "주로 이용할 동네"를 입력받기 위해 신설.
	// 카카오맵 좌표 연동 전이라 위경도(latitude/longitude)와 별개로 자유 텍스트로만 받는다.
	@Column(name = "region", length = 100)
	private String region;

	// 추가됨 (2026-08-21) — 왜: 최초 소셜 로그인 직후엔 닉네임/활동 지역을 아직 입력받지 않은 상태라,
	// OAuth2LoginSuccessHandler가 이 값을 보고 회원가입 완료 화면(/auth/signup)으로 보낼지 판단한다.
	@Builder.Default
	@Column(name = "profile_completed", nullable = false)
	private boolean profileCompleted = false;

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

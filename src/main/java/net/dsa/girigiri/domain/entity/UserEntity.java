package net.dsa.girigiri.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
// "user"는 MySQL 예약어라 회피. uniqueConstraints 추가됨 (2026-09-08, 코드 감사) — 왜: 로그인 키인
// (oauth_provider, oauth_id)에 유니크 제약이 없었다. 동시에 첫 로그인이 두 번 들어가면(더블클릭 등)
// 같은 조합으로 행이 2개 생기고, 그 뒤로 그 계정은 findByOauthProviderAndOauthId가 단건이 아니라
// 여러 건을 찾아 IncorrectResultSizeDataAccessException을 던져서 영구히 로그인 불가가 된다.
// 이메일 로그인도 oauth_provider="email"/oauth_id=이메일 규칙을 쓰므로(EmailUserDetailsService)
// 이 제약 하나로 소셜·이메일 계정 중복 가입을 전부 막는다. 반영 전 DB에 중복 데이터 없는 것 확인함.
// uk_users_phone (2026-09-10) — 왜: 휴대폰으로 "이메일 찾기"·"비밀번호 재설정"을 하려면 번호 1개당
// 계정 1개여야 조회가 명확하다. 미인증 번호지만 중복 가입은 막는다. NULL은 여러 개 허용(기존 회원).
@Table(name = "users", uniqueConstraints = {
		@UniqueConstraint(name = "uk_users_oauth", columnNames = {"oauth_provider", "oauth_id"}),
		@UniqueConstraint(name = "uk_users_phone", columnNames = {"phone"})
})
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

	// 추가됨 — 왜: 슈퍼어드민 회원 관리(정지/탈퇴)에 필요한 계정 상태. StoreEntity.approvalStatus와
	// 동일하게 문자열 상수 패턴을 따른다.
	public static final String STATUS_ACTIVE = "ACTIVE";
	public static final String STATUS_SUSPENDED = "SUSPENDED";

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

	@Builder.Default
	@Column(name = "status", length = 20)
	private String status = STATUS_ACTIVE;   // ACTIVE / SUSPENDED

	@Column(name = "nickname", length = 30)
	private String nickname;

	// 추가됨 (2026-08-21) — 왜: 회원가입 완료 화면(authView/signup)에서 "OO 계정으로 시작해요" 박스에
	// 마스킹해서 보여주기 위해 필요. 구글은 scope에 email이 있어 항상 채워지지만, 카카오/라인은 이메일
	// 동의항목 승인 전이라 scope에서 뺐다(application.properties 참고) — 그 경우 이 값은 null로 남는다.
	@Column(name = "email", length = 100)
	private String email;

	// 추가됨 (2026-09-10) — 왜: 회원가입 완료 화면에서 휴대폰 번호를 받는다. 본인확인(실명인증) API는
	// 사업자 계약이 필요해 붙이지 않았다 — 즉 이 값은 "인증된 번호"가 아니라 사용자가 입력한 값 그대로다.
	// 용도: (1) 향후 이메일/비밀번호 찾기 본인확인, (2) 노쇼 시 매장이 연락. "010-1234-5678" 형식으로 저장.
	@Column(name = "phone", length = 20)
	private String phone;

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

	// 추가됨 (2026-09-10) — 왜: 회원가입 완료 화면(authView/signup)에 약관 동의 단계를 넣으면서 신설.
	// 이용약관·개인정보 수집이용은 필수(체크 안 하면 가입 불가)라 가입 완료 시 항상 true지만,
	// "언제 무엇에 동의했는지"는 기록으로 남겨야 해서 컬럼으로 저장한다. 마케팅 수신은 선택이라
	// false로도 가입되고, 이후 알림 설정에서 on/off 하려면 이 값이 있어야 한다.
	@Builder.Default
	@Column(name = "terms_agreed", nullable = false)
	private boolean termsAgreed = false;

	@Builder.Default
	@Column(name = "privacy_agreed", nullable = false)
	private boolean privacyAgreed = false;

	@Builder.Default
	@Column(name = "marketing_agreed", nullable = false)
	private boolean marketingAgreed = false;

	@Column(name = "agreed_at")
	private LocalDateTime agreedAt;

	@CreatedDate
	@Column(name = "created_at", updatable = false)
	private LocalDateTime createdAt;

	@LastModifiedDate
	@Column(name = "updated_at")
	private LocalDateTime updatedAt;
}

package net.dsa.girigiri.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "store")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class StoreEntity {

	// 추가됨 (2026-08-21) — 왜: 점주 입점 신청 승인 상태 상수 정의 (WBS 2.1 문창호 / 7.2 송보미)
	public static final String STATUS_PENDING = "PENDING";   // 승인 대기
	public static final String STATUS_APPROVED = "APPROVED"; // 승인 완료
	public static final String STATUS_REJECTED = "REJECTED"; // 반려

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

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

	// 추가됨 (2026-08-21) — 왜: "떨이 서비스는 당일 판매·당일 픽업" 컨셉에 맞춰 픽업 시간을 손님이
	// 직접 고르지 않고, 매장이 한 번만 설정해두면 시스템이 "현재시간 + 준비시간 <= 마지막 픽업시간"으로
	// 자동 계산해서 주문 가능 여부/예상 픽업 시각을 판단하는 구조로 바꿨다 (PickupAvailabilityUtil 참고).
	// 아직 매장 설정 화면이 없어서 기존 매장(sample-data.sql)은 이 값들이 비어있을(NULL) 수 있는데,
	// 그럴 땐 "설정 전이라 항상 주문 가능"으로 취급한다 (PickupAvailabilityUtil.canOrderNow 참고).
	@Builder.Default
	@Column(name = "prep_time_minutes")
	private Integer prepTimeMinutes = 20;   // 기본 준비시간(분): 주문~픽업 준비에 걸리는 시간

	@Column(name = "last_pickup_time")
	private LocalTime lastPickupTime;   // 마지막 픽업시간: 이 시간 이후로는 오늘 주문/픽업 마감

	@Column(name = "role", nullable = false, length = 20)
	private String role;   // = OWNER (점주 매장)

	// 추가됨 (2026-08-21) — 왜: 점주(OWNER) 입점 신청 시 사업자번호 입력 및 검증용
	@Column(name = "business_number", length = 30)
	private String businessNumber;

	// 추가됨 (2026-08-21) — 왜: 점주(OWNER) 매장 대표 연락처
	@Column(name = "phone", length = 30)
	private String phone;

	// 추가됨 (2026-08-21) — 왜: 점주 입점 심사 상태 (PENDING -> 운영자 승인 시 APPROVED -> 점주 모드 활성화)
	@Builder.Default
	@Column(name = "approval_status", length = 20)
	private String approvalStatus = STATUS_PENDING;

	// 변경됨 — 왜: Store가 자체 loginId/password를 갖는 "독립 계정" 모델(안A)과, 세션 설계
	// ({ userId, role, viewMode, storeId })가 암시하는 "User가 storeId로 Store를 소유"하는 모델(안B)이
	// 어긋난다는 지적으로 확정함: 안B로 정리(Store는 로그인 주체가 아니고, User(role=OWNER)가
	// owner_id로 소유하는 데이터일 뿐). login_id/password 컬럼은 그래서 제거했다 — 실제로 어디서도
	// 참조하지 않던 죽은 컬럼이었다(로그인은 전부 User 쪽 OAuth2/이메일로만 이뤄짐).
	@Column(name = "owner_id")
	private Long ownerId;

	@CreatedDate
	@Column(name = "created_at", updatable = false)
	private LocalDateTime createdAt;

	@LastModifiedDate
	@Column(name = "updated_at")
	private LocalDateTime updatedAt;
}

package net.dsa.girigiri.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 쿠폰 캠페인 — 2026-09-07 신규 (송채현, WBS "쿠폰 발급/관리").
 * 2026-10-01 확장 (지역별 현황 드릴다운 — 매장 지정 쿠폰) — scope/discountType 등 추가.
 *
 * 두 가지 캠페인 종류를 한 엔티티로 표현한다:
 * 1) 코드형(scope=null, 기존 동작 그대로) — 슈퍼어드민이 이벤트마다 만들고("추석 프로모션" 등),
 *    회원이 code를 직접 입력해서 "발급받기" 한다.
 * 2) 매장 지정형(scope=STORE) — 통계 대시보드 "지역별 현황"에서 소진율 낮은 매장을 골라 만든다.
 *    code 없이, 대상 매장의 손님용 상세 페이지 "쿠폰 받기" 버튼으로 받는다(SuperAdminCouponService
 *    #createStoreCampaign / CouponService#claimCampaignCoupon 참고). 대상 매장 목록은 별도 테이블
 *    coupon_store(CouponStoreEntity)에 N:M으로 저장한다.
 * scope=REGION은 enum 값과 컬럼만 만들어둔 상태다 — 발행 UI도 로직도 아직 없고, 요청이 오면
 * SuperAdminCouponService가 "준비 중" 예외로 막는다(2026-10-01 범위 확정, 조장 확인).
 *
 * 캠페인 1개당 회원 1명에게 1장만(코드형·매장지정형 공통) — 발급 이력은 CouponEntity.campaignId로
 * 추적, CouponRepository.existsByCampaignIdAndIssuedToUserId로 중복 방지.
 *
 * 발급된 쿠폰(CouponEntity) 각각의 할인 조건/만료일은 발급 시점의 이 캠페인 값을 그대로 복사해서
 * 저장한다 — 나중에 캠페인 값을 바꿔도 이미 발급된 쿠폰에는 영향이 없다(일반적인 쿠폰 정책과 동일).
 */
@Entity
@Table(name = "coupon_campaign")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class CouponCampaignEntity {

	public static final String SCOPE_STORE = "STORE";
	public static final String SCOPE_REGION = "REGION"; // 값/컬럼만 존재 — 발행 로직 없음(위 클래스 주석 참고)

	public static final String DISCOUNT_TYPE_RATE = "RATE";
	public static final String DISCOUNT_TYPE_AMOUNT = "AMOUNT";

	public static final String FUNDED_BY_PLATFORM = "PLATFORM";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// 관리용 이름 — 회원에게 노출 안 함(슈퍼어드민 목록 화면 구분용).
	@Column(name = "name", nullable = false, length = 100)
	private String name;

	// 회원이 "쿠폰 받기" 화면에서 입력하는 코드. 대문자로 정규화해 저장(CouponService.normalizeCode).
	// 매장 지정형(scope=STORE)은 코드 입력 없이 버튼으로 받으므로 null — 2026-10-01, nullable로 완화.
	@Column(name = "code", length = 30, unique = true)
	private String code;

	// null = 기존 코드형 일반 프로모션(매장/지역 제한 없음, 어디서든 사용 가능).
	@Column(name = "scope", length = 10)
	private String scope;

	// 2026-10-01 추가 — RATE면 discountRate(%) 사용, AMOUNT면 discountAmount(원) 사용.
	// 기존 코드형 캠페인은 전부 RATE로 들어간다(SuperAdminCouponService#create에서 명시적으로 세팅).
	@Column(name = "discount_type", length = 10)
	private String discountType;

	// discountType=RATE일 때만 사용 — 기존 "할인코드" 필드 그대로.
	@Column(name = "discount_rate")
	private Integer discountRate;

	// discountType=AMOUNT일 때만 사용(원 단위 정액 할인).
	@Column(name = "discount_amount")
	private Integer discountAmount;

	// discountType=RATE일 때만 의미 있음 — 정률 할인의 최대 할인 금액 캡. null이면 캡 없음.
	@Column(name = "max_discount_amount")
	private Integer maxDiscountAmount;

	// 이 쿠폰을 쓰려면 주문 금액(할인 전)이 이 값 이상이어야 한다. null/0이면 제한 없음.
	@Column(name = "min_order_amount")
	private Integer minOrderAmount;

	// 발급 수량 상한. null이면 무제한(기존 코드형 캠페인과 동일). 매장 지정형은 모달에서 필수 입력.
	@Column(name = "issue_limit")
	private Integer issueLimit;

	// 비용 부담 주체 — 지금은 PLATFORM(FUNDED_BY_PLATFORM)만 존재: 정산 시 점주 매출에서 차감하지
	// 않는다(SettlementService는 이 필드를 보지 않음 — 매장 지정 쿠폰의 할인분은 전부 플랫폼이 흡수).
	@Column(name = "funded_by", length = 20)
	private String fundedBy;

	// 매장 지정형 캠페인을 만든 슈퍼어드민 계정(users.id) — 코드형(기존)은 null.
	@Column(name = "issued_by_admin_id")
	private Long issuedByAdminId;

	// 발행 사유(예: "소진율 점검 필요") — 운영 기록용, 회원에게 노출 안 함.
	@Column(name = "issue_reason", length = 200)
	private String issueReason;

	// 이 캠페인으로 발급되는 쿠폰들의 만료 시각(발급 시점과 무관하게 캠페인 전체가 같은 만료일을 씀).
	@Column(name = "expires_at", nullable = false)
	private LocalDateTime expiresAt;

	// 슈퍼어드민이 끄면(false) 그 즉시 새 발급이 막힌다 — 이미 발급된 쿠폰은 그대로 유효.
	@Builder.Default
	@Column(name = "active", nullable = false)
	private boolean active = true;

	@CreatedDate
	@Column(name = "created_at", updatable = false)
	private LocalDateTime createdAt;
}

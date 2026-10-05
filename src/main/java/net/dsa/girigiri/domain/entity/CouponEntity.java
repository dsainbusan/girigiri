package net.dsa.girigiri.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 회원 1명에게 발급된 쿠폰 1장 — 2026-09-07 신규, 2026-09-07 재설계 (송채현, WBS "쿠폰 발급/관리").
 *
 * 재설계 배경: 처음엔 "매장주가 공개 코드를 만들고 손님이 체크아웃에서 직접 입력, 여러 명이 한도까지
 * 나눠 씀" 방식으로 만들었는데, 채채 확인 결과 실제로는 다음 3가지 발급 경로만 있고 전부 "회원 1명당
 * 쿠폰 1장이 실제로 지급되는" 방식이라 quantityLimit/usedCount(공유 수량 풀) 개념 자체가 필요 없다:
 *
 * 1) 웰컴 쿠폰 (source=WELCOME) — 신규 가입 시 시스템이 전체 회원에게 자동 1장씩 (WelcomeCouponScheduler).
 * 2) 매장 귀책 보상 쿠폰 (source=STORE_COMPENSATION) — 매장이 예약을 취소(cancelledBy=STORE)했을 때
 *    책임이 매장에 있으므로 해당 회원에게 자동 지급 (ReservationService#cancelByStore 참고).
 * 3) 프로모션 쿠폰 (source=PROMOTION) — 슈퍼어드민이 만든 이벤트 코드(CouponCampaignEntity)를 회원이
 *    직접 입력해서 "발급받기" — 캠페인 1개당 회원 1명이 딱 1장만 받을 수 있다(CouponService 참고).
 *
 * 그래서 "발급 수량 제한"은 이제 코드 하나를 여러 명이 나눠 쓰는 게 아니라 "캠페인 1개당 회원마다
 * 1장만" 규칙으로 표현되고(CouponRepository.existsByCampaignIdAndIssuedToUserId), 쿠폰 자체는
 * 그냥 "썼는지(used) 안 썼는지"만 있으면 된다.
 *
 * 사용/복구 규칙(2026-09-07, 채채 확인): 체크아웃에서 쓰면 used=true. 그 예약이 나중에 취소되면
 * 원칙적으로 복구(used=false)하되, "회원이 노쇼한 경우"(고객 책임)만 예외로 복구하지 않는다 —
 * ReservationService의 각 취소/노쇼 경로에서 CouponService.restore()/markUsed() 호출로 처리한다
 * (2026-09-07 시점: 체크아웃 화면에서 쿠폰을 실제로 고르는 UI는 다음 작업 "할인코드 적용/검증"에서
 * 붙인다 — 이 엔티티와 복구 규칙은 그 작업을 위한 선행 준비).
 *
 * ⚠️ 새 엔티티 — 공통 DB 오너(송보미)에게 스키마 공유/리뷰 필요.
 */
@Entity
@Table(name = "coupon")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class CouponEntity {

	public static final String SOURCE_WELCOME = "WELCOME";
	public static final String SOURCE_STORE_COMPENSATION = "STORE_COMPENSATION";
	public static final String SOURCE_PROMOTION = "PROMOTION";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// 어떤 경로로 발급됐는지 — 위 SOURCE_* 상수 중 하나.
	@Column(name = "source", nullable = false, length = 30)
	private String source;

	// 이 쿠폰을 받은 회원. 셋 중 어느 경로든 항상 특정 회원 1명에게 귀속된다.
	@Column(name = "issued_to_user_id", nullable = false)
	private Long issuedToUserId;

	// source=PROMOTION일 때만 채워짐 — 어느 캠페인에서 발급받았는지(회원당 캠페인 1개에 1장 제한용).
	@Column(name = "campaign_id")
	private Long campaignId;

	// source=STORE_COMPENSATION일 때만 채워짐 — 보상 사유가 된 예약(감사/문의 대응용, 선택적).
	@Column(name = "source_reservation_id")
	private Long sourceReservationId;

	// 2026-10-01 추가 — 발급 시점의 캠페인 scope를 그대로 복사(CouponCampaignEntity.SCOPE_*).
	// null = 제한 없음(웰컴/매장보상/기존 코드형 캠페인 전부 여기 해당 — 하위호환).
	@Column(name = "scope", length = 10)
	private String scope;

	// 정률 할인(%). 기존 상품 할인율(DiscountRateCalculator)과 같은 방식.
	// nullable로 완화됨 (2026-10-01) — discountType=AMOUNT인 매장 지정 쿠폰은 이 필드 대신
	// discountAmount를 쓴다. 웰컴/매장보상/코드형은 전부 그대로 값이 채워진다(하위호환).
	@Column(name = "discount_rate")
	private Integer discountRate;

	// 2026-10-01 추가 — discountType=AMOUNT(정액, 원)일 때만 채워짐. null이면 discountRate(정률)
	// 방식 그대로 — 기존 발급 경로(웰컴/매장보상/코드형)는 전부 이 필드가 null이다.
	@Column(name = "discount_type", length = 10)
	private String discountType;

	@Column(name = "discount_amount")
	private Integer discountAmount;

	// discountType=RATE일 때만 의미 있음 — 정률 할인의 최대 할인 금액 캡. null이면 캡 없음.
	@Column(name = "max_discount_amount")
	private Integer maxDiscountAmount;

	// 이 쿠폰을 쓰려면 주문 금액(할인 전)이 이 값 이상이어야 한다. null/0이면 제한 없음.
	@Column(name = "min_order_amount")
	private Integer minOrderAmount;

	// 이 시각(포함) 이후로는 만료 처리.
	@Column(name = "expires_at", nullable = false)
	private LocalDateTime expiresAt;

	// 사용 여부. 체크아웃에서 쓰면 true, 그 예약이 매장 귀책/일반 취소로 취소되면 다시 false로 복구된다
	// (회원 노쇼로 인한 취소는 예외 — 복구 안 함. ReservationService 참고).
	@Builder.Default
	@Column(name = "used", nullable = false)
	private boolean used = false;

	// 추가됨 (2026-09-22, 보미 피드백 반영) — 왜: used가 언제 true로 바뀌었는지 기록이 없어서
	// "이 쿠폰이 실제로 언제 소진됐는지" 알 방법이 없었다. BaseTimeEntity의 원칙(범용 updated_at보다
	// 의미가 분명한 도메인 컬럼을 쓸 것)을 따라 generic updated_at 대신 이 이름으로 둔다.
	// CouponService.markUsed()에서 채우고, restore()로 되돌릴 때(취소/노쇼 복구) 다시 null로 비운다.
	@Column(name = "used_at")
	private LocalDateTime usedAt;

	@CreatedDate
	@Column(name = "created_at", updatable = false)
	private LocalDateTime createdAt;
}

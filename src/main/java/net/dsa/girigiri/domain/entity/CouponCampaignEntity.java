package net.dsa.girigiri.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 프로모션 이벤트 쿠폰 캠페인 — 2026-09-07 신규 (송채현, WBS "쿠폰 발급/관리").
 *
 * 슈퍼어드민이 이벤트마다 하나씩 만든다("추석 프로모션" 등). code는 회원이 앱에서 직접 입력해서
 * "발급받기" 하는 용도 — 캠페인 자체가 코드 1개당 회원 1명에게 1장만 내주는 단위다(발급 이력은
 * CouponEntity.campaignId로 추적, CouponRepository.existsByCampaignIdAndIssuedToUserId로 중복 방지).
 *
 * 발급된 쿠폰(CouponEntity) 각각의 할인율/만료일은 발급 시점의 이 캠페인 값을 그대로 복사해서 저장한다
 * — 나중에 캠페인 값을 바꿔도 이미 발급된 쿠폰에는 영향이 없다(일반적인 쿠폰 정책과 동일).
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

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// 관리용 이름 — 회원에게 노출 안 함(슈퍼어드민 목록 화면 구분용).
	@Column(name = "name", nullable = false, length = 100)
	private String name;

	// 회원이 "쿠폰 받기" 화면에서 입력하는 코드. 대문자로 정규화해 저장(CouponService.normalizeCode).
	@Column(name = "code", nullable = false, length = 30, unique = true)
	private String code;

	@Column(name = "discount_rate", nullable = false)
	private Integer discountRate;

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

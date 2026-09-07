package net.dsa.girigiri.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 쿠폰 정책 — 2026-09-07 추가 (송채현, 채채 확인).
 * "쿠폰은 사장님이 아니라 슈퍼어드민이 만들어서 뿌린다"는 원칙은 원래 프로모션 쿠폰(CouponCampaignEntity)에만
 * 적용돼 있었는데, 웰컴/매장취소보상 쿠폰의 할인율은 CouponService에 잠정값으로 하드코딩돼 있어서
 * 슈퍼어드민이 손댈 방법이 없었다 — 이 엔티티로 그 두 값을 슈퍼어드민이 직접 수정할 수 있게 뺐다.
 *
 * 캠페인처럼 여러 건이 아니라 "정책값" 하나뿐이라 항상 딱 1행(id=1)만 존재한다 — 없으면
 * CouponService#getOrCreatePolicy가 기본값(웰컴 10% / 매장보상 15%, 기존 하드코딩값과 동일)으로
 * 만들어준다. NotificationSettingEntity + NotificationService#getOrCreateSettings와 같은 패턴
 * (다만 그쪽은 사용자 1명당 1행, 여기는 전체 1행).
 */
@Entity
@Table(name = "coupon_policy")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class CouponPolicyEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "welcome_discount_rate", nullable = false)
	private Integer welcomeDiscountRate;

	@Column(name = "compensation_discount_rate", nullable = false)
	private Integer compensationDiscountRate;

	@LastModifiedDate
	@Column(name = "updated_at")
	private LocalDateTime updatedAt;
}

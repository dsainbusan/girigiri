package net.dsa.girigiri.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 사용자가 실제로 획득(해금)한 뱃지 기록.
 *
 * Badge enum의 조건은 매번 현재 통계로 재계산되는데, GOAL_HIT(이달 목표 달성률)처럼
 * 시간이 지나며 다시 거짓이 될 수 있는 조건도 있다. "한 번 딴 뱃지는 영구적으로 유지"되도록,
 * 조건을 처음 충족한 시점에 이 테이블에 기록해두고 이후로는 이 기록의 존재 여부로 해금 상태를 판정한다
 * (LedgerService.build 참고).
 */
@Entity
@Table(name = "user_badge", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "badge_code"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserBadgeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "badge_code", nullable = false, length = 50)
	private String badgeCode;

	@Column(name = "earned_at", nullable = false)
	private LocalDateTime earnedAt;

	// "🎉 새 뱃지 획득!" 토스트를 이미 보여줬는지. 기본값 false(Java 기본값) — 새로 딴 뱃지는 아직
	// 안 보여준 상태로 만들어서 다음 build() 호출 때 토스트에 뜨게 한다(LedgerService.build 참고).
	// 기존 행은 DB 마이그레이션에서 TRUE로 깔아뒀다(과거 뱃지가 갑자기 우르르 토스트로 뜨는 걸 방지).
	@Column(name = "notified", nullable = false)
	private boolean notified;
}

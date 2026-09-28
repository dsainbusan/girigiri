package net.dsa.girigiri.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 탈퇴 회원 최소 보관 기록 — 2026-09-22 신규 (송채현, 보미 피드백 반영 — soft delete 정책 후속).
 *
 * 개인정보보호법 제21조는 보유기간이 지나거나 처리 목적을 달성한 개인정보는 지체없이 파기하되, 다른
 * 법령에 따라 보존해야 하는 정보는 별도로 분리해서 저장·관리하도록 정한다("분리보관"). 예약/결제/
 * 영수증 같은 실제 거래 기록은 각 테이블에 이미 영구 보관되고 있어서(전자상거래법상 대금결제 기록
 * 등 보존의무), 이 테이블은 그 기록들과 탈퇴 회원을 나중에(분쟁 대응 등) 연결 지을 수 있는 최소한의
 * 식별 정보만 남긴다. 닉네임·활동지역·좌표처럼 서비스 이용에만 쓰이던 정보는 여기 옮기지 않고,
 * UserPurgeScheduler가 users 원본 로우를 실제로 지울 때 같이 사라진다.
 *
 * ⚠️ 여기 담는 필드와 UserPurgeScheduler의 보존기간은 실제 법무 검토 전 잠정값이다 — 서비스 약관/
 * 개인정보처리방침이 확정되면 그에 맞게 다시 확인이 필요하다 (지금은 DB 설계 리뷰 결과 채채·보미
 * 합의로 우선 구조만 만들어둔 상태).
 */
@Entity
@Table(name = "user_archive")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class UserArchiveEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// 원래 users.id — users 로우가 실제로 삭제된 뒤에도 예약/결제 등 거래 기록(user_id 값)과
	// 대조할 수 있게 남긴다. FK 매핑은 팀 컨벤션대로 하지 않는다.
	@Column(name = "original_user_id", nullable = false, unique = true)
	private Long originalUserId;

	@Column(name = "oauth_provider", length = 20)
	private String oauthProvider;

	@Column(name = "email", length = 100)
	private String email;

	@Column(name = "phone", length = 20)
	private String phone;

	@Column(name = "joined_at")
	private LocalDateTime joinedAt;

	@Column(name = "withdrawn_at", nullable = false)
	private LocalDateTime withdrawnAt;

	@CreatedDate
	@Column(name = "archived_at", updatable = false)
	private LocalDateTime archivedAt;
}

package net.dsa.girigiri.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 점주가 자기 매장을 홍보/안내하려고 직접 쓰는 공지 1건 — 2026-09-29 신규.
 * 손님이 매장 상세 페이지(정보 탭)에서 읽는다.
 *
 * NoticeEntity(슈퍼어드민이 전체 회원에게 쓰는 플랫폼 공지)와는 완전히 다른 도메인이라 별도
 * 테이블로 둔다 — 이름도 헷갈리지 않게 "공지(Notice)" 대신 "안내(Announcement)"를 썼다
 * (StoreNoticeController/Service는 이미 "점주가 슈퍼어드민 공지를 읽는" 기존 기능이 쓰고 있어서
 * 이름이 겹치면 안 된다).
 */
@Entity
@Table(name = "store_announcement")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class StoreAnnouncementEntity extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "store_id", nullable = false)
	private Long storeId;

	@Column(name = "title", length = 100, nullable = false)
	private String title;

	@Column(name = "content", length = 2000, nullable = false)
	private String content;

	// 2026-09-29 추가 — 손님 매장 상세에 노출 중인 대표 공지인지 여부 (매장당 1개만 true)
	@Column(name = "is_exposed", nullable = false)
	@Builder.Default
	private boolean exposed = true;
}

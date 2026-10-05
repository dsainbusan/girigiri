package net.dsa.girigiri.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "review")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class ReviewEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "store_id", nullable = false)
	private Long storeId;

	// 추가됨 (2026-10-05, 사용자 요청) — 왜: "가게당 리뷰 1개"에서 "주문(예약)당 리뷰 1개"로 정책을
	// 바꾸면서, 어느 주문에 대한 리뷰인지 식별할 방법이 필요했다. 기존 행(이 컬럼 도입 전에 쓰인
	// 리뷰)은 전부 NULL로 남는다 — 레거시 리뷰는 "가게당 1개" 시절 기록이라 특정 주문에 소급해서
	// 연결할 근거가 없다(ddl-auto=update라 마이그레이션 스크립트 없이 컬럼만 추가됨).
	@Column(name = "reservation_id")
	private Long reservationId;

	@Column(name = "rating", nullable = false)
	private Integer rating;

	@Column(name = "content", length = 500)
	private String content;

	// 추가됨 (강노은) — 왜: "사진 리뷰". 프로젝트에 아직 파일 업로드 인프라가 없어서(ProductEntity.imageUrl도
	// 업로드가 아니라 URL 문자열이다) 같은 컨벤션을 따라 이미지 URL 문자열로 받는다. null/빈 값이면 사진 없는 리뷰.
	@Column(name = "image_url", length = 500)
	private String imageUrl;

	// 추가됨 (강노은) — 왜: 리뷰 수정 남용(예: 작성 후 몰래 내용을 바꾸는 것) 방지용으로
	// 목록에 "수정됨" 표시를 하기 위해 필요. 최초 작성 시 false, ReviewService#submitReview에서
	// 기존 리뷰를 덮어쓸 때만 true로 바뀐다.
	@Builder.Default
	@Column(name = "edited", nullable = false)
	private boolean edited = false;

	@CreatedDate
	@Column(name = "created_at", updatable = false)
	private LocalDateTime createdAt;

	// 추가됨 (문창호, 2026-09-17) — 왜: WBS "문의 답변/리뷰 답글"(김태훈 인수) — 가게 사장님이 리뷰에
	// 남기는 답글. replyEdited는 위 edited와 같은 패턴(수정 여부만 가볍게 표시 — 과거 답글 내용을
	// 전부 남기는 별도 이력 테이블은 아니고, 리뷰 자체도 그렇게 하지 않는 것과 동일한 컨벤션이다).
	@Column(name = "reply_content", length = 500)
	private String replyContent;

	@Column(name = "reply_created_at")
	private LocalDateTime replyCreatedAt;

	@Builder.Default
	@Column(name = "reply_edited", nullable = false)
	private boolean replyEdited = false;
}

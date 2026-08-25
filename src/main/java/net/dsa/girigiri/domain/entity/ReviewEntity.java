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
}

package net.dsa.girigiri.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 강노은: 가게별 "AI 리뷰 요약" 캐시 1건 (가게당 최대 1행).
 * 매 요청마다 Gemini를 다시 부르면 느리고 비용도 들어서, 마지막으로 요약을 만들었을 때의 리뷰
 * 개수(reviewCountAtSummary)를 같이 저장해뒀다가, 지금 리뷰 개수와 다를 때만(=새 리뷰가 생겼을
 * 때만) 다시 생성한다 — ReviewService.getReviewSummary() 참고.
 *
 * generatedAt은 @CreatedDate를 안 쓴다 — 재생성 때마다(=UPDATE) 값을 갱신해야 하는데
 * @CreatedDate는 최초 INSERT 시점에만 채워지고 이후 갱신되지 않아서, 서비스 코드에서 직접 채운다.
 */
@Entity
@Table(name = "review_summary")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewSummaryEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "store_id", nullable = false, unique = true)
	private Long storeId;

	@Column(name = "summary", length = 1000, nullable = false)
	private String summary;

	@Column(name = "review_count_at_summary", nullable = false)
	private int reviewCountAtSummary;

	@Column(name = "generated_at")
	private LocalDateTime generatedAt;
}

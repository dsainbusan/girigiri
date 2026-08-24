package net.dsa.girigiri.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 문의 글에 달리는 댓글. 작성자 본인이 이어서 남기는 댓글일 수도, 가게 사장님/운영자가 남기는
 * 답변일 수도 있다 — 둘을 구분하는 컬럼은 아직 없다(role/viewMode 세션 로직 확정 후 추가 검토).
 */
@Entity
@Table(name = "inquiry_comment")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class InquiryCommentEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "inquiry_id", nullable = false)
	private Long inquiryId;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "content", nullable = false, length = 500)
	private String content;

	@CreatedDate
	@Column(name = "created_at", updatable = false)
	private LocalDateTime createdAt;
}

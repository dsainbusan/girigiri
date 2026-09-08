package net.dsa.girigiri.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 문의 게시판 글. storeId가 있으면 특정 가게에 대한 문의, null이면 서비스 전체에 대한 일반 문의다.
 */
@Entity
@Table(name = "inquiry")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class InquiryEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "store_id")
	private Long storeId;   // null이면 일반 문의(특정 가게 무관)

	// 추가됨 (2026-09-08) — 왜: 예약 상세에서 "문의하기" 버튼으로 접수된 문의는 어떤 주문 때문인지
	// 이 컬럼으로 연결된다. ComplaintEntity.targetReservationId와 동일한 취지 — storeId는 이 값이
	// 있으면 예약에서 자동으로 채워진다(InquiryService.createInquiry 참고). 가게 상세/고객센터에서
	// 올린 기존 문의는 이 값이 null.
	@Column(name = "reservation_id")
	private Long reservationId;

	@Column(name = "title", nullable = false, length = 100)
	private String title;

	@Column(name = "content", nullable = false, length = 1000)
	private String content;

	// 추가됨 (강노은) — 왜: 문의에 사진을 첨부할 수 있게(예: 상품 하자 사진 등). 리뷰 사진과 같은 방식
	// (FileStorageUtil, upload/inquiries/...)으로 로컬에 저장하고 웹 경로만 저장한다.
	// null/빈 값이면 사진 없는 문의. 수정 기능은 없어서(문의는 등록만 가능) 삭제 시에도 그대로 지운다.
	@Column(name = "image_url", length = 500)
	private String imageUrl;

	@CreatedDate
	@Column(name = "created_at", updatable = false)
	private LocalDateTime createdAt;
}

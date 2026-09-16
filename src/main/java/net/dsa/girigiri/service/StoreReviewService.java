package net.dsa.girigiri.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.entity.NotificationEntity;
import net.dsa.girigiri.domain.entity.ReviewEntity;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.repository.ReviewRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

/**
 * 가게 사장님이 자기 매장 리뷰에 남기는 답글 (WBS 3.0 "문의 답변/리뷰 답글", 원래 김태훈 담당 →
 * 문창호가 인수, 2026-09-17). 리뷰 조회(ReviewService, 강노은 담당)는 그대로 재사용하고, 여기서는
 * 답글 등록/수정/삭제(사장님 전용 쓰기 작업)만 담당한다.
 */
@Service
@RequiredArgsConstructor
public class StoreReviewService {

	private final ReviewRepository reviewRepository;
	private final StoreAccessService storeAccessService;
	private final NotificationService notificationService;

	@Transactional
	public void reply(Long ownerId, Long reviewId, String content) {
		ReviewEntity review = reviewRepository.findById(reviewId)
				.orElseThrow(() -> new EntityNotFoundException("리뷰를 찾을 수 없습니다: " + reviewId));
		assertOwnsReviewStore(ownerId, review);

		String trimmed = content == null ? "" : content.trim();
		if (trimmed.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "답글 내용을 입력해주세요.");
		}

		boolean isFirstReply = review.getReplyContent() == null || review.getReplyContent().isBlank();
		if (isFirstReply) {
			review.setReplyCreatedAt(LocalDateTime.now());
		} else {
			review.setReplyEdited(true);
		}
		review.setReplyContent(trimmed);
		reviewRepository.save(review);

		// 첫 답글일 때만 알린다 — 수정은 같은 답글을 다듬는 것뿐이라 매번 알리면 스팸이 된다.
		if (isFirstReply) {
			notificationService.createNotification(review.getUserId(), NotificationEntity.TYPE_REVIEW_REPLY,
					"내가 남긴 리뷰에 사장님 답글이 달렸어요.", "/user/stores/" + review.getStoreId(),
					"review_reply:" + reviewId);
		}
	}

	@Transactional
	public void deleteReply(Long ownerId, Long reviewId) {
		ReviewEntity review = reviewRepository.findById(reviewId)
				.orElseThrow(() -> new EntityNotFoundException("리뷰를 찾을 수 없습니다: " + reviewId));
		assertOwnsReviewStore(ownerId, review);

		review.setReplyContent(null);
		review.setReplyCreatedAt(null);
		review.setReplyEdited(false);
		reviewRepository.save(review);
	}

	private void assertOwnsReviewStore(Long ownerId, ReviewEntity review) {
		StoreEntity store = storeAccessService.findMyStore(ownerId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "매장 정보가 없습니다."));
		if (!store.getId().equals(review.getStoreId())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "이 리뷰에 답글을 남길 권한이 없습니다.");
		}
	}
}

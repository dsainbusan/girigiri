package net.dsa.girigiri.service;

import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.MyReviewRowDto;
import net.dsa.girigiri.domain.dto.ReviewRowDto;
import net.dsa.girigiri.domain.entity.ReviewEntity;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.domain.entity.UserEntity;
import net.dsa.girigiri.repository.ReviewRepository;
import net.dsa.girigiri.repository.StoreRepository;
import net.dsa.girigiri.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReviewService {

	private final ReviewRepository reviewRepository;
	private final UserRepository userRepository;
	private final StoreRepository storeRepository;

	public List<ReviewRowDto> getReviews(Long storeId, Long currentUserId, String role) {
		List<ReviewEntity> reviews = reviewRepository.findAll().stream()
				.filter(r -> storeId.equals(r.getStoreId()))
				.sorted(Comparator.comparing(ReviewEntity::getCreatedAt,
						Comparator.nullsLast(Comparator.reverseOrder())))
				.toList();

		Map<Long, String> nicknameByUserId = userRepository.findAllById(
						reviews.stream().map(ReviewEntity::getUserId).distinct().toList())
				.stream()
				.collect(Collectors.toMap(UserEntity::getId, u -> u.getNickname() == null ? "익명" : u.getNickname()));

		return reviews.stream()
				.map(r -> new ReviewRowDto(
						r.getId(),
						nicknameByUserId.getOrDefault(r.getUserId(), "익명"),
						r.getRating() == null ? 0 : r.getRating(),
						r.getContent(),
						r.getImageUrl(),
						relativeLabel(r.getCreatedAt()),
						currentUserId != null && currentUserId.equals(r.getUserId()),
						r.isEdited(),
						canDelete(r.getUserId(), currentUserId, role)
				))
				.toList();
	}

	/** "내 리뷰 관리" 페이지용 — 매장 구분 없이 내가 쓴 리뷰 전부를 최신순으로. */
	public List<MyReviewRowDto> getMyReviews(Long userId) {
		List<ReviewEntity> myReviews = reviewRepository.findAll().stream()
				.filter(r -> userId.equals(r.getUserId()))
				.sorted(Comparator.comparing(ReviewEntity::getCreatedAt,
						Comparator.nullsLast(Comparator.reverseOrder())))
				.toList();

		Map<Long, String> storeNameById = storeRepository.findAllById(
						myReviews.stream().map(ReviewEntity::getStoreId).distinct().toList())
				.stream()
				.collect(Collectors.toMap(StoreEntity::getId, StoreEntity::getStoreName));

		return myReviews.stream()
				.map(r -> new MyReviewRowDto(
						r.getId(),
						r.getStoreId(),
						storeNameById.getOrDefault(r.getStoreId(), "알 수 없는 가게"),
						r.getRating() == null ? 0 : r.getRating(),
						r.getContent(),
						r.getImageUrl(),
						relativeLabel(r.getCreatedAt()),
						r.isEdited()
				))
				.toList();
	}

	public double getAverageRating(Long storeId) {
		return reviewRepository.findAll().stream()
				.filter(r -> storeId.equals(r.getStoreId()))
				.mapToInt(r -> r.getRating() == null ? 0 : r.getRating())
				.average().orElse(0);
	}

	public int getReviewCount(Long storeId) {
		return (int) reviewRepository.findAll().stream()
				.filter(r -> storeId.equals(r.getStoreId()))
				.count();
	}

	public Optional<ReviewEntity> getMyReview(Long userId, Long storeId) {
		if (userId == null) {
			return Optional.empty();
		}
		return reviewRepository.findAll().stream()
				.filter(r -> userId.equals(r.getUserId()) && storeId.equals(r.getStoreId()))
				.findFirst();
	}

	/**
	 * 사용자당 매장 1개에 리뷰 1개만 — 이미 썼으면 내용을 덮어쓴다(재작성). 재작성이면 "수정됨" 표시가 남는다.
	 * imageUrl은 폼에 입력된 값을 그대로 반영한다 — 빈 값으로 다시 제출하면 사진이 빠진 것으로 보고 지운다
	 * (content와 동일하게 "매번 전체 값을 새로 받는다" 컨벤션).
	 */
	@Transactional
	public void submitReview(Long userId, Long storeId, int rating, String content, String imageUrl) {
		int clampedRating = Math.max(1, Math.min(5, rating));
		Optional<ReviewEntity> existing = getMyReview(userId, storeId);
		ReviewEntity review = existing.orElseGet(() -> ReviewEntity.builder()
				.userId(userId)
				.storeId(storeId)
				.build());
		if (existing.isPresent()) {
			review.setEdited(true);
		}
		review.setRating(clampedRating);
		review.setContent(content == null ? "" : content.trim());
		review.setImageUrl(imageUrl == null || imageUrl.isBlank() ? null : imageUrl.trim());
		reviewRepository.save(review);
	}

	/** 가게 사장님은 리뷰를 볼 순 있어도 지울 순 없다 — 작성자 본인 / 관리자만 true. */
	private boolean canDelete(Long reviewUserId, Long currentUserId, String role) {
		return UserEntity.ROLE_ADMIN.equals(role) || (currentUserId != null && currentUserId.equals(reviewUserId));
	}

	@Transactional
	public void deleteReview(Long userId, String role, Long reviewId) {
		ReviewEntity review = reviewRepository.findById(reviewId)
				.orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
						org.springframework.http.HttpStatus.NOT_FOUND, "리뷰를 찾을 수 없습니다: " + reviewId));
		if (!canDelete(review.getUserId(), userId, role)) {
			throw new org.springframework.web.server.ResponseStatusException(
					org.springframework.http.HttpStatus.FORBIDDEN, "이 리뷰를 삭제할 권한이 없습니다.");
		}
		reviewRepository.delete(review);
	}

	private String relativeLabel(java.time.LocalDateTime createdAt) {
		if (createdAt == null) {
			return "";
		}
		long days = ChronoUnit.DAYS.between(createdAt.toLocalDate(), LocalDate.now());
		if (days <= 0) {
			return "오늘";
		}
		if (days == 1) {
			return "어제";
		}
		return days + "일 전";
	}
}

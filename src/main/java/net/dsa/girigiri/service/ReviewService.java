package net.dsa.girigiri.service;

import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.ReviewRowDto;
import net.dsa.girigiri.domain.entity.ReviewEntity;
import net.dsa.girigiri.domain.entity.UserEntity;
import net.dsa.girigiri.repository.ReviewRepository;
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
						relativeLabel(r.getCreatedAt()),
						currentUserId != null && currentUserId.equals(r.getUserId()),
						r.isEdited(),
						canDelete(r.getUserId(), currentUserId, role)
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

	/** 사용자당 매장 1개에 리뷰 1개만 — 이미 썼으면 내용을 덮어쓴다(재작성). 재작성이면 "수정됨" 표시가 남는다. */
	@Transactional
	public void submitReview(Long userId, Long storeId, int rating, String content) {
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

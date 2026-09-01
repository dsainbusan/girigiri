package net.dsa.girigiri.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dsa.girigiri.domain.dto.MyReviewRowDto;
import net.dsa.girigiri.domain.dto.ReviewRowDto;
import net.dsa.girigiri.domain.entity.ReviewEntity;
import net.dsa.girigiri.domain.entity.ReviewSummaryEntity;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.domain.entity.UserEntity;
import net.dsa.girigiri.repository.ReservationRepository;
import net.dsa.girigiri.repository.ReviewRepository;
import net.dsa.girigiri.repository.ReviewSummaryRepository;
import net.dsa.girigiri.repository.StoreRepository;
import net.dsa.girigiri.repository.UserRepository;
import net.dsa.girigiri.util.FileStorageUtil;
import net.dsa.girigiri.util.ReviewSummaryClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService {

	private final ReviewRepository reviewRepository;
	private final UserRepository userRepository;
	private final StoreRepository storeRepository;
	private final ReservationRepository reservationRepository;
	private final FileStorageUtil fileStorageUtil;
	// 추가됨 (강노은) — 왜: "AI 리뷰 요약" 기능용. summarize()는 별도 클래스(ReviewSummaryClient)로
	// 뗐다 — 이유는 그 클래스 상단 주석 참고(GeminiClient는 송채현 담당이라 안 건드림).
	private final ReviewSummaryRepository reviewSummaryRepository;
	private final ReviewSummaryClient reviewSummaryClient;

	private static final String REVIEW_IMAGE_SUBDIR = "reviews";

	// 추가됨 (강노은) — 왜: 리뷰가 너무 적으면(1~2개) "요약"이라 부르기도 애매하고 AI 호출만
	// 낭비라, 표 요구사항 그대로 10건 이상부터 요약 섹션을 노출한다.
	private static final int MIN_REVIEWS_FOR_SUMMARY = 10;
	// 요약에 넣는 리뷰 본문은 최근 것 위주로 이 개수만큼만 — 리뷰가 수백 개인 인기 매장이어도
	// 프롬프트가 무한정 길어지지 않게(토큰 비용/지연 방지) 상한을 둔다.
	private static final int MAX_REVIEWS_FOR_SUMMARY_PROMPT = 30;

	// ReservationEntity.status의 "픽업완료" 값. 리뷰는 이 상태의 예약이 있어야 쓸 수 있다.
	private static final String RESERVATION_STATUS_PICKED = "picked";

	/**
	 * 추가됨 (강노은) — 왜: "이용해본 사람만 리뷰를 쓸 수 있게" — 그 가게에서 예약 후 픽업까지 완료한
	 * 이력이 하나라도 있어야 true. 이미 리뷰를 쓴 사람의 "수정"은 이 체크를 다시 거치지 않는다(작성
	 * 시점에 이미 검증됐음 — submitReview 참고).
	 * (2026-08-26) 현재는 StoreDetailController에서 이 메서드 대신 canReview=true를 임시로 박아둔
	 * 상태(예약·픽업 플로우 테스트 전)라 실제로는 호출되지 않지만, 나중에 되돌릴 때를 위해 남겨둔다.
	 */
	public boolean canWriteReview(Long userId, Long storeId) {
		if (userId == null) {
			return false;
		}
		return reservationRepository.existsByUserIdAndStoreIdAndStatus(userId, storeId, RESERVATION_STATUS_PICKED);
	}

	public List<ReviewRowDto> getReviews(Long storeId, Long currentUserId, String role) {
		List<ReviewEntity> reviews = reviewRepository.findAll().stream()
				.filter(r -> storeId.equals(r.getStoreId()))
				.sorted(Comparator.comparing(ReviewEntity::getCreatedAt,
						Comparator.nullsLast(Comparator.reverseOrder())))
				.toList();
		
		Map<Long, String> nicknameByUserId = userRepository.findAllById(
						reviews.stream().map(ReviewEntity::getUserId).distinct().toList())
				.stream()
				.collect(Collectors.toMap(
						UserEntity::getId,
						u -> u.getNickname() == null ? "익명" : u.getNickname()
				));
		
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
				.collect(Collectors.toMap(
						StoreEntity::getId,
						StoreEntity::getStoreName
				));
		
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
				.average()
				.orElse(0);
	}
	
	public int getReviewCount(Long storeId) {
		return (int) reviewRepository.findAll().stream()
				.filter(r -> storeId.equals(r.getStoreId()))
				.count();
	}

	/**
	 * 추가됨 (강노은) — 왜: "AI 리뷰 요약" — 리뷰가 {@link #MIN_REVIEWS_FOR_SUMMARY}건 이상인
	 * 가게만 요약을 보여준다. 캐시(ReviewSummaryEntity)에 저장된 리뷰 개수와 지금 리뷰 개수가
	 * 같으면 캐시를 그대로 돌려주고(=새 리뷰가 안 생겼으면 Gemini를 다시 안 부름), 다르면 새로
	 * 생성해서 캐시를 갱신한다. Gemini 호출이 실패해도(설정 안 됨/일시 오류) 화면이 깨지면 안
	 * 되니, 그 경우 예전 캐시가 있으면 그거라도 보여주고 없으면 조용히 empty를 돌려준다.
	 */
	@Transactional
	public Optional<String> getReviewSummary(Long storeId) {
		int reviewCount = getReviewCount(storeId);
		if (reviewCount < MIN_REVIEWS_FOR_SUMMARY) {
			return Optional.empty();
		}

		Optional<ReviewSummaryEntity> cached = reviewSummaryRepository.findByStoreId(storeId);
		if (cached.isPresent() && cached.get().getReviewCountAtSummary() == reviewCount) {
			return Optional.of(cached.get().getSummary());
		}

		Optional<String> fresh = reviewSummaryClient.summarize(buildSummaryPrompt(storeId));
		if (fresh.isEmpty()) {
			log.warn("> [ReviewService] 리뷰 요약 생성 실패 - storeId={}, 캐시 폴백 사용", storeId);
			return cached.map(ReviewSummaryEntity::getSummary); // 예전 캐시라도 있으면 그거라도
		}

		ReviewSummaryEntity entity = cached.orElseGet(() -> ReviewSummaryEntity.builder().storeId(storeId).build());
		entity.setSummary(fresh.get());
		entity.setReviewCountAtSummary(reviewCount);
		entity.setGeneratedAt(LocalDateTime.now());
		reviewSummaryRepository.save(entity);

		return fresh;
	}

	/** 최근 리뷰 위주로 "별점 - 내용" 줄글을 만들어 요약 프롬프트에 넣는다. 내용 없는(별점만) 리뷰는 건너뛴다. */
	private String buildSummaryPrompt(Long storeId) {
		return reviewRepository.findAll().stream()
				.filter(r -> storeId.equals(r.getStoreId()))
				.filter(r -> r.getContent() != null && !r.getContent().isBlank())
				.sorted(Comparator.comparing(ReviewEntity::getCreatedAt,
						Comparator.nullsLast(Comparator.reverseOrder())))
				.limit(MAX_REVIEWS_FOR_SUMMARY_PROMPT)
				.map(r -> "- (" + (r.getRating() == null ? 0 : r.getRating()) + "점) " + r.getContent())
				.collect(Collectors.joining("\n"));
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
	 * 사용자당 매장 1개에 리뷰 1개만 작성할 수 있으며,
	 * 이미 작성한 리뷰가 있으면 내용을 수정한다.
	 *
	 * 새 사진을 업로드하면 기존 사진을 삭제하고 새 사진으로 교체한다.
	 * removeImage가 true이면 기존 사진을 삭제한다.
	 * 둘 다 없으면 기존 사진을 그대로 유지한다.
	 */
	@Transactional
	public boolean submitReview(Long userId, Long storeId, int rating, String content,
	                            MultipartFile imagePhoto, boolean removeImage) {
		
		int clampedRating = Math.max(1, Math.min(5, rating));
		
		Optional<ReviewEntity> existing = getMyReview(userId, storeId);
		
		boolean isNew = existing.isEmpty();
		
		ReviewEntity review = existing.orElseGet(() -> ReviewEntity.builder()
				.userId(userId)
				.storeId(storeId)
				.build());
		
		if (existing.isPresent()) {
			review.setEdited(true);
		}
		
		review.setRating(clampedRating);
		review.setContent(content == null ? "" : content.trim());
		
		// 새 사진을 업로드한 경우
		if (imagePhoto != null && !imagePhoto.isEmpty()) {
			fileStorageUtil.deleteIfOwned(
					review.getImageUrl(),
					REVIEW_IMAGE_SUBDIR
			);
			
			review.setImageUrl(
					fileStorageUtil.store(
							imagePhoto,
							REVIEW_IMAGE_SUBDIR
					)
			);
		}
		// 새 사진은 없지만 기존 사진을 삭제한 경우
		else if (removeImage) {
			fileStorageUtil.deleteIfOwned(
					review.getImageUrl(),
					REVIEW_IMAGE_SUBDIR
			);
			
			review.setImageUrl(null);
		}
		
		reviewRepository.save(review);
		
		return isNew;
	}
	
	/** 가게 사장님은 리뷰를 볼 순 있어도 지울 순 없다 — 작성자 본인 / 관리자만 true. */
	private boolean canDelete(Long reviewUserId, Long currentUserId, String role) {
		return UserEntity.ROLE_ADMIN.equals(role)
				|| (currentUserId != null && currentUserId.equals(reviewUserId));
	}
	
	@Transactional
	public void deleteReview(Long userId, String role, Long reviewId) {
		ReviewEntity review = reviewRepository.findById(reviewId)
				.orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
						org.springframework.http.HttpStatus.NOT_FOUND,
						"리뷰를 찾을 수 없습니다: " + reviewId
				));
		
		if (!canDelete(review.getUserId(), userId, role)) {
			throw new org.springframework.web.server.ResponseStatusException(
					org.springframework.http.HttpStatus.FORBIDDEN,
					"이 리뷰를 삭제할 권한이 없습니다."
			);
		}
		
		reviewRepository.delete(review);
	}
	
	private String relativeLabel(java.time.LocalDateTime createdAt) {
		if (createdAt == null) {
			return "";
		}
		
		long days = ChronoUnit.DAYS.between(
				createdAt.toLocalDate(),
				LocalDate.now()
		);
		
		if (days <= 0) {
			return "오늘";
		}
		
		if (days == 1) {
			return "어제";
		}
		
		return days + "일 전";
	}
}
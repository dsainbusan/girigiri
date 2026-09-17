package net.dsa.girigiri.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.Badge;
import net.dsa.girigiri.domain.dto.ReviewerInfoDto;
import net.dsa.girigiri.domain.entity.NotificationEntity;
import net.dsa.girigiri.domain.entity.ReviewEntity;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.domain.entity.UserEntity;
import net.dsa.girigiri.repository.ReservationRepository;
import net.dsa.girigiri.repository.ReviewRepository;
import net.dsa.girigiri.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
	private final UserRepository userRepository;
	private final ReservationRepository reservationRepository;

	private static final String RESERVATION_STATUS_PICKED = "picked";

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

	/**
	 * 리뷰 작성자 닉네임을 눌렀을 때 보여줄 간단 정보 — 리뷰 id를 키로 돌려준다(화면에서 리뷰별로
	 * 토글 패널을 다는 게 review.id 기준이라). 전화번호·이메일 같은 민감정보는 뺀다
	 * (ReviewerInfoDto 주석 참고).
	 */
	@Transactional(readOnly = true)
	public Map<Long, ReviewerInfoDto> getReviewerInfoByReviewId(Long storeId) {
		List<ReviewEntity> reviews = reviewRepository.findAll().stream()
				.filter(r -> storeId.equals(r.getStoreId()))
				.toList();

		Map<Long, UserEntity> userById = userRepository.findAllById(
						reviews.stream().map(ReviewEntity::getUserId).distinct().toList())
				.stream()
				.collect(Collectors.toMap(UserEntity::getId, u -> u));

		Map<Long, ReviewerInfoDto> result = new HashMap<>();
		for (ReviewEntity review : reviews) {
			UserEntity user = userById.get(review.getUserId());
			if (user == null) {
				continue;
			}

			long visitCount = reservationRepository.countByUserIdAndStoreIdAndStatus(
					user.getId(), storeId, RESERVATION_STATUS_PICKED);

			Badge badge = Badge.findByCode(user.getRepresentativeBadge()).orElse(null);

			result.put(review.getId(), new ReviewerInfoDto(
					user.getNickname() == null ? "익명" : user.getNickname(),
					"가입 " + daysJoined(user) + "일째",
					visitCount,
					badge == null ? null : badge.getIcon(),
					badge == null ? null : badge.getName()
			));
		}
		return result;
	}

	/** MypageService.calculateDaysJoined와 같은 산식(가입일 포함해서 1일째부터 시작). */
	private long daysJoined(UserEntity user) {
		if (user.getCreatedAt() == null) {
			return 1;
		}
		return ChronoUnit.DAYS.between(user.getCreatedAt().toLocalDate(), LocalDate.now()) + 1;
	}

	private void assertOwnsReviewStore(Long ownerId, ReviewEntity review) {
		StoreEntity store = storeAccessService.findMyStore(ownerId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "매장 정보가 없습니다."));
		if (!store.getId().equals(review.getStoreId())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "이 리뷰에 답글을 남길 권한이 없습니다.");
		}
	}
}

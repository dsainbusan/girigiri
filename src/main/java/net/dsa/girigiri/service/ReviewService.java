package net.dsa.girigiri.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dsa.girigiri.domain.dto.MyReviewRowDto;
import net.dsa.girigiri.domain.dto.ReviewRowDto;
import net.dsa.girigiri.domain.dto.ReviewableReservationDto;
import net.dsa.girigiri.domain.entity.ReservationEntity;
import net.dsa.girigiri.domain.entity.ReviewEntity;
import net.dsa.girigiri.domain.entity.ReviewSummaryEntity;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.domain.entity.UserEntity;
import net.dsa.girigiri.exception.ReviewNotAllowedException;
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
	// 추가됨 (2026-09-08, 코드 감사) — Gemini 응답 길이를 MAX_OUTPUT_TOKENS=300과 프롬프트의
	// "120자 정도" 부탁으로만 제한하고 있어서, ReviewSummaryEntity.summary(varchar(1000))보다 긴
	// 응답이 한 번만 와도 저장 시 DataIntegrityViolationException으로 그 매장 상세 페이지 전체가
	// 500으로 죽었다 — 클래스 주석이 명시한 "실패해도 화면은 안 깨져야 한다"와 정반대로 동작하던 부분.
	// ReservationService.truncateReason/PaymentEntity.truncateFailReason과 같은 패턴으로 저장 직전에 자른다.
	private static final int SUMMARY_MAX_LENGTH = 1000;

	// ReservationEntity.status의 "픽업완료" 값. 리뷰는 이 상태의 예약이 있어야 쓸 수 있다.
	private static final String RESERVATION_STATUS_PICKED = "picked";

	// 추가됨 (2026-10-05, 사용자 요청) — "신선한 기억으로" 리뷰를 남기도록, 픽업 후 이 시간 안에만
	// 작성 자격을 준다(병합 — 강노은의 "예약당 1건" 구조 위에 문창호가 얹음). 수정에는 적용 안 함 —
	// 이미 쓴 리뷰는 언제든 고칠 수 있어야 해서.
	private static final long REVIEW_WINDOW_HOURS = 72;

	/**
	 * 변경됨 (강노은, 2026-10-01) — "매장당 리뷰 1건" → "픽업완료 예약(구매)당 1건"으로 바꾸면서
	 * canWriteReview(boolean 하나)를 대체. 이 가게에서 픽업완료했는데 아직 리뷰를 안 쓴 예약을
	 * 전부 돌려준다 — 여러 건이면 화면에서 어느 구매에 대한 리뷰인지 직접 고르게 한다(createReview
	 * 호출 시 reservationId로 지정). 빈 리스트면 "리뷰 작성" 자체를 못 띄운다.
	 * 변경됨 (2026-10-05, 문창호) — 픽업 후 REVIEW_WINDOW_HOURS(72시간)가 지난 예약은 목록에서 뺀다.
	 */
	public List<ReviewableReservationDto> getReviewableReservations(Long userId, Long storeId) {
		if (userId == null) {
			return List.of();
		}
		LocalDateTime cutoff = LocalDateTime.now().minusHours(REVIEW_WINDOW_HOURS);
		return reservationRepository.findByUserIdAndStoreIdAndStatusOrderByPickedAtDesc(userId, storeId, RESERVATION_STATUS_PICKED)
				.stream()
				.filter(r -> !reviewRepository.existsByReservationId(r.getId()))
				.filter(r -> r.getPickedAt() != null && r.getPickedAt().isAfter(cutoff))
				.map(r -> new ReviewableReservationDto(r.getId(), r.getProductName(), relativeLabel(r.getPickedAt())))
				.toList();
	}

	// 추가됨 (2026-10-05, 문창호) — 구매내역(ReservationService#toListItemDto)에서 "이 주문에 이미
	// 리뷰를 썼는지"만 가볍게 확인할 때 쓴다. getReviewableReservations처럼 목록 전체를 안 만들어도 됨.
	public boolean hasReview(Long reservationId) {
		return reservationId != null && reviewRepository.existsByReservationId(reservationId);
	}

	// 추가됨 (2026-10-05, 문창호) — 구매내역에서 "이 주문, 지금 리뷰 쓸 수 있나"를 예약 하나 단위로
	// 바로 확인할 때 쓴다(getReviewableReservations와 같은 조건 — 픽업완료·72시간 이내·아직 리뷰 없음
	// — 을 예약 1건에 대해서만 가볍게 재검사).
	public boolean canWriteReviewForReservation(Long userId, Long storeId, Long reservationId) {
		if (userId == null || reservationId == null) {
			return false;
		}
		LocalDateTime cutoff = LocalDateTime.now().minusHours(REVIEW_WINDOW_HOURS);
		return reservationRepository.findById(reservationId)
				.filter(r -> userId.equals(r.getUserId()))
				.filter(r -> storeId.equals(r.getStoreId()))
				.filter(r -> RESERVATION_STATUS_PICKED.equals(r.getStatus()))
				.filter(r -> r.getPickedAt() != null && r.getPickedAt().isAfter(cutoff))
				.filter(r -> !reviewRepository.existsByReservationId(reservationId))
				.isPresent();
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
						canDelete(r.getUserId(), currentUserId, role),
						r.getReplyContent(),
						relativeLabel(r.getReplyCreatedAt()),
						r.isReplyEdited()
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
	// 변경됨 (2026-09-08, 코드 감사) — @Transactional이 걸려있어서 Gemini 호출(초 단위로 걸릴 수 있는
	// 외부 네트워크 요청)이 진행되는 동안 DB 커넥션을 계속 붙잡고 있었다. 여기는 confirmPayment처럼
	// 락을 잡고 그 락을 유지해야 할 이유가 없다(캐시 테이블이라 store_id 유니크 제약 안에서
	// 마지막에 쓴 값이 이기면 충분) — @Transactional을 떼서 조회(findByStoreId)·외부 호출(Gemini)·
	// 저장(save)이 각자 짧은 트랜잭션으로 끝나게 한다.
	//
	// 변경됨 (강노은, 2026-09-14, QA 발견) — 왜: SYSTEM_PROMPT가 "한국어 존댓말로 요약해"라고
	// 못박아뒀는데도 실제로 한 번은 Gemini가 영어 문장("Kind owner, nice that items were left
	// before")을 돌려준 적이 있었다(DB review_summary에 그대로 저장돼 있었음, 직접 확인). 문제는
	// 그 뒤: reviewCountAtSummary가 그대로면 캐시를 검증 없이 영구적으로 재사용하는 구조라, 리뷰가
	// 하나도 안 늘어나는 한 이 이상한 영어 문장이 화면에 계속 떴다. looksLikeKoreanSummary()로
	// "그럴듯한 한국어 응답인지"를 캐시 히트/신규 응답 양쪽에 다 검증해서, 캐시가 이미 오염돼 있어도
	// 다음 조회 때 자동으로 재생성을 시도하고, 새로 받은 응답도 한국어가 아니면 저장하지 않는다.
	public Optional<String> getReviewSummary(Long storeId) {
		int reviewCount = getReviewCount(storeId);
		if (reviewCount < MIN_REVIEWS_FOR_SUMMARY) {
			return Optional.empty();
		}

		Optional<ReviewSummaryEntity> cached = reviewSummaryRepository.findByStoreId(storeId);
		boolean cacheUsable = cached.isPresent()
				&& cached.get().getReviewCountAtSummary() == reviewCount
				&& looksLikeKoreanSummary(cached.get().getSummary());
		if (cacheUsable) {
			return Optional.of(cached.get().getSummary());
		}

		Optional<String> fresh = reviewSummaryClient.summarize(buildSummaryPrompt(storeId))
				.filter(this::looksLikeKoreanSummary);
		if (fresh.isEmpty()) {
			log.warn("> [ReviewService] 리뷰 요약 생성 실패(또는 한국어가 아닌 응답) - storeId={}, 캐시 폴백 사용", storeId);
			// 예전 캐시라도 한국어로 된 정상 응답일 때만 그거라도 보여준다 — 오염된 캐시를 폴백으로
			// 다시 내보내면 안 된다.
			return cached.map(ReviewSummaryEntity::getSummary).filter(this::looksLikeKoreanSummary);
		}

		String summary = truncateSummary(fresh.get());
		ReviewSummaryEntity entity = cached.orElseGet(() -> ReviewSummaryEntity.builder().storeId(storeId).build());
		entity.setSummary(summary);
		entity.setReviewCountAtSummary(reviewCount);
		entity.setGeneratedAt(LocalDateTime.now());
		reviewSummaryRepository.save(entity);

		return Optional.of(summary);
	}

	// 추가됨 (강노은, 2026-09-14, QA 발견) — 한글 완성형 음절이 일정 개수 이상 없으면 버린다.
	// "하나라도 있으면 통과"로 처음 짰더니, ReviewSummaryClient가 토큰 예산 부족으로 중간에 잘린
	// 응답("120자 이내? Yes (83" — 한글 음절 3개뿐)까지 통과시켜서 반쪽짜리 문장이 저장되는 걸
	// 직접 재현해서 봤다. SYSTEM_PROMPT의 최소 응답인 "아직 뚜렷한 특징을 요약하기 어려워요."가
	// 한글 음절 16개라, 그보다 넉넉히 낮은 8을 기준으로 잡아 정상 응답은 다 통과시키면서 잘린
	// 조각 응답은 걸러낸다. (ReviewSummaryClient의 finishReason=MAX_TOKENS 체크와 이중 방어.)
	private static final java.util.regex.Pattern HANGUL_SYLLABLE = java.util.regex.Pattern.compile("[가-힣]");
	private static final int MIN_HANGUL_SYLLABLES_FOR_SUMMARY = 8;

	private boolean looksLikeKoreanSummary(String summary) {
		if (summary == null || summary.isBlank()) {
			return false;
		}
		java.util.regex.Matcher matcher = HANGUL_SYLLABLE.matcher(summary);
		int hangulCount = 0;
		while (matcher.find()) {
			hangulCount++;
			if (hangulCount >= MIN_HANGUL_SYLLABLES_FOR_SUMMARY) {
				return true;
			}
		}
		return false;
	}

	private String truncateSummary(String summary) {
		if (summary != null && summary.length() > SUMMARY_MAX_LENGTH) {
			log.warn("> [ReviewService] AI 리뷰 요약이 {}자를 넘어 잘랐어요 (원래 {}자)", SUMMARY_MAX_LENGTH, summary.length());
			return summary.substring(0, SUMMARY_MAX_LENGTH);
		}
		return summary;
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


	/**
	 * 강노은 (2026-10-01) — 새 리뷰 작성. reservationId가 실제로 (1) 이 유저 것이고 (2) 이 매장
	 * 픽업완료 건이며 (3) 아직 리뷰가 안 달렸는지 서버에서 다시 검증한다 — 화면은
	 * getReviewableReservations가 돌려준 것만 "리뷰 작성" 버튼으로 보여주지만, 폼을 직접 조작해
	 * 남의 예약 id로 우회 제출하는 경우까지 막아야 한다(ReviewNotAllowedException 클래스 주석 참고).
	 */
	@Transactional
	public void createReview(Long userId, Long storeId, Long reservationId, int rating, String content,
	                          MultipartFile imagePhoto) {
		ReservationEntity reservation = reservationRepository.findById(reservationId)
				.orElseThrow(() -> new ReviewNotAllowedException("예약을 찾을 수 없습니다: " + reservationId));

		if (!userId.equals(reservation.getUserId()) || !storeId.equals(reservation.getStoreId())
				|| !RESERVATION_STATUS_PICKED.equals(reservation.getStatus())) {
			throw new ReviewNotAllowedException("이 예약에는 리뷰를 쓸 수 없습니다.");
		}
		if (reviewRepository.existsByReservationId(reservationId)) {
			throw new ReviewNotAllowedException("이미 리뷰를 작성한 예약입니다.");
		}
		// 추가됨 (2026-10-05, 문창호) — getReviewableReservations가 72시간 지난 예약은 애초에 버튼을
		// 안 보여주지만, 폼을 직접 조작해 옛날 reservationId로 우회 제출하는 경우까지 막는다.
		LocalDateTime cutoff = LocalDateTime.now().minusHours(REVIEW_WINDOW_HOURS);
		if (reservation.getPickedAt() == null || !reservation.getPickedAt().isAfter(cutoff)) {
			throw new ReviewNotAllowedException("픽업 후 " + REVIEW_WINDOW_HOURS + "시간 이내에만 리뷰를 쓸 수 있어요.");
		}

		ReviewEntity review = ReviewEntity.builder()
				.userId(userId)
				.storeId(storeId)
				.reservationId(reservationId)
				.build();
		applyContent(review, rating, content, imagePhoto, false);
		reviewRepository.save(review);
	}

	/**
	 * 강노은 (2026-10-01) — 내 리뷰 수정(마이페이지 "내가 쓴 리뷰" 전용). 작성자 본인만 가능하다 —
	 * canDelete와 달리 관리자 예외가 없다(삭제는 신고 대응용으로 열어뒀지만, 수정은 원래부터
	 * "내 리뷰"일 때만 버튼이 보이던 본인 전용 기능).
	 *
	 * 새 사진을 업로드하면 기존 사진을 삭제하고 새 사진으로 교체한다.
	 * removeImage가 true이면 기존 사진을 삭제한다.
	 * 둘 다 없으면 기존 사진을 그대로 유지한다.
	 */
	@Transactional
	public void updateReview(Long userId, Long reviewId, int rating, String content,
	                          MultipartFile imagePhoto, boolean removeImage) {
		ReviewEntity review = reviewRepository.findById(reviewId)
				.orElseThrow(() -> new ReviewNotAllowedException("리뷰를 찾을 수 없습니다: " + reviewId));

		if (!userId.equals(review.getUserId())) {
			throw new ReviewNotAllowedException("이 리뷰를 수정할 권한이 없습니다.");
		}

		review.setEdited(true);
		applyContent(review, rating, content, imagePhoto, removeImage);
		reviewRepository.save(review);
	}

	private void applyContent(ReviewEntity review, int rating, String content,
	                           MultipartFile imagePhoto, boolean removeImage) {
		review.setRating(Math.max(1, Math.min(5, rating)));
		review.setContent(content == null ? "" : content.trim());

		// 새 사진을 업로드한 경우
		if (imagePhoto != null && !imagePhoto.isEmpty()) {
			fileStorageUtil.deleteIfOwned(review.getImageUrl(), REVIEW_IMAGE_SUBDIR);
			review.setImageUrl(fileStorageUtil.store(imagePhoto, REVIEW_IMAGE_SUBDIR));
		}
		// 새 사진은 없지만 기존 사진을 삭제한 경우
		else if (removeImage) {
			fileStorageUtil.deleteIfOwned(review.getImageUrl(), REVIEW_IMAGE_SUBDIR);
			review.setImageUrl(null);
		}
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
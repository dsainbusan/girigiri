package net.dsa.girigiri;

import net.dsa.girigiri.domain.entity.ReviewEntity;
import net.dsa.girigiri.domain.entity.ReviewSummaryEntity;
import net.dsa.girigiri.repository.ReservationRepository;
import net.dsa.girigiri.repository.ReviewRepository;
import net.dsa.girigiri.repository.ReviewSummaryRepository;
import net.dsa.girigiri.repository.StoreRepository;
import net.dsa.girigiri.repository.UserRepository;
import net.dsa.girigiri.service.ReviewService;
import net.dsa.girigiri.util.FileStorageUtil;
import net.dsa.girigiri.util.ReviewSummaryClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 검증(더미데이터) — "AI 리뷰 요약이 varchar(1000)을 넘기면 가게 상세 페이지가 죽는다" 버그 수정 확인.
 * DB/Gemini 둘 다 실제로 안 건드린다 — ReviewSummaryClient를 목(mock)으로 바꿔서 "Gemini가 1500자짜리
 * 요약을 돌려줬다"는 더미 상황을 만들고, ReviewService가 그걸 그대로 저장하는지(버그) 1000자로 잘라서
 * 저장하는지(수정 후)만 확인한다.
 */
class ReviewSummaryTruncationTest {

	private static final Long STORE_ID = 100L;

	@Test
	void 리뷰_요약이_1000자를_넘으면_저장_전에_잘린다() {
		ReviewRepository reviewRepository = mock(ReviewRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		StoreRepository storeRepository = mock(StoreRepository.class);
		ReservationRepository reservationRepository = mock(ReservationRepository.class);
		FileStorageUtil fileStorageUtil = mock(FileStorageUtil.class);
		ReviewSummaryRepository reviewSummaryRepository = mock(ReviewSummaryRepository.class);
		ReviewSummaryClient reviewSummaryClient = mock(ReviewSummaryClient.class);

		ReviewService reviewService = new ReviewService(reviewRepository, userRepository, storeRepository,
				reservationRepository, fileStorageUtil, reviewSummaryRepository, reviewSummaryClient);

		// 더미데이터: 요약 노출 기준(MIN_REVIEWS_FOR_SUMMARY=10)을 넘기는 리뷰 10개
		List<ReviewEntity> dummyReviews = IntStream.rangeClosed(1, 10)
				.mapToObj(i -> ReviewEntity.builder()
						.id((long) i).userId((long) i).storeId(STORE_ID).rating(5)
						.content("맛있어요 " + i).createdAt(LocalDateTime.now().minusDays(i))
						.build())
				.toList();
		when(reviewRepository.findAll()).thenReturn(dummyReviews);
		when(reviewSummaryRepository.findByStoreId(STORE_ID)).thenReturn(Optional.empty());

		// 더미데이터: Gemini가 "120자 정도로"라는 프롬프트 지시를 어기고 1500자를 돌려준 상황을 재현
		String oversizedSummary = "가".repeat(1500);
		when(reviewSummaryClient.summarize(any())).thenReturn(Optional.of(oversizedSummary));
		when(reviewSummaryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		Optional<String> result = reviewService.getReviewSummary(STORE_ID);

		// 1) 서비스가 돌려주는 값도 1000자 이하
		assertTrue(result.isPresent());
		assertTrue(result.get().length() <= 1000,
				"반환된 요약이 " + result.get().length() + "자 — DB 컬럼(1000자)을 초과하면 안 됨");

		// 2) 실제로 저장을 시도한 엔티티도 1000자 이하 (여기서 안 잘리면 DataIntegrityViolationException)
		ArgumentCaptor<ReviewSummaryEntity> captor = ArgumentCaptor.forClass(ReviewSummaryEntity.class);
		verify(reviewSummaryRepository).save(captor.capture());
		assertEquals(1000, captor.getValue().getSummary().length());
	}
}

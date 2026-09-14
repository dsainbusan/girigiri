package net.dsa.girigiri;

import net.dsa.girigiri.domain.dto.RankingData;
import net.dsa.girigiri.domain.entity.ProductEntity;
import net.dsa.girigiri.domain.entity.ReservationEntity;
import net.dsa.girigiri.domain.entity.UserBadgeEntity;
import net.dsa.girigiri.domain.entity.UserEntity;
import net.dsa.girigiri.repository.ProductRepository;
import net.dsa.girigiri.repository.ReservationRepository;
import net.dsa.girigiri.repository.StoreRepository;
import net.dsa.girigiri.repository.UserBadgeRepository;
import net.dsa.girigiri.repository.UserRepository;
import net.dsa.girigiri.service.LedgerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LedgerServiceRankingTest {

	@Mock
	private ReservationRepository reservationRepository;

	@Mock
	private ProductRepository productRepository;

	@Mock
	private StoreRepository storeRepository;

	@Mock
	private UserRepository userRepository;

	@Mock
	private UserBadgeRepository userBadgeRepository;

	@InjectMocks
	private LedgerService ledgerService;

	@Test
	@DisplayName("이번 달 절약액이 큰 순서로 랭킹이 정렬된다")
	void ranksUsersByMonthlySavingsDescending() {
		ProductEntity product = ProductEntity.builder().id(10L).originalPrice(5000).build();

		ReservationEntity r1 = ReservationEntity.builder() // 유저1: 정상가 10,000 - 결제 6,000 = 4,000원 절약
				.id(1L).userId(1L).productId(10L).storeId(20L)
				.reservedQuantity(2).totalPrice(6000).status("picked")
				.pickedAt(LocalDateTime.now()).build();
		ReservationEntity r2 = ReservationEntity.builder() // 유저2: 정상가 10,000 - 결제 1,000 = 9,000원 절약
				.id(2L).userId(2L).productId(10L).storeId(20L)
				.reservedQuantity(2).totalPrice(1000).status("picked")
				.pickedAt(LocalDateTime.now()).build();

		when(reservationRepository.findByStatusAndPickedAtBetween(eq("picked"), any(), any()))
				.thenReturn(List.of(r1, r2));
		when(productRepository.findAllById(any())).thenReturn(List.of(product));
		when(userRepository.findAllById(any())).thenReturn(List.of(
				UserEntity.builder().id(1L).nickname("유저1").build(),
				UserEntity.builder().id(2L).nickname("유저2").build()
		));

		RankingData ranking = ledgerService.buildRanking(1L);

		assertEquals(2, ranking.totalParticipants());
		assertEquals(2, ranking.topRanks().size());
		assertEquals("유저2", ranking.topRanks().get(0).nickname()); // 9,000원으로 1위
		assertEquals(1, ranking.topRanks().get(0).rank());
		assertEquals("유저1", ranking.topRanks().get(1).nickname());
		assertEquals(2, ranking.topRanks().get(1).rank());

		assertNotNull(ranking.myRank());
		assertEquals(2, ranking.myRank().rank()); // 조회 요청자는 userId=1(유저1) -> 2위
		assertTrue(ranking.myRankInTop());
	}

	@Test
	@DisplayName("절약액이 동점이면 그 금액을 더 일찍 채운(마지막 픽업이 더 이른) 사람이 위로 온다")
	void tiedSavingsRankedByEarlierAchievement() {
		ProductEntity product = ProductEntity.builder().id(10L).originalPrice(5000).build();

		// 유저1, 유저2 둘 다 4,000원 절약으로 동점 — 유저1이 유저2보다 하루 먼저 그 금액을 채웠다.
		ReservationEntity r1 = ReservationEntity.builder()
				.id(1L).userId(1L).productId(10L).storeId(20L)
				.reservedQuantity(2).totalPrice(6000).status("picked")
				.pickedAt(LocalDateTime.now().minusDays(2)).build();
		ReservationEntity r2 = ReservationEntity.builder()
				.id(2L).userId(2L).productId(10L).storeId(20L)
				.reservedQuantity(2).totalPrice(6000).status("picked")
				.pickedAt(LocalDateTime.now().minusDays(1)).build();

		when(reservationRepository.findByStatusAndPickedAtBetween(eq("picked"), any(), any()))
				.thenReturn(List.of(r1, r2));
		when(productRepository.findAllById(any())).thenReturn(List.of(product));
		when(userRepository.findAllById(any())).thenReturn(List.of(
				UserEntity.builder().id(1L).nickname("유저1").build(),
				UserEntity.builder().id(2L).nickname("유저2").build()
		));

		RankingData ranking = ledgerService.buildRanking(1L);

		assertEquals(4000, ranking.topRanks().get(0).savedAmount());
		assertEquals(4000, ranking.topRanks().get(1).savedAmount());
		assertEquals("유저1", ranking.topRanks().get(0).nickname()); // 더 일찍 픽업한 유저1이 1위
		assertEquals(1, ranking.topRanks().get(0).rank());
		assertEquals("유저2", ranking.topRanks().get(1).nickname());
		assertEquals(2, ranking.topRanks().get(1).rank());
	}

	@Test
	@DisplayName("이번 달 픽업완료 내역이 하나도 없으면 빈 랭킹을 반환한다")
	void returnsEmptyRankingWhenNoDataThisMonth() {
		when(reservationRepository.findByStatusAndPickedAtBetween(eq("picked"), any(), any()))
				.thenReturn(List.of());

		RankingData ranking = ledgerService.buildRanking(1L);

		assertTrue(ranking.topRanks().isEmpty());
		assertNull(ranking.myRank());
		assertFalse(ranking.myRankInTop());
		assertEquals(0, ranking.totalParticipants());
	}

	@Test
	@DisplayName("지난달 절약 랭킹 1·2·3위에게 RANK_1/2/3 뱃지를 지급한다")
	void awardsRankBadgesToTop3() {
		ProductEntity product = ProductEntity.builder().id(10L).originalPrice(5000).build();
		ReservationEntity r1 = ReservationEntity.builder() // 유저1: 4,000원 절약 -> 3위
				.id(1L).userId(1L).productId(10L).storeId(20L)
				.reservedQuantity(2).totalPrice(6000).status("picked")
				.pickedAt(LocalDateTime.now()).build();
		ReservationEntity r2 = ReservationEntity.builder() // 유저2: 9,000원 절약 -> 1위
				.id(2L).userId(2L).productId(10L).storeId(20L)
				.reservedQuantity(2).totalPrice(1000).status("picked")
				.pickedAt(LocalDateTime.now()).build();
		ReservationEntity r3 = ReservationEntity.builder() // 유저3: 6,000원 절약 -> 2위
				.id(3L).userId(3L).productId(10L).storeId(20L)
				.reservedQuantity(2).totalPrice(4000).status("picked")
				.pickedAt(LocalDateTime.now()).build();

		when(reservationRepository.findByStatusAndPickedAtBetween(eq("picked"), any(), any()))
				.thenReturn(List.of(r1, r2, r3));
		when(productRepository.findAllById(any())).thenReturn(List.of(product));
		when(userRepository.findAllById(any())).thenReturn(List.of(
				UserEntity.builder().id(1L).nickname("유저1").build(),
				UserEntity.builder().id(2L).nickname("유저2").build(),
				UserEntity.builder().id(3L).nickname("유저3").build()
		));
		when(userBadgeRepository.existsByUserIdAndBadgeCode(any(), any())).thenReturn(false);

		int awarded = ledgerService.awardMonthlyRankBadges(YearMonth.now().minusMonths(1));

		assertEquals(3, awarded);
		ArgumentCaptor<UserBadgeEntity> captor = ArgumentCaptor.forClass(UserBadgeEntity.class);
		verify(userBadgeRepository, times(3)).save(captor.capture());

		List<UserBadgeEntity> saved = captor.getAllValues();
		assertTrue(saved.stream().anyMatch(b -> b.getUserId().equals(2L) && b.getBadgeCode().equals("RANK_1")));
		assertTrue(saved.stream().anyMatch(b -> b.getUserId().equals(3L) && b.getBadgeCode().equals("RANK_2")));
		assertTrue(saved.stream().anyMatch(b -> b.getUserId().equals(1L) && b.getBadgeCode().equals("RANK_3")));
		// 지급 즉시 토스트로 안 보여주고, 그 유저가 다음에 가계부를 열 때 build()에서 보여줘야 하므로 notified=false
		assertTrue(saved.stream().allMatch(b -> !b.isNotified()));
	}

	@Test
	@DisplayName("이미 랭킹 뱃지를 받은 유저에게는 중복 지급하지 않는다 (스케줄러가 두 번 돌아도 안전)")
	void doesNotAwardDuplicateRankBadge() {
		ProductEntity product = ProductEntity.builder().id(10L).originalPrice(5000).build();
		ReservationEntity r1 = ReservationEntity.builder() // 유저2: 9,000원 절약 -> 1위 (이미 RANK_1 보유)
				.id(1L).userId(2L).productId(10L).storeId(20L)
				.reservedQuantity(2).totalPrice(1000).status("picked")
				.pickedAt(LocalDateTime.now()).build();

		when(reservationRepository.findByStatusAndPickedAtBetween(eq("picked"), any(), any()))
				.thenReturn(List.of(r1));
		when(productRepository.findAllById(any())).thenReturn(List.of(product));
		when(userRepository.findAllById(any())).thenReturn(List.of(
				UserEntity.builder().id(2L).nickname("유저2").build()
		));
		when(userBadgeRepository.existsByUserIdAndBadgeCode(2L, "RANK_1")).thenReturn(true);

		int awarded = ledgerService.awardMonthlyRankBadges(YearMonth.now().minusMonths(1));

		assertEquals(0, awarded);
		verify(userBadgeRepository, never()).save(any());
	}
}

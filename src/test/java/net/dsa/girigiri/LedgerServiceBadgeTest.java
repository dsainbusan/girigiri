package net.dsa.girigiri;

import net.dsa.girigiri.domain.Badge;
import net.dsa.girigiri.domain.dto.LedgerData;
import net.dsa.girigiri.domain.entity.ProductEntity;
import net.dsa.girigiri.domain.entity.ReservationEntity;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.domain.entity.UserBadgeEntity;
import net.dsa.girigiri.domain.entity.UserEntity;
import net.dsa.girigiri.repository.ProductRepository;
import net.dsa.girigiri.repository.ReservationRepository;
import net.dsa.girigiri.repository.StoreRepository;
import net.dsa.girigiri.repository.UserBadgeRepository;
import net.dsa.girigiri.repository.UserRepository;
import net.dsa.girigiri.service.LedgerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LedgerServiceBadgeTest {

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

	private UserEntity user;
	private ReservationEntity reservation1;
	private ProductEntity product1;
	private StoreEntity store1;

	@BeforeEach
	void setUp() {
		user = UserEntity.builder()
				.id(100L)
				.nickname("알뜰이")
				.savingsGoalAmount(50000)
				.representativeBadge(null)
				.build();

		reservation1 = ReservationEntity.builder()
				.id(1L)
				.userId(100L)
				.productId(10L)
				.storeId(20L)
				.productName("단팥빵 세트")
				.reservedQuantity(2)
				.totalPrice(6000)
				.status("picked")
				.reservedAt(LocalDateTime.now().minusDays(2))
				.pickedAt(LocalDateTime.now().minusDays(2).withHour(21)) // 21시 픽업 -> NIGHT_OWL 충족
				.build();

		product1 = ProductEntity.builder()
				.id(10L)
				.storeId(20L)
				.originalPrice(5000) // 정상가 5,000원 x 2 = 10,000원 -> 절약액 4,000원
				.build();

		store1 = StoreEntity.builder()
				.id(20L)
				.storeName("기리기리 제과")
				.category("베이커리")
				.build();
	}

	@Test
	@DisplayName("구제 실적이 있는 경우 RESCUE_1과 NIGHT_OWL 뱃지가 해금되고 build()에 반영된다")
	void buildWithUnlockedBadges() {
		when(userRepository.findById(100L)).thenReturn(Optional.of(user));
		when(reservationRepository.findByUserIdAndStatusInOrderByReservedAtDesc(eq(100L), any()))
				.thenReturn(List.of(reservation1));
		when(productRepository.findAllById(any())).thenReturn(List.of(product1));
		when(storeRepository.findAllById(any())).thenReturn(List.of(store1));
		when(userBadgeRepository.findByUserId(100L)).thenReturn(List.of());

		LedgerData data = ledgerService.build(100L);

		assertNotNull(data);
		assertEquals("알뜰이", data.nickname());
		assertEquals(2, data.rescuedCount());
		assertEquals(4000, data.totalSaved());

		// 환경 기여도 배너용 이번 달 수치 — 이 예약은 이번 달 안이라 누적과 값이 같아야 한다
		assertEquals(2, data.thisMonthRescuedCount());
		assertTrue(data.thisMonthCo2Kg() > 0);
		assertTrue(data.unlockedBadgeCount() >= 2); // RESCUE_1, NIGHT_OWL 해금
		assertEquals(Badge.values().length, data.totalBadgeCount());

		// RESCUE_1 뱃지 해금 상태 확인
		assertTrue(data.badges().stream()
				.filter(b -> b.code().equals("RESCUE_1"))
				.findFirst().orElseThrow().unlocked());

		// NIGHT_OWL 뱃지 해금 상태 확인
		assertTrue(data.badges().stream()
				.filter(b -> b.code().equals("NIGHT_OWL"))
				.findFirst().orElseThrow().unlocked());

		// 10,000원 절약 뱃지는 미달성 (4,000원 절약)
		assertFalse(data.badges().stream()
				.filter(b -> b.code().equals("SAVE_10K"))
				.findFirst().orElseThrow().unlocked());

		// 새로 해금된 뱃지(RESCUE_1, NIGHT_OWL)는 user_badge에 영구 기록되어야 한다
		verify(userBadgeRepository).saveAll(any());

		// 방금 새로 해금된 뱃지만 토스트용 목록에 담긴다 — 이 호출 이전엔 아무것도 없었으니 RESCUE_1, NIGHT_OWL 둘 다 포함
		assertTrue(data.newlyUnlockedBadges().stream().anyMatch(b -> b.code().equals("RESCUE_1")));
		assertTrue(data.newlyUnlockedBadges().stream().anyMatch(b -> b.code().equals("NIGHT_OWL")));

		// 방금 해금된 뱃지는 획득일이 오늘 날짜로 찍혀야 한다("OOOO.MM.DD 달성" 형식)
		String todayLabel = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy.MM.dd"));
		String rescue1EarnedLabel = data.badges().stream()
				.filter(b -> b.code().equals("RESCUE_1")).findFirst().orElseThrow().earnedDateLabel();
		assertEquals(todayLabel + " 달성", rescue1EarnedLabel);

		// 미해금 뱃지는 획득일이 없어야 한다
		assertNull(data.badges().stream()
				.filter(b -> b.code().equals("SAVE_10K")).findFirst().orElseThrow().earnedDateLabel());
	}

	@Test
	@DisplayName("이미 획득한 뱃지는 이후 조건을 더 이상 만족하지 않아도 영구적으로 해금 상태를 유지한다")
	void previouslyEarnedBadgeStaysUnlockedEvenIfConditionNoLongerMet() {
		// GOAL_HIT은 지난달엔 달성해서 영구 기록이 남아있지만, 이번 달 통계(목표 미설정 -> goalPercent 0)로는
		// 실시간 조건을 만족하지 못한다 — 그래도 계속 해금 상태여야 한다.
		when(userRepository.findById(100L)).thenReturn(Optional.of(user));
		when(reservationRepository.findByUserIdAndStatusInOrderByReservedAtDesc(eq(100L), any()))
				.thenReturn(List.of(reservation1));
		when(productRepository.findAllById(any())).thenReturn(List.of(product1));
		when(storeRepository.findAllById(any())).thenReturn(List.of(store1));
		// notified=true — 지난달에 이미 토스트로 보여줬다는 뜻(realistic fixture).
		UserBadgeEntity earnedGoalHit = UserBadgeEntity.builder()
				.id(1L).userId(100L).badgeCode("GOAL_HIT").earnedAt(LocalDateTime.now().minusMonths(1))
				.notified(true).build();
		when(userBadgeRepository.findByUserId(100L)).thenReturn(List.of(earnedGoalHit));

		LedgerData data = ledgerService.build(100L);

		assertTrue(data.badges().stream()
				.filter(b -> b.code().equals("GOAL_HIT"))
				.findFirst().orElseThrow().unlocked());

		// 이미 지난달에 땄던 GOAL_HIT은 "새로 해금"이 아니므로 토스트 목록엔 다시 안 뜬다
		assertTrue(data.newlyUnlockedBadges().stream().noneMatch(b -> b.code().equals("GOAL_HIT")));
	}

	@Test
	@DisplayName("해금된 뱃지는 대표 뱃지로 정상 설정된다")
	void setRepresentativeBadgeSuccess() {
		when(userRepository.findById(100L)).thenReturn(Optional.of(user));
		when(userBadgeRepository.existsByUserIdAndBadgeCode(100L, "RESCUE_1")).thenReturn(true);

		// RESCUE_1 뱃지는 이미 획득한 기록이 있으므로 성공
		ledgerService.updateRepresentativeBadge(100L, "RESCUE_1");

		assertEquals("RESCUE_1", user.getRepresentativeBadge());
		verify(userRepository).save(user);
	}

	@Test
	@DisplayName("미해금 뱃지를 대표 뱃지로 설정하려 하면 IllegalStateException이 발생한다")
	void setRepresentativeBadgeFailsWhenLocked() {
		when(userRepository.findById(100L)).thenReturn(Optional.of(user));
		when(userBadgeRepository.existsByUserIdAndBadgeCode(100L, "RESCUE_50")).thenReturn(false);

		// 획득 기록이 없는 뱃지(RESCUE_50)는 아직 미해금 상태
		IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
				ledgerService.updateRepresentativeBadge(100L, "RESCUE_50")
		);

		assertTrue(exception.getMessage().contains("아직 달성하지 못한 뱃지"));
		assertNull(user.getRepresentativeBadge());
		verify(userRepository, never()).save(user);
	}

	@Test
	@DisplayName("대표 뱃지를 해제(null 또는 빈값 전달)하면 representativeBadge가 null이 된다")
	void clearRepresentativeBadge() {
		user.setRepresentativeBadge("RESCUE_1");
		when(userRepository.findById(100L)).thenReturn(Optional.of(user));

		ledgerService.updateRepresentativeBadge(100L, "");

		assertNull(user.getRepresentativeBadge());
		verify(userRepository).save(user);
	}
}

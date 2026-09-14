package net.dsa.girigiri;

import net.dsa.girigiri.domain.dto.LedgerData;
import net.dsa.girigiri.domain.entity.ProductEntity;
import net.dsa.girigiri.domain.entity.ReservationEntity;
import net.dsa.girigiri.domain.entity.StoreEntity;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 절약 목표 설정 검증 (코드 감사, 2026-09-14).
 * 최소 금액(1만원) 미만은 GOAL_HIT 뱃지를 사실상 거저 따게 만들어서 서버에서 막아야 한다.
 */
@ExtendWith(MockitoExtension.class)
class LedgerServiceGoalTest {

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

	@BeforeEach
	void setUp() {
		user = UserEntity.builder().id(100L).nickname("알뜰이").build();
	}

	@Test
	@DisplayName("최소 금액(1만원) 미만으로 목표를 설정하면 저장하지 않고 TOO_LOW를 반환한다")
	void rejectsGoalBelowMinimum() {
		// 금액 검증이 유저 조회보다 먼저 일어나므로 userRepository는 아예 호출되지 않는다.
		LedgerService.GoalUpdateResult result = ledgerService.updateGoal(100L, 1000);

		assertEquals(LedgerService.GoalUpdateResult.TOO_LOW, result);
		assertNull(user.getSavingsGoalAmount());
		verify(userRepository, never()).save(any());
	}

	@Test
	@DisplayName("최소 금액 이상이면 정상 저장된다")
	void acceptsGoalAtOrAboveMinimum() {
		when(userRepository.findById(100L)).thenReturn(Optional.of(user));

		LedgerService.GoalUpdateResult result = ledgerService.updateGoal(100L, 10000);

		assertEquals(LedgerService.GoalUpdateResult.SUCCESS, result);
		assertEquals(10000, user.getSavingsGoalAmount());
		verify(userRepository).save(user);
	}

	@Test
	@DisplayName("null이나 0을 보내면 목표가 해제(null)된다")
	void clearsGoalWhenNullOrZero() {
		user.setSavingsGoalAmount(50000);
		when(userRepository.findById(100L)).thenReturn(Optional.of(user));

		LedgerService.GoalUpdateResult result = ledgerService.updateGoal(100L, null);

		assertEquals(LedgerService.GoalUpdateResult.SUCCESS, result);
		assertNull(user.getSavingsGoalAmount());
		verify(userRepository).save(user);
	}

	@Test
	@DisplayName("이번 달 절약액이 목표를 넘으면 goalPercent는 100으로 캡되고 초과분은 goalOverPercent로 따로 나온다")
	void capsGoalPercentAndTracksOverPercentSeparately() {
		// 정상가 5,000원 x 2개 = 10,000원짜리를 6,000원에 픽업 -> 4,000원 절약. 목표를 2,000원으로
		// 잡아두면(테스트 픽스처일 뿐, 실제로는 updateGoal의 최소금액 검증을 거치지 않고는 이 값이 될 수 없다)
		// 달성률은 200% -> goalPercent는 100으로 캡, goalOverPercent는 100이어야 한다.
		user.setSavingsGoalAmount(2000);
		ReservationEntity reservation = ReservationEntity.builder()
				.id(1L).userId(100L).productId(10L).storeId(20L)
				.reservedQuantity(2).totalPrice(6000).status("picked")
				.reservedAt(LocalDateTime.now()).pickedAt(LocalDateTime.now()).build();
		ProductEntity product = ProductEntity.builder().id(10L).storeId(20L).originalPrice(5000).build();
		StoreEntity store = StoreEntity.builder().id(20L).storeName("기리기리 제과").category("베이커리").build();

		when(userRepository.findById(100L)).thenReturn(Optional.of(user));
		when(reservationRepository.findByUserIdAndStatusInOrderByReservedAtDesc(eq(100L), any()))
				.thenReturn(List.of(reservation));
		when(productRepository.findAllById(any())).thenReturn(List.of(product));
		when(storeRepository.findAllById(any())).thenReturn(List.of(store));
		when(userBadgeRepository.findByUserId(100L)).thenReturn(List.of());

		LedgerData data = ledgerService.build(100L);

		assertEquals(4000, data.thisMonthSaved());
		assertEquals(100, data.goalPercent());
		assertEquals(100, data.goalOverPercent());
	}
}

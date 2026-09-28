package net.dsa.girigiri;

import net.dsa.girigiri.domain.entity.UserEntity;
import net.dsa.girigiri.repository.ReservationRepository;
import net.dsa.girigiri.repository.UserArchiveRepository;
import net.dsa.girigiri.repository.UserBadgeRepository;
import net.dsa.girigiri.repository.UserRepository;
import net.dsa.girigiri.service.MypageService;
import net.dsa.girigiri.service.StoreAccessService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

/**
 * 회원 탈퇴 처리 검증 (2026-09-22 갱신 — soft delete 정책 반영, 보미 피드백).
 *
 * 예전엔 users를 바로 hard delete하는지만 확인했는데(코드 감사, 2026-09-12), 이제는
 * (1) user_archive에 최소 식별 정보를 먼저 남기고 (2) user_badge 기록을 지운 뒤 (3) users 로우는
 * deletedAt만 채워서 저장(soft delete)하는지를 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class MypageServiceWithdrawTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private ReservationRepository reservationRepository;

	@Mock
	private StoreAccessService storeAccessService;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private UserBadgeRepository userBadgeRepository;

	@Mock
	private UserArchiveRepository userArchiveRepository;

	@InjectMocks
	private MypageService mypageService;

	@Test
	@DisplayName("탈퇴 시 user_archive에 남기고, user_badge를 지운 뒤, users는 soft delete(저장)한다")
	void withdrawArchivesBadgeThenSoftDeletesUser() {
		UserEntity user = UserEntity.builder()
				.id(100L)
				.oauthProvider("google")
				.oauthId("g-100")
				.email("withdraw-test@example.com")
				.build();
		when(userRepository.findById(100L)).thenReturn(Optional.of(user));

		mypageService.withdraw(100L);

		InOrder order = inOrder(userArchiveRepository, userBadgeRepository, userRepository);
		order.verify(userArchiveRepository).save(any());
		order.verify(userBadgeRepository).deleteByUserId(100L);

		ArgumentCaptor<UserEntity> savedUser = ArgumentCaptor.forClass(UserEntity.class);
		order.verify(userRepository).save(savedUser.capture());

		assertEquals(100L, savedUser.getValue().getId());
		assertNotNull(savedUser.getValue().getDeletedAt());
	}
}

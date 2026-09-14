package net.dsa.girigiri;

import net.dsa.girigiri.repository.ReservationRepository;
import net.dsa.girigiri.repository.UserBadgeRepository;
import net.dsa.girigiri.repository.UserRepository;
import net.dsa.girigiri.service.MypageService;
import net.dsa.girigiri.service.StoreAccessService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

/**
 * 회원 탈퇴 시 user_badge 고아 데이터가 안 남는지 검증 (코드 감사, 2026-09-12).
 * user_badge는 users를 참조하는 DB 레벨 FK가 없어서, withdraw()가 명시적으로 먼저 지워야 한다.
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

	@InjectMocks
	private MypageService mypageService;

	@Test
	@DisplayName("탈퇴 시 user_badge 기록을 먼저 지우고 users를 지운다")
	void withdrawDeletesUserBadgeBeforeUser() {
		mypageService.withdraw(100L);

		InOrder order = inOrder(userBadgeRepository, userRepository);
		order.verify(userBadgeRepository).deleteByUserId(100L);
		order.verify(userRepository).deleteById(100L);
	}
}

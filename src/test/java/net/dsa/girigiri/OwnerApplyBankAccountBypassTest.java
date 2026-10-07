package net.dsa.girigiri;

import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.repository.SocialAccountRepository;
import net.dsa.girigiri.repository.StoreRepository;
import net.dsa.girigiri.repository.UserRepository;
import net.dsa.girigiri.service.AuthService;
import net.dsa.girigiri.util.FileStorageUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 추가됨 (2026-10-07, 보안 리뷰 대응) — 회귀 테스트. AuthService#ownerApply가
 * findByOwnerId(userId)로 기존 매장을 재사용하는 구조라, 이미 승인된(APPROVED) 매장의 점주가
 * /auth/owner-apply를 다시 제출하면 계좌를 포함한 모든 정보가 운영자 재승인 없이 덮어써질 수
 * 있었다 — approvalStatus는 PENDING으로 되돌아가지만 accountStatus는 그대로 NORMAL이라, 신고
 * 없이 아무 계좌나 지급 가능 상태로 바꿀 수 있는 구멍이었다(BankAccountChangeService의 "계좌
 * 변경은 승인제" 요구사항을 완전히 우회).
 */
@ExtendWith(MockitoExtension.class)
class OwnerApplyBankAccountBypassTest {

	@Mock
	private UserRepository userRepository;
	@Mock
	private StoreRepository storeRepository;
	@Mock
	private PasswordEncoder passwordEncoder;
	@Mock
	private SocialAccountRepository socialAccountRepository;
	@Mock
	private FileStorageUtil fileStorageUtil;

	@InjectMocks
	private AuthService authService;

	private MockMultipartFile passbook() {
		return new MockMultipartFile("passbook", "p.jpg", "image/jpeg", new byte[]{1});
	}

	@Test
	void 이미_승인된_매장은_재신청으로_계좌를_바꿀_수_없다() {
		StoreEntity approvedStore = StoreEntity.builder()
				.id(1L).ownerId(100L)
				.approvalStatus(StoreEntity.STATUS_APPROVED)
				.accountStatus(StoreEntity.ACCOUNT_STATUS_NORMAL)
				.bankAccount("기존계좌").build();
		when(storeRepository.findByOwnerId(100L)).thenReturn(Optional.of(approvedStore));

		ResponseStatusException e = assertThrows(ResponseStatusException.class,
				() -> authService.ownerApply(100L, "가게", "123-45-67890", "베이커리", "서울시 중구", "02-1234-5678",
						"09:00 ~ 18:00", "신한", "공격자계좌", "공격자", passbook()));

		assertEquals(409, e.getStatusCode().value());
		// 계좌가 실제로 안 바뀌었는지도 같이 확인 — 예외만 던지고 store는 그대로여야 한다.
		assertEquals("기존계좌", approvedStore.getBankAccount());
		assertEquals(StoreEntity.ACCOUNT_STATUS_NORMAL, approvedStore.getAccountStatus());
		verify(storeRepository, never()).save(any());
	}

	@Test
	void 반려됐던_매장이_계좌와_함께_재신청하면_미등록_상태로_저장된다() {
		StoreEntity rejectedStore = StoreEntity.builder()
				.id(2L).ownerId(200L)
				.approvalStatus(StoreEntity.STATUS_REJECTED)
				.accountStatus(StoreEntity.ACCOUNT_STATUS_UNREGISTERED)
				.build();
		when(storeRepository.findByOwnerId(200L)).thenReturn(Optional.of(rejectedStore));
		when(fileStorageUtil.storePrivate(any(), eq("passbooks"))).thenReturn("passbooks/p.jpg");
		when(storeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		StoreEntity result = authService.ownerApply(200L, "가게", "123-45-67890", "베이커리", "서울시 중구", "02-1234-5678",
				"09:00 ~ 18:00", "신한", "11112222", "홍길동", passbook());

		assertEquals(StoreEntity.STATUS_PENDING, result.getApprovalStatus());
		assertEquals(StoreEntity.ACCOUNT_STATUS_UNREGISTERED, result.getAccountStatus());
		assertEquals("11112222", result.getBankAccount());
	}
}

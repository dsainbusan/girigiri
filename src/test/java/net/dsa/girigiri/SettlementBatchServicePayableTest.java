package net.dsa.girigiri;

import net.dsa.girigiri.domain.entity.SettlementEntity;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.repository.SettlementRepository;
import net.dsa.girigiri.repository.StoreRepository;
import net.dsa.girigiri.service.SettlementBatchService;
import net.dsa.girigiri.service.SettlementService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 추가됨 (2026-10-07, 계좌 보안) — "지급 완료 조건"(사용자 요구사항 "작업 방식" 항목) 단위 테스트.
 * SettlementBatchService.markPaid()가 "지급 대기(PENDING) + 계좌 정상(payable)"인 건만 PAID로
 * 바꾸는지 검증한다 — 화면 체크박스 비활성화와 별개로 서버가 다시 막아야 하는 부분
 * (settlements.html에서 비활성화해도 폼을 직접 조작하는 경우까지 막기 위함).
 */
@ExtendWith(MockitoExtension.class)
class SettlementBatchServicePayableTest {

	@Mock
	private StoreRepository storeRepository;
	@Mock
	private SettlementRepository settlementRepository;
	@Mock
	private SettlementService settlementService;

	@InjectMocks
	private SettlementBatchService batchService;

	private SettlementEntity pendingSettlement(Long id, Long storeId, String bankAccountSnapshot) {
		return SettlementEntity.builder()
				.id(id).storeId(storeId)
				.periodStart(LocalDate.now()).periodEnd(LocalDate.now().plusDays(6))
				.status(SettlementEntity.STATUS_PENDING)
				.payout(10000)
				.bankAccount(bankAccountSnapshot)
				.confirmedAt(LocalDateTime.now())
				.scheduledPayoutDate(LocalDate.now())
				.build();
	}

	@Test
	void 스냅샷_계좌가_있으면_매장_현재_상태와_무관하게_지급된다() {
		// 확정 당시엔 정상이어서 스냅샷이 찍혔는데, 그 뒤 매장이 변경 심사 중으로 바뀐 상황을 가정.
		SettlementEntity s = pendingSettlement(1L, 100L, "암호화된계좌값");
		StoreEntity store = StoreEntity.builder().id(100L).accountStatus(StoreEntity.ACCOUNT_STATUS_UNDER_REVIEW).build();
		when(settlementRepository.findAllById(List.of(1L))).thenReturn(List.of(s));
		when(storeRepository.findAllById(anyList())).thenReturn(List.of(store));

		int paid = batchService.markPaid(List.of(1L), "메모", null);

		assertEquals(1, paid);
		assertEquals(SettlementEntity.STATUS_PAID, s.getStatus());
	}

	@Test
	void 스냅샷이_없고_매장_계좌도_정상이_아니면_지급에서_제외된다() {
		SettlementEntity s = pendingSettlement(2L, 200L, null);
		StoreEntity store = StoreEntity.builder().id(200L).accountStatus(StoreEntity.ACCOUNT_STATUS_UNREGISTERED).build();
		when(settlementRepository.findAllById(List.of(2L))).thenReturn(List.of(s));
		when(storeRepository.findAllById(anyList())).thenReturn(List.of(store));

		int paid = batchService.markPaid(List.of(2L), "메모", null);

		assertEquals(0, paid);
		assertEquals(SettlementEntity.STATUS_PENDING, s.getStatus());
	}

	@Test
	void 스냅샷이_없어도_매장_계좌가_지금_정상이면_지급된다() {
		// 확정 당시엔 미등록이라 스냅샷이 없었는데, 그 뒤 계좌 등록이 승인돼 지금은 정상인 상황.
		SettlementEntity s = pendingSettlement(3L, 300L, null);
		StoreEntity store = StoreEntity.builder().id(300L).accountStatus(StoreEntity.ACCOUNT_STATUS_NORMAL).build();
		when(settlementRepository.findAllById(List.of(3L))).thenReturn(List.of(s));
		when(storeRepository.findAllById(anyList())).thenReturn(List.of(store));

		int paid = batchService.markPaid(List.of(3L), "메모", null);

		assertEquals(1, paid);
		assertEquals(SettlementEntity.STATUS_PAID, s.getStatus());
	}

	@Test
	void PENDING이_아닌_건은_계좌가_정상이어도_그대로_둔다() {
		SettlementEntity s = pendingSettlement(4L, 400L, "암호화된계좌값");
		s.setStatus(SettlementEntity.STATUS_CARRIED);
		when(settlementRepository.findAllById(List.of(4L))).thenReturn(List.of(s));
		when(storeRepository.findAllById(anyList())).thenReturn(List.of());

		int paid = batchService.markPaid(List.of(4L), "메모", null);

		assertEquals(0, paid);
		assertEquals(SettlementEntity.STATUS_CARRIED, s.getStatus());
	}
}

package net.dsa.girigiri;

import net.dsa.girigiri.domain.dto.PayoutStatementDto;
import net.dsa.girigiri.domain.entity.SettlementEntity;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.repository.SettlementRepository;
import net.dsa.girigiri.service.PayoutStatementService;
import net.dsa.girigiri.util.PayoutStatementPdfGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 추가됨 (2026-10-07) — 점주용 지급 명세서(PayoutStatementService). DB 없이 도는 단위 테스트:
 * 내 매장 + 지급 완료 건만 열리는지, 금액은 확정 기록 그대로인지, 계좌는 스냅샷·마스킹인지 확인한다.
 */
class PayoutStatementServiceTest {

	private final SettlementRepository settlementRepository = mock(SettlementRepository.class);
	private final PayoutStatementService service = new PayoutStatementService(settlementRepository);

	private final StoreEntity myStore = StoreEntity.builder()
			.id(1L).storeName("다이스키 베이커리").businessNumber("123-45-67890")
			.bankName("신한").bankAccount("110987654321").accountHolder("새예금주")
			.build();

	private SettlementEntity settlement(Long storeId, String status, String snapshotAccount) {
		return SettlementEntity.builder()
				.id(123L).storeId(storeId)
				.periodStart(LocalDate.of(2026, 9, 21)).periodEnd(LocalDate.of(2026, 9, 27))
				.gross(37_600).refund(5_400).netAmount(32_200).commissionRate(5).commission(1_610)
				.weekAmount(30_590).carriedIn(4_200).payout(34_790)
				.status(status)
				.confirmedAt(LocalDateTime.of(2026, 9, 28, 0, 0))
				.scheduledPayoutDate(LocalDate.of(2026, 9, 30))
				.paidAt(LocalDateTime.of(2026, 9, 30, 10, 12))
				.bankName(snapshotAccount == null ? null : "국민").bankAccount(snapshotAccount)
				.accountHolder(snapshotAccount == null ? null : "옛예금주")
				.build();
	}

	@Test
	void 지급완료된_내_매장_정산은_확정기록_그대로_명세서가_된다() {
		when(settlementRepository.findById(123L)).thenReturn(Optional.of(settlement(1L, SettlementEntity.STATUS_PAID, "1101234567")));

		PayoutStatementDto dto = service.getPaidStatement(myStore, 123L);

		assertEquals("PS-20260921-000123", dto.statementNo());
		assertEquals(34_790, dto.payout());
		assertEquals(4_200, dto.carriedIn());
		// 확정 시점 계좌 스냅샷을 쓰고(지금 매장 계좌 아님), 번호는 마스킹만
		assertEquals("국민", dto.bankName());
		assertEquals("110***4567", dto.maskedAccount());
		assertEquals("옛예금주", dto.accountHolder());
	}

	@Test
	void 계좌_스냅샷이_없으면_지금_매장_계좌로_보여준다() {
		when(settlementRepository.findById(123L)).thenReturn(Optional.of(settlement(1L, SettlementEntity.STATUS_PAID, null)));

		PayoutStatementDto dto = service.getPaidStatement(myStore, 123L);

		assertEquals("신한", dto.bankName());
		assertEquals("110*****4321", dto.maskedAccount());
	}

	@Test
	void 다른_매장_정산은_404() {
		when(settlementRepository.findById(123L)).thenReturn(Optional.of(settlement(2L, SettlementEntity.STATUS_PAID, "1101234567")));

		assertThrows(ResponseStatusException.class, () -> service.getPaidStatement(myStore, 123L));
	}

	@Test
	void 아직_지급_전이면_404() {
		when(settlementRepository.findById(123L)).thenReturn(Optional.of(settlement(1L, SettlementEntity.STATUS_PENDING, "1101234567")));

		assertThrows(ResponseStatusException.class, () -> service.getPaidStatement(myStore, 123L));
	}

	@Test
	void 명세서_PDF가_만들어진다() throws Exception {
		when(settlementRepository.findById(123L)).thenReturn(Optional.of(settlement(1L, SettlementEntity.STATUS_PAID, "1101234567")));

		byte[] pdf = PayoutStatementPdfGenerator.generate(service.getPaidStatement(myStore, 123L));

		assertTrue(pdf.length > 1000);
		assertEquals("%PDF", new String(pdf, 0, 4));
	}
}

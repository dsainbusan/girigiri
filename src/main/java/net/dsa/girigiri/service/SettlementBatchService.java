package net.dsa.girigiri.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dsa.girigiri.domain.entity.SettlementEntity;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.repository.SettlementRepository;
import net.dsa.girigiri.repository.StoreRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 주간 정산 배치 — WBS 2.0 (문창호, 2026-09-01).
 *
 * "정산 확정"(계산·레코드 생성)과 "지급"(실제 송금)을 분리한다:
 *  - confirmWeek(): 매주 월 00:00 SettlementScheduler가 호출. 지난 주(월~일) 매장별 정산액을 계산해
 *    SettlementEntity를 만든다(status=PENDING). 지급액이 MIN_PAYOUT 미만이면 status=CARRIED로 두고
 *    다음 주 정산에 합산한다.
 *  - markPaid(): 은행 대량이체로 송금한 뒤 "지급 완료" 처리.
 *
 * ⚠️ 지급 처리 화면(지급 대기 목록 / 이체 목록 Excel 다운로드 / 지급 완료 버튼)은 슈퍼어드민(송보미)
 *    영역이라 여기서 만들지 않았다. 송보미가 /superadmin 화면에서 아래만 호출하면 붙는다:
 *      - 지급 대기 목록      : SettlementRepository.findByStatusOrderByScheduledPayoutDateAsc("PENDING")
 *      - 이체 목록 Excel     : SettlementTransferExcelGenerator.generate(...) (매장 계좌 = StoreEntity.bank*)
 *      - 지급 완료 처리      : this.markPaid(선택된 id 리스트, 이체 메모)
 *    실제 계좌 이체 자동화(펌뱅킹/지급대행 API)는 범위 밖 — 운영자가 이체 목록(Excel)을 받아
 *    은행 기업뱅킹 대량이체로 직접 보낸다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementBatchService {

	private static final String STORE_ROLE_OWNER = "OWNER";
	private static final int PAYOUT_BUSINESS_DAYS = 2;   // 확정일 + 영업일 2일 = 지급 예정일

	private final StoreRepository storeRepository;
	private final SettlementRepository settlementRepository;
	private final SettlementService settlementService;

	/** 마지막으로 끝난(일요일까지 지난) 주의 월요일. 스케줄러·데모용 수동 실행에서 씀. */
	public LocalDate lastCompletedWeekStart() {
		return LocalDate.now()
				.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
				.minusWeeks(1);
	}

	/**
	 * 지정한 주(weekStart=월요일 ~ +6일=일요일) 정산을 확정한다. 승인된 매장 전체를 훑어 레코드를 만든다.
	 * 이미 만든 매장은 건너뛴다(멱등). 만든 레코드 수를 돌려준다.
	 */
	@Transactional
	public int confirmWeek(LocalDate weekStart) {
		LocalDate periodStart = weekStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
		LocalDate periodEnd = periodStart.plusDays(6);
		LocalDateTime windowStart = periodStart.atStartOfDay();
		LocalDateTime windowEnd = periodStart.plusDays(7).atStartOfDay();
		LocalDateTime now = LocalDateTime.now();
		LocalDate payoutDate = addBusinessDays(now.toLocalDate(), PAYOUT_BUSINESS_DAYS);

		int created = 0;
		for (StoreEntity store : storeRepository.findByApprovalStatusAndRole(StoreEntity.STATUS_APPROVED, STORE_ROLE_OWNER)) {
			if (settlementRepository.existsByStoreIdAndPeriodStart(store.getId(), periodStart)) {
				continue;
			}
			SettlementService.Agg agg = settlementService.aggregate(store, windowStart, windowEnd);

			List<SettlementEntity> carried =
					settlementRepository.findByStoreIdAndStatus(store.getId(), SettlementEntity.STATUS_CARRIED);
			long carriedIn = carried.stream().mapToLong(SettlementEntity::getWeekAmount).sum();
			long total = agg.weekAmount() + carriedIn;

			// 이번 주 거래도 없고 이월분도 없으면 만들 게 없다 (0원 레코드 안 만듦)
			if (agg.weekAmount() <= 0 && carriedIn <= 0) {
				continue;
			}

			boolean pay = total >= SettlementService.MIN_PAYOUT && total > 0;

			// 추가됨 (2026-10-07, 계좌 보안) — 확정 시점에 계좌가 정상(NORMAL)이면 그 계좌를 스냅샷으로
			// 같이 저장한다("확정된 건은 기존 계좌 유지" 요구사항, SettlementEntity.isPayable 참고).
			// 정상이 아니면(미등록/변경 심사 중) 스냅샷을 안 남긴다 — 나중에 계좌가 정상화되면 그때
			// 가서 지금 시점의 매장 계좌를 쓰도록 비워둔다(SettlementEntity.isPayable의 폴백 분기).
			boolean accountNormalNow = store.isAccountPayable();

			SettlementEntity s = SettlementEntity.builder()
					.storeId(store.getId())
					.periodStart(periodStart)
					.periodEnd(periodEnd)
					.gross(agg.gross())
					.refund(agg.refund())
					.netAmount(agg.net())
					.commissionRate(SettlementService.COMMISSION_RATE_PERCENT)
					.commission(agg.commission())
					.weekAmount(agg.weekAmount())
					.carriedIn(carriedIn)
					.payout(pay ? total : 0)
					.status(pay ? SettlementEntity.STATUS_PENDING : SettlementEntity.STATUS_CARRIED)
					.confirmedAt(now)
					.scheduledPayoutDate(payoutDate)
					.bankName(accountNormalNow ? store.getBankName() : null)
					.bankAccount(accountNormalNow ? store.getBankAccount() : null)
					.accountHolder(accountNormalNow ? store.getAccountHolder() : null)
					.build();
			settlementRepository.save(s);
			created++;

			if (pay && !carried.isEmpty()) {
				for (SettlementEntity c : carried) {
					c.setStatus(SettlementEntity.STATUS_ROLLED);
					c.setMergedIntoId(s.getId());
				}
				settlementRepository.saveAll(carried);
			}
		}
		log.info("> [정산 확정] {} ~ {} : {}건 생성", periodStart, periodEnd, created);
		return created;
	}

	/** 슈퍼어드민 "지급 완료" 처리. PENDING인 것만 PAID로 바꾼다. 처리된 건수를 돌려준다. */
	@Transactional
	public int markPaid(List<Long> settlementIds, String memo) {
		return markPaid(settlementIds, memo, null);
	}

	/**
	 * receiptUrl 버전 — 2026-10-07 추가. 이체 확인증(스크린샷) 경로를 같이 받아 선택한 건 전부에
	 * 동일하게 붙인다("한 번의 대량이체 = 한 장의 이체 확인증"이라 여러 매장 건이 같은 파일을
	 * 공유한다, transferMemo와 동일한 전제). receiptUrl이 null이면(파일 첨부 안 함) 기존 값을
	 * 그대로 두고 건드리지 않는다 — 재시도 시 이미 붙은 영수증을 실수로 지우지 않기 위해서다.
	 */
	@Transactional
	public int markPaid(List<Long> settlementIds, String memo, String receiptUrl) {
		if (settlementIds == null || settlementIds.isEmpty()) {
			return 0;
		}
		LocalDateTime now = LocalDateTime.now();
		List<SettlementEntity> targets = settlementRepository.findAllById(settlementIds);
		// 추가됨 (2026-10-07, 계좌 보안) — "지급 대기 + 계좌 정상"인 건만 업데이트(요구사항 5). 화면에서
		// 체크박스를 비활성화해도 폼을 직접 조작해서 비정상 계좌 건의 id를 끼워 넣을 수 있으니, 여기서
		// 한 번 더 막는다 — SettlementEntity.isPayable()이 스냅샷 우선, 없으면 현재 매장 계좌 상태를 본다.
		Map<Long, StoreEntity> storesById = storeRepository.findAllById(
				targets.stream().map(SettlementEntity::getStoreId).distinct().toList())
				.stream().collect(Collectors.toMap(StoreEntity::getId, store -> store));

		int paid = 0;
		for (SettlementEntity s : targets) {
			if (!SettlementEntity.STATUS_PENDING.equals(s.getStatus())) {
				continue;
			}
			if (!s.isPayable(storesById.get(s.getStoreId()))) {
				continue;
			}
			s.setStatus(SettlementEntity.STATUS_PAID);
			s.setPaidAt(now);
			s.setTransferMemo(memo != null && !memo.isBlank() ? memo.trim() : "은행 대량이체");
			if (receiptUrl != null) {
				s.setTransferReceiptUrl(receiptUrl);
			}
			paid++;
		}
		log.info("> [정산 지급] {}건 지급 완료 처리", paid);
		return paid;
	}

	/** 주말(토·일)을 건너뛰고 영업일 n일 뒤 날짜. (공휴일은 고려 안 함 — 데모 범위) */
	static LocalDate addBusinessDays(LocalDate from, int businessDays) {
		LocalDate d = from;
		int added = 0;
		while (added < businessDays) {
			d = d.plusDays(1);
			if (d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY) {
				added++;
			}
		}
		return d;
	}
}

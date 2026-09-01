package net.dsa.girigiri.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 주간 정산 확정 스케줄러 — WBS 2.0 (문창호, 2026-09-01).
 *
 * 매주 월요일 00:00에 지난 주(월~일) 정산을 확정한다(SettlementBatchService.confirmWeek).
 * "언제 돌릴지"만 여기서 정하고 로직은 전부 배치 서비스에 있다 (NoShowScheduler와 같은 구조).
 * 실제 송금은 슈퍼어드민이 "정산 지급" 화면에서 수동으로 한다 (/superadmin/settlements).
 * (@EnableScheduling은 GirigiriApplication에 이미 있음)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementScheduler {

	private final SettlementBatchService settlementBatchService;

	// 매주 월요일 00:00 (초 분 시 일 월 요일)
	@Scheduled(cron = "0 0 0 * * MON")
	public void confirmLastWeek() {
		int created = settlementBatchService.confirmWeek(settlementBatchService.lastCompletedWeekStart());
		log.info("> [SettlementScheduler] 주간 정산 확정 {}건", created);
	}
}

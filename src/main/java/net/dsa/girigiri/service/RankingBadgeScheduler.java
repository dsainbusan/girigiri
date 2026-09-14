package net.dsa.girigiri.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.YearMonth;

/**
 * 절약 랭킹 TOP3 뱃지 지급 스케줄러 — WBS 6.0 (문창호, 2026-09-12).
 *
 * 매달 1일 00:00에 "지난달" 절약 랭킹 1·2·3위에게 랭킹 뱃지(RANK_1/2/3)를 지급한다
 * (LedgerService.awardMonthlyRankBadges). "언제 돌릴지"만 여기서 정하고 로직은 전부
 * LedgerService에 있다 (SettlementScheduler와 같은 구조).
 * (@EnableScheduling은 GirigiriApplication에 이미 있음)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RankingBadgeScheduler {

	private final LedgerService ledgerService;

	// 매달 1일 00:00 (초 분 시 일 월 요일)
	@Scheduled(cron = "0 0 0 1 * *")
	public void awardLastMonthTop3() {
		YearMonth lastMonth = YearMonth.now().minusMonths(1);
		int awarded = ledgerService.awardMonthlyRankBadges(lastMonth);
		log.info("> [RankingBadgeScheduler] {} 랭킹 TOP3 뱃지 {}건 지급", lastMonth, awarded);
	}
}

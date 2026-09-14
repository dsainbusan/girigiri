package net.dsa.girigiri.domain.dto;

import java.time.YearMonth;
import java.util.List;

/**
 * 이번 달 절약 랭킹 (WBS 6.0, 문창호). LedgerService.buildRanking()에서 생성.
 * 매달 절약액 기준으로 새로 집계한다 — 누적 기준으로 하면 초반 가입자가 계속 1위라 재미가 없어서
 * 히어로 카드의 "이번 달 절약"과 같은 기준(월간)으로 맞췄다.
 */
public record RankingData(
		List<RankingRow> topRanks,   // 상위 10명
		RankingRow myRank,           // 내 순위(이번 달 구제 실적이 없으면 null)
		boolean myRankInTop,         // myRank가 topRanks 안에도 포함돼 있는지(중복 표시 방지용)
		int totalParticipants,       // 이번 달 랭킹에 이름을 올린 전체 인원
		YearMonth month
) {
	public record RankingRow(int rank, Long userId, String nickname, int savedAmount) {}
}

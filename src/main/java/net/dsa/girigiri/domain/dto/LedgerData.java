package net.dsa.girigiri.domain.dto;

import java.util.List;

/**
 * 마이페이지 절약 가계부 화면·CSV·PDF 공용 집계 결과 (문창호, WBS 6.0).
 * LedgerService.build() 하나에서 나온다 — 화면·파일 숫자가 항상 일치하도록.
 * 데이터 소스는 이 사용자의 픽업 완료(status=picked) 예약 전체 — "결제만 하고 안 찾아간 것"은
 * 절약이 아니라 노쇼라 집계에서 뺀다.
 */
public record LedgerData(
		String nickname,           // CSV/PDF 인사말·파일명에 사용

		// 히어로 카드 — 이번 달 요약
		int thisMonthSaved,
		int deltaAmount,           // 전월 대비 증감액(이번달 − 전월, 음수 가능). 전월 데이터 자체가 없어도 0.
		Integer deltaPercent,      // 전월 대비 %(전월 절약 0원이면 비교 불가 → null)
		int totalSaved,            // 누적 절약액
		int rescueRatePercent,     // 절약률 = 누적 절약액 ÷ 누적 정상가 합 × 100
		Integer goalAmount,        // 이번 달 절약 목표(미설정이면 null)
		int goalPercent,           // 목표 대비 달성률(게이지 폭·뱃지 판정용, 100 캡)
		int goalOverPercent,       // 100% 초과분만(예: 123% 달성 -> 23). 초과 달성 없으면 0.
		List<MonthPoint> monthly,  // 최근 6개월 추이

		// 히어로 카드 바로 아래 "환경 기여도 배너"용 — 누적이 아니라 이번 달 기준(2026-09-12 추가).
		int thisMonthRescuedCount,
		double thisMonthCo2Kg,

		// 탭: 내역
		List<HistoryRow> history,

		// 탭: 분석
		List<CategoryRow> categories,

		// 등급(티어) 계산 + CSV/PDF 요약용 — 누적(all-time) 기준. 화면에 직접 노출되는 숫자는
		// 위 thisMonthRescuedCount/thisMonthCo2Kg(환경 기여도 배너) 쪽이다.
		int rescuedCount,          // 구제한 음식 개수(수량 합, 누적)
		double co2Kg,              // CO2 절감(누적)
		String tier,               // 새싹/브론즈/실버/골드/플래티넘
		String tierEmoji,
		int nextTierAt,            // 다음 등급까지 몇 개 남았는지 개수(최고 등급이면 -1)

		// 수집형 뱃지 및 업적 도감
		BadgeDto representativeBadge,  // 현재 장착된 대표 뱃지 (미설정 시 null)
		List<BadgeDto> badges,         // 전체 뱃지 도감 목록
		int unlockedBadgeCount,        // 획득한 뱃지 수
		int totalBadgeCount,           // 전체 뱃지 수

		// 방금 이 build() 호출에서 "처음" 조건을 만족해 새로 해금된 뱃지 — 평소엔 빈 리스트.
		// 화면에서 이게 비어있지 않으면 "🎉 새 뱃지 획득!" 토스트를 띄운다(ledger.html 참고).
		List<BadgeDto> newlyUnlockedBadges
) {
	/** 월별 막대 한 칸. heightPercent는 6개월 중 최댓값 대비 비율(컨트롤러/서비스에서 미리 계산). */
	public record MonthPoint(String label, int saved, int heightPercent, boolean isCurrent, String monthKey) {
		public MonthPoint(String label, int saved, int heightPercent) {
			this(label, saved, heightPercent, false, "");
		}
	}

	/** 구매·절약 내역 한 줄. */
	public record HistoryRow(String dateLabel, String storeName, String productName,
	                         int quantity, int originalTotal, int paidTotal, int saved) {
		public int discountPercent() {
			return originalTotal > 0 ? (int) Math.round(100.0 * saved / originalTotal) : 0;
		}
	}

	/** 카테고리별 소비·절약 한 줄. spent = 그 카테고리에서 실제로 낸 돈(결제액 합). */
	public record CategoryRow(String category, int spent, int saved, int percent) {}
}

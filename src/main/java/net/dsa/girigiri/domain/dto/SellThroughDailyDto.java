package net.dsa.girigiri.domain.dto;

/**
 * 통계 대시보드 "마감 상품 소진 현황"(최근 7일) 막대 1개 (2026-09-30).
 * SKU(상품 건수) 기준 — 수량(재고) 기준이 아니다(사용자 확인 완료). registeredHeightPercent/
 * soldHeightPercent는 같은 축(그 주 최댓값 registeredCount 대비 %)으로 계산해서, 막대 하나 안에
 * 판매(아래)/미판매(위) 두 구간을 flex-grow로 쌓을 수 있게 한다 — storeView 대시보드의
 * DailySalesBarDto와 동일한 패턴.
 *
 * 변경됨 (2026-10-06) — 날짜 기준을 "상품 등록일"에서 "상품 마감일시"로 바꿨다(소진율 = 마감 지난
 * 상품 중 픽업완료된 것 / 마감 지난 상품). 오늘 날짜 칸은 아직 마감 안 지난 상품이 섞여있을 수
 * 있어 inProgress로 표시하고, registeredCount/soldCount는 "이미 마감 지난 것만" 센다(확정 집계만).
 */
public record SellThroughDailyDto(
		String dateLabel,
		boolean isToday,
		// true면 이 날짜에 마감 예정인 상품 중 아직 마감 안 지난 게 남아있다는 뜻 — 화면엔 "집계 중".
		boolean inProgress,
		int registeredCount,
		int soldCount,
		// 등록 0건이거나 inProgress면 null → 화면에서 "0%"가 아니라 "-"/"집계 중"으로 표시.
		Integer sellThroughPercent,
		int registeredHeightPercent,
		int soldHeightPercent
) {
}

package net.dsa.girigiri.domain.dto;

import java.util.List;

/**
 * 점주 대시보드(StoreController#dashboard) 집계 결과 — 2026-09-03, 레이어 규칙 2단계로
 * StoreService#buildDashboardStats / emptyDashboardStats 이관 시 도입.
 *
 * 기존 컨트롤러가 model에 직접 addAttribute 하던 35개 값을 그대로 필드로 옮겼다 — 필드 순서·이름은
 * 기존 model 속성명과 1:1 대응한다.
 */
public record StoreDashboardStatsDto(
		String todaySales,
		String salesDelta,
		String salesDeltaClass,
		int soldCount,
		int registeredCount,
		int sellingNowCount,
		// 추가됨 (2026-10-05) — 왜: idleCount는 상태와 무관하게 remainingQuantity를 전부 더해서
		// "남음"(마감 전)/"폐기"(마감 후) 양쪽에 쓰인다 — 사장님이 직접 "품절" 처리한 재고도 결국
		// 못 판 거라 폐기 집계엔 맞게 남겨둬야 한다. 하지만 hero 카드의 "판매중 재고"는 글자 그대로
		// "지금 손님이 살 수 있는 재고"를 뜻하므로, status=active인 상품의 재고만 센 별도 값이 필요했다
		// (품절 처리 직후 idleCount는 그대로인데 sellingNowCount만 0이 되며 숫자가 모순돼 보이는
		// 문제를 사용자가 발견 — 품절/재개 버튼에 바로 반응해야 함).
		int sellableStock,
		int reservationCount,
		int reservationWaiting,
		int reservationDone,
		int reservationCancelled,
		int expiredCount,
		int rescueRate,
		int rescueGoalPercent,
		String rescueGoal,
		int totalQuantity,
		int pickedCount,
		int reservedNotPickedCount,
		int idleCount,
		boolean isClosed,
		// 추가됨 (2026-09-17) — 왜: StoreHoursUtil.isOpen(closeAt)은 closeAt==null(영업시간 미설정·
		// 형식 이상)이면 무조건 영업중(isClosed=false)으로 본다 — "정보 없으면 막지 않는다"는 의도로
		// 다른 9곳(발행 가능 여부 등)과 공유하는 판정이라 그 자체는 안 바꾼다. 하지만 대시보드
		// 배지에 실제로 영업 여부를 모르는데 "영업중"이라고 단정해서 보여주는 건 오해를 부르므로,
		// 화면에서만 "영업시간 미설정"이라는 3번째 상태로 구분해서 보여줄 수 있게 이 값을 따로 내려준다.
		boolean hoursConfigured,
		String closingCountdownLabel,
		int donutPickedPct,
		int donutReservedCumPct,
		int donutSoldCumPct,
		List<DailySalesBarDto> weeklySalesBars,
		int weeklySoldTotal,
		int weeklyWasteTotal,
		int weeklyRescuedCount,
		String weeklyRecoveredAmount,
		String weeklyCo2Kg,
		String todayCo2Kg,
		long draftPendingCount,
		int incomingReservationCount,
		boolean needsAutomationSetup,
		boolean needsBankAccount,
		String settlementPayout
) {
}

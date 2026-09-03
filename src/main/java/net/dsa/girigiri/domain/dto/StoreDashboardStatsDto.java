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

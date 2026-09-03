package net.dsa.girigiri.domain.dto;

import java.time.LocalDate;

/**
 * "이번 정산 주간"(이번 주 월~일) 진행 중 미리보기 — 아직 확정 전 (문창호, 2026-09-03).
 *
 * 정산 지급은 월~일 고정 주간 단위인데 페이지의 "최근 7일"은 롤링이라 정산 주기와 안 맞았다.
 * 매장 정산 페이지의 "정산 내역"(주간 확정 기록) 리스트 맨 위에 이 진행 중인 주 행을 하나 얹어서,
 * 지급 주기 그대로 "이번 주 얼마 쌓였나 / 언제 확정되나"를 볼 수 있게 한다.
 */
public record SettlementWeekPreview(
		LocalDate periodStart,   // 이번 주 월요일
		LocalDate periodEnd,     // 이번 주 일요일
		long amount,             // 현재까지 쌓인 정산액 (net - commission, 음수면 0)
		LocalDate confirmDate,   // 확정 예정일 (다음 월요일 00:00에 확정)
		int dayOfWeek            // 주 며칠차 (1=월 ~ 7=일)
) {}

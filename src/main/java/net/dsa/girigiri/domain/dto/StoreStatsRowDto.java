package net.dsa.girigiri.domain.dto;

/**
 * 추가됨 (2026-09-09) — 슈퍼어드민 "플랫폼 통계"(/superadmin/stats) 화면의 매장별 행 하나.
 * 대시보드 "거래량/구제량/매출" 카드를 눌렀을 때 가는 상세 화면 — 매장 단위로 쪼개서 보여준다.
 * 거래량/구제량/매출은 전부 status="picked"(픽업 완료)인 예약만 집계한다 — 결제 전(pending)이거나
 * 취소된 건은 실제로 오간 거래가 아니라서 뺀다(SuperAdminDashboardService#getPlatformStats 참고).
 *
 * 변경됨 (2026-09-09) — "매장별 취소율/노쇼율도 보고 싶다"는 요청으로 cancelRatePercent/
 * noshowRatePercent 추가. 분모(totalOrderCount)는 결제까지 간 주문(pending 제외) 전체 —
 * ReservationRepository.countByStoreIdAndStatusNot과 같은 기준. 표본이 적을 때(예: 1건 중 1건 취소
 * = 100%) 오해하지 않도록 totalOrderCount도 같이 내려서 화면에서 괄호로 건수를 보여준다.
 */
public record StoreStatsRowDto(
		Long storeId,
		String storeName,
		long transactionCount,
		long rescuedQuantity,
		long revenue,
		long totalOrderCount,
		double cancelRatePercent,
		double noshowRatePercent
) {
}

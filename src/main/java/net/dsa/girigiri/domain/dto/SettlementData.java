package net.dsa.girigiri.domain.dto;

import java.util.List;

/**
 * 매장 정산 데이터 — 미리보기 화면(settlementView/settlement.html) · Excel · PDF 공용.
 * WBS 2.0 "매장 정산 페이지 (기간별 결제 집계 / 수수료·정산 예정액 / 정산 내역 목록)" — 문창호, 2026-08-31.
 *
 * 판매·폐기 리포트와 다른 문서다:
 *  - 판매·폐기 리포트 = 운영·환경(폐기·구제율·CO₂)
 *  - 정산 = 회계(결제 총액 → 수수료 뺀 실지급액, 환불 차감)
 *
 * ⚠️ 지금은 MySQL(payment/reservation)에서 조회. 나중에 이 조립부(SettlementService)만
 *    Supabase REST(JSON)로 갈아끼울 수 있게 화면/생성 코드와 분리해뒀다.
 */
public record SettlementData(
		String storeName,
		String periodLabel,          // "2026-08 (이번 달)" 등
		int commissionRatePercent,   // 플랫폼 수수료율 %

		long gross,        // 총 결제액 (기간 내 결제 완료된 금액 합)
		long refund,       // 환불 차감 (기간 내 결제됐다가 취소·환불된 금액 합)
		long net,          // 순 결제액 = gross - refund
		long commission,   // 플랫폼 수수료 (결제 유지분 기준)
		long payout,       // 정산 예정액 = net - commission

		List<Line> lines,        // 결제 유지 거래 목록
		List<RefundLine> refunds // 환불 거래 목록 (없으면 빈 리스트)
) {

	/** 결제 완료 거래 1건 */
	public record Line(
			String dateLabel,   // 결제일 MM/dd
			String name,        // 상품명
			long amount,        // 결제액
			long commission,    // 수수료 (amount × rate)
			long payout,        // 실지급액 = amount - commission
			String status       // "픽업 완료" / "노쇼(환불 없음)"
	) {}

	/** 환불된 거래 1건 */
	public record RefundLine(
			String dateLabel,
			String name,
			long amount          // 환불액
	) {}
}

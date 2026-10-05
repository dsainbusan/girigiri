package net.dsa.girigiri.domain.dto;

/**
 * 추가됨 (강노은, 2026-10-05) — 마이페이지 "이용 내역" 한 줄. 픽업완료·노쇼·취소 세 가지 결과를
 * 한 목록에서 같이 보여준다(LedgerData.HistoryRow와 비슷한 모양이지만, 저쪽은 "픽업완료"만 다루는
 * 절약 가계부 전용이라 노쇼/취소까지 담을 수 없어서 따로 만들었다).
 *
 * statusVariant: ReservationService의 상태 배지 색 구분(badge--success/muted/danger)과 맞춘
 * "done"/"cancelled"/"noshow" 중 하나. noteText: 환불 여부처럼 상태별로 덧붙일 한 줄 설명
 * (픽업완료는 null — 가격 줄에서 이미 할인/절약을 보여주므로 따로 설명이 필요 없다).
 */
public record UsageHistoryRowDto(
		String dateLabel,
		String storeName,
		String productName,
		int quantity,
		int originalTotal,
		int paidTotal,
		int saved,
		String statusLabel,
		String statusVariant,
		String noteText
) {
	public int discountPercent() {
		return originalTotal > 0 ? (int) Math.round(100.0 * saved / originalTotal) : 0;
	}
}

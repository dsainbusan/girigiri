package net.dsa.girigiri.domain.dto;

/**
 * 재고 관리 화면 "판매 대기" — 아직 초안이 만들어지기 전, POS가 실시간으로 알려주는 재고 메뉴 1건
 * (2026-09-21). 정해진 시각이 되면 이 메뉴가 "발행 대기" 초안이 되고, 점주가 [바로 올리기]를 눌러야
 * 판매가 시작된다.
 *
 * saleQuantity: 앱에 실제로 올라갈 수량(= min(POS 재고, 앱 판매 최대 수량)).
 * expectedRate/expectedPrice: 지금 올린다고 가정한 할인율·할인가 — 마감까지 남은 시간에 따라 바뀐다.
 * manualRate: 점주가 이 메뉴에 할인율을 직접 지정해 뒀으면 true.
 */
public record PendingMenuDto(
		Long id,
		String name,
		String imageUrl,
		String thumbEmoji,
		String thumbColor,
		int stockQuantity,
		int saleQuantity,
		int originalPrice,
		int expectedRate,
		int expectedPrice,
		boolean manualRate
) {
}

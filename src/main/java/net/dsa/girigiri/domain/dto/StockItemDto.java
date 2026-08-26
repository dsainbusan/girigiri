package net.dsa.girigiri.domain.dto;

/**
 * 점주 재고 관리 목록(storeView/products.html) 카드에 뿌려줄 화면 전용 데이터.
 * ProductEntity를 그대로 넘기지 않고, 목록에 필요한 값만 뽑아서 넘긴다.
 *
 * 현재는 StoreProductController가 하드코딩 샘플로 채운다 (WBS 3.0 상품 등록 화면 목업 단계 —
 * 원래 김태훈 담당이었으나 2026-08-26 문창호 인계). 실데이터 배선 시 ProductEntity -> 이 DTO 매핑 +
 * 할인율/할인가는 DiscountRateCalculator로 계산해서 채운다 (PosApiController와 동일 정책).
 *
 * statusVariant: selling(판매중) / soldout(품절) / closed(마감) — store.css의 .stock-item__status--* 와 매칭.
 * imageUrl 이 null 이면 화면에서 thumbEmoji + thumbColor 로 대체 썸네일을 그린다.
 */
public record StockItemDto(
		Long id,
		String name,
		String imageUrl,
		String thumbEmoji,
		String thumbColor,
		String statusLabel,
		String statusVariant,
		int discountRate,
		int originalPrice,
		int discountedPrice,
		int remainingQuantity,
		int quantity,
		String registeredLabel
) {
}

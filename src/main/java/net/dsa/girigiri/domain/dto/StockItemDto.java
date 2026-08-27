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
 * manualSoldOut: 사장님이 "품절" 버튼으로 직접 내린 것(status='sold'). true면 "판매 재개" 버튼을 보여준다.
 *                (재고가 0이라 자연 품절된 건 재개할 게 없으므로 false.)
 * imageUrl 이 null 이면 화면에서 thumbEmoji + thumbColor 로 대체 썸네일을 그린다.
 * source: 이 상품/초안이 어디서 왔는지 — "pos"(POS 재고 스냅샷) / "template"(자동 등록 템플릿) / "manual"(직접 등록).
 *         발행 대기 초안 카드에 출처 뱃지를 붙이는 데 쓴다. (2026-08-27 문창호)
 */
public record StockItemDto(
		Long id,
		String name,
		String imageUrl,
		String thumbEmoji,
		String thumbColor,
		String statusLabel,
		String statusVariant,
		boolean manualSoldOut,
		int discountRate,
		int originalPrice,
		int discountedPrice,
		int remainingQuantity,
		int quantity,
		String registeredLabel,
		String source
) {
}

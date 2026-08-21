package net.dsa.girigiri.domain.dto;

/**
 * 찜 목록 화면 카드 1개. onSale=false면 discountRate/salePrice/leftLabel은 전부 null이고
 * offSaleText만 채워진다("지금은 세일 중이 아니에요").
 */
public record LikedStoreDto(
		Long storeId,
		String name,
		String category,
		String thumbText,
		String thumbColor,
		boolean onSale,
		String discountRate,
		String salePrice,
		String leftLabel,
		String offSaleText
) {
}

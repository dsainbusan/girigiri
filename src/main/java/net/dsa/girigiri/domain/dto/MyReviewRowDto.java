package net.dsa.girigiri.domain.dto;

/** "내 리뷰 관리" 페이지용 — 매장 하나에 종속되지 않고 내가 쓴 리뷰를 전체 매장에 걸쳐 모아 보여준다. */
public record MyReviewRowDto(
		Long id,
		Long storeId,
		String storeName,
		int rating,
		String content,
		String imageUrl,
		String createdAtLabel,
		boolean edited
) {
}

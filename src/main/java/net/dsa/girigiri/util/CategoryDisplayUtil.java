package net.dsa.girigiri.util;

/**
 * 추가됨 (2026-09-08, 코드 감사) — 카테고리 문자열 → 썸네일 이모지/색상 매핑이 HomeService·
 * SearchService·LikeService·RecommendationService·ProductController·StoreDetailController·
 * StoreProductController, 7곳에 거의 그대로 복붙돼 있었다. 그중 StoreProductController(재고관리)
 * 사본만 "카페/디저트"·"도시락/샐러드"(회원가입 화면 authView/ownerApply.html, 매장정보 수정
 * storeView/edit.html·superAdminView/storeEdit.html 드롭다운의 실제 선택지 값) 변형까지 인식하고
 * 나머지는 못 알아봐서, 그 카테고리를 고른 매장은 재고관리 화면에선 맞는 색/이모지가 보이는데
 * 홈·검색·찜·추천·상품상세·가게상세에선 전부 기본값(회색 접시)으로 보이는 불일치가 있었다 —
 * 더 완전한(변형까지 인식하는) 목록으로 통일한다.
 */
public final class CategoryDisplayUtil {

	private CategoryDisplayUtil() {
	}

	public static String thumbColor(String category) {
		if (category == null) {
			return "var(--c-line-weak)";
		}
		return switch (category) {
			case "베이커리" -> "var(--c-accent-weak)";
			case "카페", "카페/디저트" -> "var(--c-info-weak)";
			case "반찬", "도시락", "도시락/샐러드" -> "var(--c-primary-weak)";
			default -> "var(--c-line-weak)";
		};
	}

	public static String thumbEmoji(String category) {
		if (category == null) {
			return "🍽️";
		}
		return switch (category) {
			case "베이커리" -> "🥐";
			case "반찬" -> "🍚";
			case "도시락", "도시락/샐러드" -> "🍱";
			case "카페", "카페/디저트" -> "☕";
			default -> "🍽️";
		};
	}
}

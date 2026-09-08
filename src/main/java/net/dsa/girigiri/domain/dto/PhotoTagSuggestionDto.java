package net.dsa.girigiri.domain.dto;

/**
 * 강노은: "상품 사진 자동 태깅" 결과 — 점주가 상품 등록 폼에서 고른 사진을 보고 AI가 추측한
 * 카테고리·품목명. 값을 하나라도 못 정했으면 그 필드가 null(프론트는 null이면 해당 입력을
 * 그대로 안 건드린다) — "추천"일 뿐이라 사장님이 그대로 써도 되고 자유롭게 고쳐도 된다.
 */
public record PhotoTagSuggestionDto(String category, String itemName) {

	public static PhotoTagSuggestionDto empty() {
		return new PhotoTagSuggestionDto(null, null);
	}
}

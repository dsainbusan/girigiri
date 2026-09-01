package net.dsa.girigiri.domain.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 점주 상품 등록/수정 폼(storeView/productForm.html) 바인딩용.
 *
 * 사진(MultipartFile)은 여기 담지 않고 컨트롤러에서 @RequestParam("image")로 따로 받는다 —
 * 수정 시 "사진 안 바꿈"과 "사진 새로 올림"을 구분해야 해서 폼 객체에 섞지 않는 편이 깔끔하다.
 * 할인가(discountedPrice)는 폼에서 안 받는다 — 원가 + 할인율로 서버가 계산한다.
 * 할인율(discountRate)은 선택 입력: 비우면 마감시간 기준 자동값, 넣으면 그 값(자동값보다 낮으면 거부).
 * 빈 문자열 바인딩 문제를 피하려고 Integer가 아니라 String으로 받아 서버에서 파싱한다.
 */
@Getter
@Setter
public class ProductFormDto {

	private String name;
	private Integer originalPrice;
	private Integer quantity;
	private String description;
	private String discountRate;

	/** 수정 화면에서 현재 등록된 사진을 미리 보여주기 위한 값 (신규 등록 시 null). */
	private String currentImageUrl;

	public static ProductFormDto empty() {
		return new ProductFormDto();
	}
}

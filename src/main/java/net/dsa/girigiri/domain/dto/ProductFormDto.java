package net.dsa.girigiri.domain.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 점주 상품 등록/수정 폼(storeView/productForm.html) 바인딩용.
 *
 * 사진(MultipartFile)은 여기 담지 않고 컨트롤러에서 @RequestParam("image")로 따로 받는다 —
 * 수정 시 "사진 안 바꿈"과 "사진 새로 올림"을 구분해야 해서 폼 객체에 섞지 않는 편이 깔끔하다.
 * 할인가(discountedPrice)도 폼에서 안 받는다 — 원가 + 마감시간 기준으로 서버가 자동 계산한다
 * (DiscountRateCalculator, PosApiController와 동일 정책).
 */
@Getter
@Setter
public class ProductFormDto {

	private String name;
	private Integer originalPrice;
	private Integer quantity;
	private String description;

	/** 수정 화면에서 현재 등록된 사진을 미리 보여주기 위한 값 (신규 등록 시 null). */
	private String currentImageUrl;

	public static ProductFormDto empty() {
		return new ProductFormDto();
	}
}

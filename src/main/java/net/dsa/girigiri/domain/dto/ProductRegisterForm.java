package net.dsa.girigiri.domain.dto;

import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

/**
 * 사장님용 재고(상품) 등록 화면 입력값.
 * ProductEntity를 그대로 노출하지 않기 위한 폼 전용 DTO.
 */
@Getter
@Setter
public class ProductRegisterForm {

	private String name;
	private Integer originalPrice;
	private Integer discountedPrice;
	private Integer quantity;
	private String description;
	private MultipartFile image;
}

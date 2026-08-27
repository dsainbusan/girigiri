package net.dsa.girigiri.domain.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * "POS json 카탈로그 연동" 수신 포맷 — 2026-08-27 신규 (문창호).
 * POS가 매장 메뉴 카탈로그를 이런 배열로 보낸다고 가정한다:
 *   [ {"posSku":"BR001","name":"크루아상","originalPrice":3500,"imageUrl":null}, ... ]
 *
 * PosProductDto(단건 상품 수신, quantity 있음)와 달리 이건 "메뉴"라 수량이 없다.
 */
@Getter
@Setter
public class PosMenuItemDto {

	private String posSku;
	private String name;
	private Integer originalPrice;
	private String imageUrl;
}

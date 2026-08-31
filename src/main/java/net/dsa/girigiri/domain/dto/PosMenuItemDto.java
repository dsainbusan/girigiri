package net.dsa.girigiri.domain.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * "POS json 카탈로그 연동" 수신 포맷 — 2026-08-27 신규 (문창호).
 * POS가 매장 메뉴 카탈로그를 이런 배열로 보낸다고 가정한다:
 *   [ {"posSku":"BR001","name":"크루아상","originalPrice":3500,"imageUrl":null}, ... ]
 *
 * 재고는 여기 없다 — 재고는 별도로 PosStockDto(POST /api/pos/stock)로 받는다.
 */
@Getter
@Setter
public class PosMenuItemDto {

	private String posSku;
	private String name;
	private Integer originalPrice;
	private String imageUrl;
}

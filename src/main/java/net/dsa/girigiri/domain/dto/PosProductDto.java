package net.dsa.girigiri.domain.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 추가됨 (2026-08-21) — 왜: POS json 자동 수신 연동 (가정, WBS 3.0 문창호 담당).
 * 실제 POS 단말기가 아직 없어서, POS가 이런 형태의 JSON을 보낸다고 가정하고 그대로 받는다.
 * 예: {"name": "식빵 마감세트", "originalPrice": 6000, "quantity": 10}
 */
@Getter
@Setter
public class PosProductDto {

	private String name;
	private Integer originalPrice;
	private Integer quantity;
}

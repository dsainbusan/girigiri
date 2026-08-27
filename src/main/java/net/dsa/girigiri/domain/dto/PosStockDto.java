package net.dsa.girigiri.domain.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * "POS json 재고 스냅샷" 수신 포맷 — 2026-08-27 신규 (문창호).
 * POS가 현재 재고를 이런 배열로 보낸다고 가정한다:
 *   [ {"posSku":"BR001","remaining":7}, {"posSku":"LB001","remaining":4}, ... ]
 *
 * 마감 무렵 앱이 이 스냅샷으로 "지금 이만큼 남았는데 앱에 올릴래요?" 초안을 만든다(B안).
 */
@Getter
@Setter
public class PosStockDto {

	private String posSku;
	private Integer remaining;
}

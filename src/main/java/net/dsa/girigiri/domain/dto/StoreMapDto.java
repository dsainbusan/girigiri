package net.dsa.girigiri.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 홈 화면 지도에 마커로 표시할 가게 정보. StoreEntity를 그대로 노출하지 않는다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoreMapDto {

	private Long id;
	private String storeName;
	private String category;
	private Double latitude;
	private Double longitude;
}

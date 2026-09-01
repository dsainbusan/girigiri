package net.dsa.girigiri.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 홈 화면 카드 리스트 1개 항목.
 * common/components.html의 storeCard(store) fragment가 기대하는 필드 이름을 그대로 따른다
 * (fragment는 공용 자산이라 여기서 필드명을 맞춘다 — fragment 쪽을 고치지 않는다).
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoreCardDto {

	private Long id;              // 상품 id (/user/products/{id} 링크용)
	private Long storeId;         // 매장 id (찜하기 버튼용 — id와 별개)
	private String thumbText;
	private String thumbColor;
	private String name;          // 매장명
	private String category;
	private String distance;      // "1.2km" 형태 — 사용자 좌표 없으면 빈 값(DistanceUtil.label 참고).
	private String origPrice;     // "12,000원" 형태로 포맷된 문자열
	private String salePrice;
	private String discountRate;  // "-51%" 형태
	private String leftLabel;     // "마감까지 42분"
	private boolean urgent;
	private boolean liked;
}

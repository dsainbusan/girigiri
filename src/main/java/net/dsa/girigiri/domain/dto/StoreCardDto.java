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
	private String imageUrl;      // 가게 사진 (2026-09-21 추가) — null이면 thumbText/thumbColor 아바타로 대체
	private String name;          // 매장명
	private String category;
	private String distance;      // "1.2km" 형태 — 사용자 좌표 없으면 빈 값(DistanceUtil.label 참고).
	private String origPrice;     // "12,000원" 형태로 포맷된 문자열
	private String salePrice;
	private String discountRate;  // "-51%" 형태
	private String leftLabel;     // "마감까지 42분"
	private boolean urgent;
	private boolean liked;

	// 추가됨 (2026-09-16, 채채 요청 — 매장 신뢰도 자동 정지) — 왜: 신뢰도 정지 중인 매장의 홈 카드가
	// 평소랑 똑같이 "-50% 3,000원, 마감까지 5시간" 그대로 떠서 눌러서 들어가야만 정지된 걸 알 수
	// 있었다. 체크아웃 화면과 같은 원칙(정지 사유는 노출 안 함)으로, 카드는 그대로 두되 회색으로
	// 흐리고 마감 카운트다운 대신 "오늘 휴무"만 보여준다(common/components.html storeCard 참고).
	// 기본값 false라 이 필드를 안 채우는 다른 화면(검색/찜/추천)은 기존과 동일하게 동작한다.
	private boolean blocked;
}

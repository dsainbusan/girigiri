package net.dsa.girigiri.domain.dto;

import java.util.List;

/**
 * 강노은: 홈 화면 "개인화 추천" 섹션 하나 — 제목(카테고리 기반 문구 또는 신규 유저용 기본 문구)과
 * 그 아래 보여줄 카드 목록. 카드는 홈 메인 목록과 같은 storeCard 프래그먼트를 그대로 재사용한다.
 */
public record RecommendationSectionDto(String title, List<StoreCardDto> cards) {
}

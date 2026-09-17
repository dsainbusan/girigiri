package net.dsa.girigiri.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 마케팅 홈페이지("/") 히어로 섹션의 실적 카운터용. 과장된 수치를 지어내지 않고 실제 DB 집계값만
 * 담는다 — MarketingService#getImpactStats 참고.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketingStatsDto {

	private long rescuedCount; // 픽업 완료(status=picked)된 예약 수 — "지금까지 구제한 음식"
	private long storeCount;   // 입점 승인된 매장 수 — "함께하는 가게"
}

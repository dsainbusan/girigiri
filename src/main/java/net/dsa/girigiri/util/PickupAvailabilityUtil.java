package net.dsa.girigiri.util;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * "손님이 픽업 시간대를 직접 고르지 않고, 시스템이 자동으로 주문 가능 여부/예상 픽업 시각을
 * 계산해주는" 방식으로 바뀌면서 만든 유틸.
 *
 * 핵심 조건 (매장이 StoreEntity.prepTimeMinutes/lastPickupTime을 설정해뒀을 때):
 *   현재시간 + 준비시간 <= 마지막 픽업시간   → 지금 주문하면 오늘 안에 픽업까지 끝낼 수 있다.
 *
 * 매장이 아직 이 값을 설정 안 했으면(lastPickupTime == null) — 매장 설정 화면이 아직 없어서
 * 기존 매장 데이터는 비어있을 수 있다 — "설정 전이라 항상 주문 가능"으로 취급한다(하위 호환).
 */
public class PickupAvailabilityUtil {

	// 강노은 — 왜: StoreEntity.prepTimeMinutes가 null일 때 쓰는 기본 준비시간. 원래 ReservationController에만
	// 로컬 상수로 있었는데 SearchService(픽업시간 필터)도 같은 기본값이 필요해서 여기 공용 유틸로 옮겼다.
	// StoreEntity.prepTimeMinutes의 @Builder.Default(20)와 반드시 같은 값으로 맞춘다.
	// 참고: ReservationController.DEFAULT_PREP_TIME_MINUTES는 예약 로직 담당(문창호) 파일이라 손 안 대고
	// 그대로 남겨뒀다 — 값이 같은 한 문제는 없지만, 완전히 한 곳으로 합치려면 그쪽에서 이 상수를
	// 참조하도록 바꿔야 한다.
	public static final int DEFAULT_PREP_TIME_MINUTES = 20;

	private PickupAvailabilityUtil() {
	}

	/** 지금(now) 주문하면 매장 마지막 픽업시간 전에 준비가 끝나는지. */
	public static boolean canOrderNow(LocalDateTime now, LocalTime lastPickupTime, int prepTimeMinutes) {
		if (lastPickupTime == null) {
			return true;   // 매장이 아직 설정 안 함 → 항상 주문 가능 취급
		}
		LocalDateTime lastPickupDateTime = LocalDateTime.of(now.toLocalDate(), lastPickupTime);
		return !now.plusMinutes(prepTimeMinutes).isAfter(lastPickupDateTime);
	}

	/** 지금(now) 주문하면 예상되는 가장 빠른 픽업 가능 시각 = 현재시간 + 준비시간. */
	public static LocalDateTime earliestPickupTime(LocalDateTime now, int prepTimeMinutes) {
		return now.plusMinutes(prepTimeMinutes);
	}
}

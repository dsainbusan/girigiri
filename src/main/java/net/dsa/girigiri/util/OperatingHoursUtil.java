package net.dsa.girigiri.util;

import java.time.LocalTime;

/**
 * StoreEntity.operatingHours에서 영업종료시간만 뽑아내는 유틸.
 *
 * 수정됨 (2026-09-08) — StoreHoursUtil.parseClosingTime()으로 위임하여
 * 프로젝트 전반의 영업시간 파싱 로직 중복을 제거했다.
 */
public class OperatingHoursUtil {

	private OperatingHoursUtil() {
	}

	/** "09:00 ~ 22:00" -> 22:00(LocalTime)을 돌려준다. 형식이 안 맞으면 IllegalArgumentException. */
	public static LocalTime parseClosingTime(String operatingHours) {
		return StoreHoursUtil.parseClosingTime(operatingHours);
	}
}

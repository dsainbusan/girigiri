package net.dsa.girigiri.util;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * StoreEntity.operatingHours("09:00 ~ 22:00" 같은 자유 텍스트)에서 영업종료시간만 뽑아내는 유틸.
 *
 * 주의: 지금은 "HH:mm ~ HH:mm" 형식만 가정한다. 사장님이 다른 형식으로 입력하면(예: "오전 9시~오후 10시")
 *      파싱에 실패한다. 진짜 서비스라면 자유 텍스트 대신 시간 선택 UI로 받는 게 안전하지만,
 *      지금 단계에서는 일단 이 형식 하나만 지원하고 나머지는 알려진 제약으로 남겨둔다.
 */
public class OperatingHoursUtil {

	private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

	private OperatingHoursUtil() {
	}

	/** "09:00 ~ 22:00" -> 22:00(LocalTime)을 돌려준다. 형식이 안 맞으면 IllegalArgumentException. */
	public static LocalTime parseClosingTime(String operatingHours) {
		if (operatingHours == null || !operatingHours.contains("~")) {
			throw new IllegalArgumentException("영업시간 형식을 읽을 수 없어요: " + operatingHours);
		}

		String closingPart = operatingHours.split("~")[1].trim();
		try {
			return LocalTime.parse(closingPart, TIME_FORMAT);
		} catch (Exception e) {
			throw new IllegalArgumentException("영업시간 형식을 읽을 수 없어요: " + operatingHours, e);
		}
	}
}

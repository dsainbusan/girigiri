package net.dsa.girigiri.util;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * StoreEntity.operatingHours("09:00 ~ 22:00" 형태)에서 마감 시각을 계산하는 공용 유틸.
 * 홈 화면 카드(HomeService)와 상품 상세(ProductController) 양쪽에서 같은 로직을 쓴다.
 */
public final class StoreHoursUtil {

	private static final DateTimeFormatter HOUR_FORMAT = DateTimeFormatter.ofPattern("H:mm");

	private StoreHoursUtil() {
	}

	public record ClosingInfo(String label, boolean urgent, LocalDateTime closeAt) {
	}

	/**
	 * operatingHours 형식이 다르거나 없으면 빈 라벨(closeAt=null)로 처리한다 — 예외를 던지지 않는다.
	 */
	public static ClosingInfo parse(String operatingHours, long urgentThresholdMinutes) {
		if (operatingHours == null || !operatingHours.contains("~")) {
			return new ClosingInfo("", false, null);
		}
		try {
			String closePart = operatingHours.split("~")[1].trim();
			LocalTime closeTime = LocalTime.parse(closePart, HOUR_FORMAT);
			LocalDateTime close = LocalDateTime.now().toLocalDate().atTime(closeTime);
			LocalDateTime now = LocalDateTime.now();

			if (!close.isAfter(now)) {
				return new ClosingInfo("영업 종료", false, close);
			}

			long minutes = Duration.between(now, close).toMinutes();
			boolean urgent = minutes <= urgentThresholdMinutes;
			String label = minutes < 60
					? "마감까지 " + minutes + "분"
					: "마감까지 " + (minutes / 60) + "시간 " + (minutes % 60) + "분";
			return new ClosingInfo(label, urgent, close);
		} catch (Exception e) {
			return new ClosingInfo("", false, null);
		}
	}
}

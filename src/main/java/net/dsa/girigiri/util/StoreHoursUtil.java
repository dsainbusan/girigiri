package net.dsa.girigiri.util;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * StoreEntity.operatingHours("09:00 ~ 22:00" 형태)에서 마감 시각을 계산하는 공용 유틸.
 * 홈 화면 카드(HomeService)와 상품 상세(ProductController) 양쪽에서 같은 로직을 쓴다.
 */
public final class StoreHoursUtil {

	private static final DateTimeFormatter HOUR_FORMAT = DateTimeFormatter.ofPattern("H:mm");

	// 변경됨 — 왜: 매장 정보 수정 폼(storeView/edit.html)의 placeholder가 "09:00 ~ 21:00 (마감 세일
	// 19:00~)"처럼 마감 시각 뒤에 괄호 설명을 붙이는 걸 유도하는데, 아래 파싱이 "~" 뒤 문자열
	// 전체를 그대로 LocalTime.parse에 넘기면 괄호 때문에 항상 실패해서 대시보드 마감 배지가 빈
	// 채로 나왔다(직접 재현해서 확인함). "~" 뒤에서 첫 번째로 나오는 "H:mm" 패턴만 뽑아 쓰도록 고친다.
	private static final Pattern TIME_TOKEN = Pattern.compile("(\\d{1,2}:\\d{2})");

	/**
	 * "오늘의 구제" 초안을 발행(등록)할 수 있는 마지막 여유(분).
	 * 마감 직전에 올리면 손님이 예약·픽업하러 올 시간이 없어서 죽은 매물이 되므로, 마감 N분 전에 등록을 닫는다.
	 */
	public static final int PUBLISH_CUTOFF_MINUTES = 10;

	private StoreHoursUtil() {
	}

	public record ClosingInfo(String label, boolean urgent, LocalDateTime closeAt) {
	}

	/**
	 * 지금 초안을 발행할 수 있는지 — 마감 {@link #PUBLISH_CUTOFF_MINUTES}분 전을 넘겼으면 false.
	 * closeAt이 null(영업시간 정보 없음)이면 막지 않는다(true).
	 */
	public static boolean canPublishNow(LocalDateTime closeAt) {
		return closeAt == null
				|| LocalDateTime.now().isBefore(closeAt.minusMinutes(PUBLISH_CUTOFF_MINUTES));
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
			Matcher matcher = TIME_TOKEN.matcher(closePart);
			if (!matcher.find()) {
				return new ClosingInfo("", false, null);
			}
			LocalTime closeTime = LocalTime.parse(matcher.group(1), HOUR_FORMAT);
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

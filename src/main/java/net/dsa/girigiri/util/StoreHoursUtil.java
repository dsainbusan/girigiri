package net.dsa.girigiri.util;

import java.time.Duration;
import java.time.LocalDate;
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
		return parse(operatingHours, urgentThresholdMinutes, LocalDateTime.now());
	}

	/** now를 주입받는 오버로드 — 테스트용. 자정을 넘기는 영업시간 판정 때문에 시각 의존이 커서 분리했다. */
	static ClosingInfo parse(String operatingHours, long urgentThresholdMinutes, LocalDateTime now) {
		if (operatingHours == null || !operatingHours.contains("~")) {
			return new ClosingInfo("", false, null);
		}
		try {
			String[] parts = operatingHours.split("~");
			LocalTime closeTime = firstTimeToken(parts[1]);
			if (closeTime == null) {
				return new ClosingInfo("", false, null);
			}
			LocalTime openTime = parts.length > 0 ? firstTimeToken(parts[0]) : null;

			// 자정을 넘기는 영업시간(예: 18:00 ~ 02:00): 마감 시각이 시작 시각보다 이르면 마감은 "다음날"이다.
			// 단, 지금이 이미 마감 시각 이전(새벽)이면 어제 시작한 영업이 오늘 새벽에 끝나는 것이라 "오늘".
			LocalDate closeDate = now.toLocalDate();
			boolean crossesMidnight = openTime != null && closeTime.isBefore(openTime);
			if (crossesMidnight && !now.toLocalTime().isBefore(closeTime)) {
				closeDate = closeDate.plusDays(1);
			}
			LocalDateTime close = closeDate.atTime(closeTime);

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

	/** 문자열에서 첫 번째 "H:mm" 토큰을 LocalTime으로. 못 찾으면 null. */
	private static LocalTime firstTimeToken(String s) {
		if (s == null) {
			return null;
		}
		Matcher matcher = TIME_TOKEN.matcher(s);
		return matcher.find() ? LocalTime.parse(matcher.group(1), HOUR_FORMAT) : null;
	}
}

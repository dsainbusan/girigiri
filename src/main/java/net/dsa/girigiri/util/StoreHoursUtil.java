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
		if (operatingHours == null || !operatingHours.contains("~")) {
			return new ClosingInfo("", false, null);
		}
		try {
			String[] parts = operatingHours.split("~", 2);
			String openPart = parts[0].trim();
			String closePart = parts[1].trim();

			Matcher matcher = TIME_TOKEN.matcher(closePart);
			if (!matcher.find()) {
				return new ClosingInfo("", false, null);
			}
			LocalTime closeTime = LocalTime.parse(matcher.group(1), HOUR_FORMAT);
			LocalDateTime now = LocalDateTime.now();
			LocalDate closeDate = now.toLocalDate();

			// 추가됨 (2026-09-08, 코드 감사) — 마감이 자정을 넘기는 매장(예: 18:00~02:00)은 마감시각을
			// 항상 "오늘 날짜"로 고정해서 계산했더니, 자정 넘는 마감은 계산 즉시 과거가 되어 "영업 종료"가
			// 하루 종일 뜨고 canPublishNow가 영원히 false가 되는 문제가 있었다(밤늦게 파는 포차·야식
			// 매장이 실제로 부딪히는 조합). 오픈시각도 같이 파싱해서 "마감 <= 오픈"(자정을 넘긴다는 뜻)
			// 이고 지금이 오픈시각 이후(=아직 자정 전, 오늘 영업 중)면 마감을 내일 날짜로 계산한다.
			// 오픈시각 파싱에 실패하면(형식이 다르거나 없으면) 안전하게 예전 동작(오늘 날짜)으로 폴백한다.
			Matcher openMatcher = TIME_TOKEN.matcher(openPart);
			if (openMatcher.find()) {
				LocalTime openTime = LocalTime.parse(openMatcher.group(1), HOUR_FORMAT);
				boolean crossesMidnight = !closeTime.isAfter(openTime);
				if (crossesMidnight && !now.toLocalTime().isBefore(openTime)) {
					closeDate = closeDate.plusDays(1);
				}
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
}

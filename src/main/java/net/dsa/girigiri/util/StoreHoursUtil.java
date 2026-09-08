package net.dsa.girigiri.util;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * StoreEntity.operatingHours("09:00 ~ 22:00" 형태)에서 마감 시각을 계산하고
 * 영업시간 형식을 검증·조합하는 공용 유틸.
 * 홈 화면 카드(HomeService)와 상품 상세(ProductController) 양쪽에서 같은 로직을 쓴다.
 */
public final class StoreHoursUtil {

	private static final DateTimeFormatter HOUR_FORMAT = DateTimeFormatter.ofPattern("H:mm");
	private static final DateTimeFormatter STANDARD_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

	// 변경됨 — 왜: 매장 정보 수정 폼(storeView/edit.html)의 placeholder가 "09:00 ~ 21:00 (마감 세일
	// 19:00~)"처럼 마감 시각 뒤에 괄호 설명을 붙이는 걸 유도하는데, 아래 파싱이 "~" 뒤 문자열
	// 전체를 그대로 LocalTime.parse에 넘기면 괄호 때문에 항상 실패해서 대시보드 마감 배지가 빈
	// 채로 나왔다(직접 재현해서 확인함). "~" 뒤에서 첫 번째로 나오는 "H:mm" 패턴만 뽑아 쓰도록 고친다.
	private static final Pattern TIME_TOKEN = Pattern.compile("(\\d{1,2}:\\d{2})");
	private static final Pattern SALE_TIME_PATTERN = Pattern.compile("마감\\s*세일\\s*:?\\s*(\\d{1,2}:\\d{2})");

	/**
	 * "오늘의 구제" 초안을 발행(등록)할 수 있는 마지막 여유(분).
	 * 마감 직전에 올리면 손님이 예약·픽업하러 올 시간이 없어서 죽은 매물이 되므로, 마감 N분 전에 등록을 닫는다.
	 */
	public static final int PUBLISH_CUTOFF_MINUTES = 10;

	// 추가됨 (2026-09-08, 코드 감사) — parse(operatingHours, urgentThresholdMinutes)의 두 번째 인자로
	// "마감까지 60분 이내면 urgent"를 쓰는 곳이 9군데(ProductController·StoreDetailController·
	// PosCatalogService·LikeService·HomeService·ProductService·SearchService·ListingDraftScheduler·
	// RecommendationService)에 전부 로컬 상수로 따로 선언돼 있었다 — 값을 바꾸려면 9곳을 다 찾아야
	// 하는 구조라 PickupAvailabilityUtil.DEFAULT_PREP_TIME_MINUTES와 같은 패턴으로 여기 하나로 모은다.
	public static final long URGENT_THRESHOLD_MINUTES = 60;

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

	// 추가됨 (2026-09-08, 코드 감사) — "영업중/휴업" 판정이 SuperAdminStoreController(isOpen, 2곳)와
	// StoreService(isClosed)에 각자 다른 형태(긍정문/부정문)로 따로 쓰여 있었다. 수학적으로는 서로
	// 반대라 실제로 값이 어긋난 건 아니었지만(De Morgan), 같은 조건이 표현만 3번 갈라져 있으면 나중에
	// 조건 하나를 손볼 때(예: "마감 10분 전부터는 휴업으로 보이게") 하나만 고치고 나머지를 빠뜨리기
	// 쉽다. closeAt이 null(영업시간 정보 없음)이면 영업중으로 본다.
	public static boolean isOpen(LocalDateTime closeAt) {
		return closeAt == null || closeAt.isAfter(LocalDateTime.now());
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
			LocalTime closeTime = parseClosingTime(operatingHours);
			LocalTime openTime = parseOpeningTime(operatingHours);

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

	/**
	 * "09:00 ~ 22:00"에서 마감 시각 22:00(LocalTime)을 추출한다.
	 * "~" 뒤의 첫 번째 H:mm 패턴을 읽으며, 형식이 올바르지 않으면 IllegalArgumentException을 던진다.
	 */
	public static LocalTime parseClosingTime(String operatingHours) {
		if (operatingHours == null || !operatingHours.contains("~")) {
			throw new IllegalArgumentException("영업시간 형식을 읽을 수 없어요: " + operatingHours);
		}
		String[] parts = operatingHours.split("~", 2);
		String closingPart = parts.length > 1 ? parts[1].trim() : "";
		LocalTime closeTime = firstTimeToken(closingPart);
		if (closeTime == null) {
			throw new IllegalArgumentException("영업시간 형식을 읽을 수 없어요: " + operatingHours);
		}
		return closeTime;
	}

	/**
	 * "09:00 ~ 22:00"에서 시작 시각 09:00(LocalTime)을 추출한다.
	 * "~" 앞의 첫 번째 H:mm 패턴을 읽으며, 없으면 null을 반환한다.
	 */
	public static LocalTime parseOpeningTime(String operatingHours) {
		if (operatingHours == null || !operatingHours.contains("~")) {
			return null;
		}
		String[] parts = operatingHours.split("~", 2);
		return firstTimeToken(parts[0].trim());
	}

	/**
	 * "09:00 ~ 22:00 (마감 세일 19:00~)"에서 마감 세일 시작 시각 19:00(LocalTime)을 추출한다.
	 * 패턴이 없거나 파싱 실패 시 null을 반환한다.
	 */
	public static LocalTime parseSaleStartTime(String operatingHours) {
		if (operatingHours == null) {
			return null;
		}
		Matcher matcher = SALE_TIME_PATTERN.matcher(operatingHours);
		if (matcher.find()) {
			try {
				return LocalTime.parse(matcher.group(1), HOUR_FORMAT);
			} catch (Exception e) {
				return null;
			}
		}
		return null;
	}

	/**
	 * 시작 시각, 마감 시각, 마감 세일 시작 시각(선택)을 표준 문자열 포맷으로 조합한다.
	 * 예: "09:00 ~ 22:00" 또는 "09:00 ~ 22:00 (마감 세일 20:00~)"
	 */
	public static String formatOperatingHours(LocalTime openTime, LocalTime closeTime, LocalTime saleStartTime) {
		if (openTime == null || closeTime == null) {
			return null;
		}
		String base = openTime.format(STANDARD_FORMAT) + " ~ " + closeTime.format(STANDARD_FORMAT);
		if (saleStartTime != null) {
			return base + " (마감 세일 " + saleStartTime.format(STANDARD_FORMAT) + "~)";
		}
		return base;
	}

	/**
	 * 문자열이 유효한 영업시간 형식(시작과 마감 시각이 모두 존재하는지)인지 검증한다.
	 * null이거나 공백이면 설정 안 함으로 간주하여 true를 반환한다.
	 */
	public static boolean isValidFormat(String operatingHours) {
		if (operatingHours == null || operatingHours.isBlank()) {
			return true;
		}
		if (!operatingHours.contains("~")) {
			return false;
		}
		try {
			LocalTime open = parseOpeningTime(operatingHours);
			LocalTime close = parseClosingTime(operatingHours);
			return open != null && close != null;
		} catch (Exception e) {
			return false;
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

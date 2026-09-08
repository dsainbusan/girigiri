package net.dsa.girigiri.util;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * StoreEntity.operatingHours("09:00 ~ 22:00" 같은 자유 텍스트)에서 영업종료시간만 뽑아내는 유틸.
 *
 * 주의: 지금은 "HH:mm ~ HH:mm" 형식만 가정한다. 사장님이 다른 형식으로 입력하면(예: "오전 9시~오후 10시")
 *      파싱에 실패한다. 진짜 서비스라면 자유 텍스트 대신 시간 선택 UI로 받는 게 안전하지만,
 *      지금 단계에서는 일단 이 형식 하나만 지원하고 나머지는 알려진 제약으로 남겨둔다.
 *
 * 수정됨 (2026-09-08, 코드 감사) — 왜: "09:00 ~"처럼 "~" 뒤에 시각이 없으면 split("~")가 트레일링
 * 빈 문자열을 잘라내(Java 기본 동작) 배열 길이가 1이 되는데, 그 상태로 인덱스 [1]에 접근해서
 * ArrayIndexOutOfBoundsException이 던져지고 있었다 — 이건 IllegalArgumentException이 아니라서
 * 세 호출부(ReservationService 2곳, ReservationStoreController 1곳)의 catch(IllegalArgumentException)를
 * 전부 그냥 통과해버렸다. 매장 수정 폼이 예시로 보여주는 "09:00 ~ 21:00 (마감 세일 19:00~)" 형식도
 * "~" 뒤에 숫자가 아닌 괄호가 오면 이전 로직(LocalTime.parse 전체 문자열)이 실패했다.
 * StoreHoursUtil.parse()가 이미 쓰고 있는 "~" 뒤 첫 "H:mm" 토큰만 정규식으로 뽑는 방식으로 맞춰서
 * 두 문제를 한 번에 없앤다 — 반환 타입/예외 계약(IllegalArgumentException)은 그대로라 호출부는
 * 안 바뀐다.
 */
public class OperatingHoursUtil {

	private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("H:mm");
	private static final Pattern TIME_TOKEN = Pattern.compile("(\\d{1,2}:\\d{2})");

	private OperatingHoursUtil() {
	}

	/** "09:00 ~ 22:00" -> 22:00(LocalTime)을 돌려준다. 형식이 안 맞으면 IllegalArgumentException. */
	public static LocalTime parseClosingTime(String operatingHours) {
		if (operatingHours == null || !operatingHours.contains("~")) {
			throw new IllegalArgumentException("영업시간 형식을 읽을 수 없어요: " + operatingHours);
		}

		String[] parts = operatingHours.split("~", 2);
		String closingPart = parts.length > 1 ? parts[1].trim() : "";
		Matcher matcher = TIME_TOKEN.matcher(closingPart);
		if (!matcher.find()) {
			throw new IllegalArgumentException("영업시간 형식을 읽을 수 없어요: " + operatingHours);
		}

		try {
			return LocalTime.parse(matcher.group(1), TIME_FORMAT);
		} catch (Exception e) {
			throw new IllegalArgumentException("영업시간 형식을 읽을 수 없어요: " + operatingHours, e);
		}
	}
}

package net.dsa.girigiri.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 자정을 넘기는 영업시간(예: 18:00 ~ 02:00) 처리 확인 (코드 감사 #17).
 * parse(operatingHours, threshold, now) 오버로드로 now를 고정해서 테스트한다.
 */
class StoreHoursUtilTest {

	private static final long URGENT = 60;

	private StoreHoursUtil.ClosingInfo parse(String hours, LocalDateTime now) {
		return StoreHoursUtil.parse(hours, URGENT, now);
	}

	@Test
	void 일반_영업시간_저녁() {
		var info = parse("09:00 ~ 21:00", LocalDateTime.of(2026, 9, 8, 19, 0));
		assertEquals(LocalDateTime.of(2026, 9, 8, 21, 0), info.closeAt());
		assertEquals("마감까지 2시간 0분", info.label());
	}

	@Test
	void 자정_넘김_저녁엔_마감이_다음날() {
		// 18:00~02:00, 지금 20:00 → 마감은 "내일 새벽 02:00" (예전엔 오늘 02:00으로 잡혀 "영업 종료"였음)
		var info = parse("18:00 ~ 02:00", LocalDateTime.of(2026, 9, 8, 20, 0));
		assertEquals(LocalDateTime.of(2026, 9, 9, 2, 0), info.closeAt());
		assertTrue(info.label().startsWith("마감까지 6시간"));
	}

	@Test
	void 자정_넘김_새벽엔_마감이_오늘() {
		// 18:00~02:00, 지금 01:00 → 어제 시작한 영업이 오늘 02:00에 끝남
		var info = parse("18:00 ~ 02:00", LocalDateTime.of(2026, 9, 8, 1, 0));
		assertEquals(LocalDateTime.of(2026, 9, 8, 2, 0), info.closeAt());
		assertTrue(info.label().startsWith("마감까지 1시간"));
	}

	@Test
	void 자정_넘김_저녁엔_아직_영업중이라_영업_종료가_아니다() {
		// 예전엔 closeAt이 "오늘 02:00"(과거)로 잡혀 종일 "영업 종료" + canPublishNow가 영원히 false였다.
		// 이제 closeAt이 "내일 02:00"(미래)라 영업중으로 뜬다. (canPublishNow는 실제 벽시계 now를 쓰므로
		// 여기선 검증하지 않는다 — parse가 미래 시각을 돌려주는지만 본다.)
		var info = parse("18:00 ~ 02:00", LocalDateTime.of(2026, 9, 8, 20, 0));
		assertNotEquals("영업 종료", info.label());
		assertTrue(info.closeAt().isAfter(LocalDateTime.of(2026, 9, 8, 20, 0)));
	}

	@Test
	void 마감_지난_일반매장은_영업_종료() {
		var info = parse("09:00 ~ 21:00", LocalDateTime.of(2026, 9, 8, 23, 0));
		assertEquals("영업 종료", info.label());
	}

	@Test
	void 괄호_설명_붙은_형식도_마감시각만_뽑는다() {
		var info = parse("09:00 ~ 21:00 (마감 세일 19:00~)", LocalDateTime.of(2026, 9, 8, 12, 0));
		assertEquals(LocalDateTime.of(2026, 9, 8, 21, 0), info.closeAt());
	}

	@Test
	void 영업시간_파싱_세부_테스트() {
		String text = "09:30 ~ 21:30 (마감 세일 20:00~)";
		assertEquals(java.time.LocalTime.of(9, 30), StoreHoursUtil.parseOpeningTime(text));
		assertEquals(java.time.LocalTime.of(21, 30), StoreHoursUtil.parseClosingTime(text));
		assertEquals(java.time.LocalTime.of(20, 0), StoreHoursUtil.parseSaleStartTime(text));

		String textWithoutSale = "10:00 ~ 22:00";
		assertEquals(java.time.LocalTime.of(10, 0), StoreHoursUtil.parseOpeningTime(textWithoutSale));
		assertEquals(java.time.LocalTime.of(22, 0), StoreHoursUtil.parseClosingTime(textWithoutSale));
		org.junit.jupiter.api.Assertions.assertNull(StoreHoursUtil.parseSaleStartTime(textWithoutSale));
	}

	@Test
	void 영업시간_포맷_조합_테스트() {
		String formattedWithSale = StoreHoursUtil.formatOperatingHours(
				java.time.LocalTime.of(9, 0),
				java.time.LocalTime.of(21, 0),
				java.time.LocalTime.of(19, 30)
		);
		assertEquals("09:00 ~ 21:00 (마감 세일 19:30~)", formattedWithSale);

		String formattedWithoutSale = StoreHoursUtil.formatOperatingHours(
				java.time.LocalTime.of(9, 0),
				java.time.LocalTime.of(21, 0),
				null
		);
		assertEquals("09:00 ~ 21:00", formattedWithoutSale);
	}

	@Test
	void 영업시간_포맷_유효성_검증() {
		org.junit.jupiter.api.Assertions.assertTrue(StoreHoursUtil.isValidFormat("09:00 ~ 21:00"));
		org.junit.jupiter.api.Assertions.assertTrue(StoreHoursUtil.isValidFormat("09:00 ~ 21:00 (마감 세일 19:00~)"));
		org.junit.jupiter.api.Assertions.assertTrue(StoreHoursUtil.isValidFormat(""));
		org.junit.jupiter.api.Assertions.assertTrue(StoreHoursUtil.isValidFormat(null));

		org.junit.jupiter.api.Assertions.assertFalse(StoreHoursUtil.isValidFormat("오전 9시 ~ 오후 9시"));
		org.junit.jupiter.api.Assertions.assertFalse(StoreHoursUtil.isValidFormat("09:00"));
		org.junit.jupiter.api.Assertions.assertFalse(StoreHoursUtil.isValidFormat("이상한문자열"));
	}
}

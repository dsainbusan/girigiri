package net.dsa.girigiri.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

class StoreHoursValidationTest {

	// 수정됨 (2026-10-06, 코드 리뷰 #9) — 이 테스트는 원래 OperatingHoursUtil이 StoreHoursUtil에
	// 제대로 위임하는지 확인하는 용도였다. 그 래퍼(1줄 위임뿐이었음)를 삭제하면서, parseClosingTime
	// 자체에 대한 테스트로 바꿔 커버리지를 유지한다.
	@Test
	@DisplayName("parseClosingTime 정상/비정상 케이스")
	void parseClosingTimeCases() {
		assertEquals(LocalTime.of(22, 0), StoreHoursUtil.parseClosingTime("09:00 ~ 22:00"));
		assertEquals(LocalTime.of(21, 30), StoreHoursUtil.parseClosingTime("09:00 ~ 21:30 (마감 세일 20:00~)"));
		assertThrows(IllegalArgumentException.class, () -> StoreHoursUtil.parseClosingTime("invalid"));
		assertThrows(IllegalArgumentException.class, () -> StoreHoursUtil.parseClosingTime(null));
	}

	@Test
	@DisplayName("다양한 정상/비정상 포맷에 대한 isValidFormat 검증")
	void isValidFormatCases() {
		// 정상 케이스
		assertTrue(StoreHoursUtil.isValidFormat("09:00 ~ 22:00"));
		assertTrue(StoreHoursUtil.isValidFormat("9:00 ~ 21:00"));
		assertTrue(StoreHoursUtil.isValidFormat("09:00 ~ 21:00 (마감 세일 19:00~)"));
		assertTrue(StoreHoursUtil.isValidFormat("09:00 ~ 21:00 (마감 세일 : 20:00)"));
		assertTrue(StoreHoursUtil.isValidFormat(null));
		assertTrue(StoreHoursUtil.isValidFormat(""));
		assertTrue(StoreHoursUtil.isValidFormat("   "));

		// 비정상 케이스
		assertFalse(StoreHoursUtil.isValidFormat("~"));
		assertFalse(StoreHoursUtil.isValidFormat("09:00 ~"));
		assertFalse(StoreHoursUtil.isValidFormat("~ 21:00"));
		assertFalse(StoreHoursUtil.isValidFormat("오전 9시 ~ 오후 9시"));
		assertFalse(StoreHoursUtil.isValidFormat("09:00 - 21:00"));
		assertFalse(StoreHoursUtil.isValidFormat("25:00 ~ 26:00"));
	}

	@Test
	@DisplayName("마감 세일 시작 시각 파싱 테스트")
	void parseSaleStartTimeCases() {
		assertEquals(LocalTime.of(19, 0), StoreHoursUtil.parseSaleStartTime("09:00 ~ 21:00 (마감 세일 19:00~)"));
		assertEquals(LocalTime.of(20, 30), StoreHoursUtil.parseSaleStartTime("09:00 ~ 22:00 (마감세일 20:30)"));
		assertEquals(LocalTime.of(20, 0), StoreHoursUtil.parseSaleStartTime("09:00 ~ 21:00 (마감 세일 : 20:00)"));
		assertNull(StoreHoursUtil.parseSaleStartTime("09:00 ~ 21:00"));
		assertNull(StoreHoursUtil.parseSaleStartTime(null));
	}

	@Test
	@DisplayName("영업 시작 및 마감 시간 포맷 조합 테스트")
	void formatOperatingHoursCases() {
		assertEquals("09:00 ~ 21:00",
				StoreHoursUtil.formatOperatingHours(LocalTime.of(9, 0), LocalTime.of(21, 0), null));

		assertEquals("09:00 ~ 21:00 (마감 세일 20:00~)",
				StoreHoursUtil.formatOperatingHours(LocalTime.of(9, 0), LocalTime.of(21, 0), LocalTime.of(20, 0)));

		assertNull(StoreHoursUtil.formatOperatingHours(null, LocalTime.of(21, 0), null));
		assertNull(StoreHoursUtil.formatOperatingHours(LocalTime.of(9, 0), null, null));
	}
}

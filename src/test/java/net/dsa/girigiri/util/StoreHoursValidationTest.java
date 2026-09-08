package net.dsa.girigiri.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

class StoreHoursValidationTest {

	@Test
	@DisplayName("OperatingHoursUtil이 StoreHoursUtil과 동일하게 정상 위임 동작하는지 확인")
	void operatingHoursUtilDelegation() {
		assertEquals(LocalTime.of(22, 0), OperatingHoursUtil.parseClosingTime("09:00 ~ 22:00"));
		assertEquals(LocalTime.of(21, 30), OperatingHoursUtil.parseClosingTime("09:00 ~ 21:30 (마감 세일 20:00~)"));
		assertThrows(IllegalArgumentException.class, () -> OperatingHoursUtil.parseClosingTime("invalid"));
		assertThrows(IllegalArgumentException.class, () -> OperatingHoursUtil.parseClosingTime(null));
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

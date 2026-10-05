package net.dsa.girigiri.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 주소 문자열에서 시도를 뽑는 SidoParser 확인 (2026-09-30, 통계 대시보드 "지역별 현황").
 */
class SidoParserTest {

	@Test
	void 실제_시드데이터_형식인_서울시_접두사를_인식한다() {
		assertEquals("서울", SidoParser.parse("서울시 용산구 한강대로 88"));
	}

	@Test
	void 정식_행정명칭도_인식한다() {
		assertEquals("경기", SidoParser.parse("경기도 성남시 분당구 판교역로 1"));
		assertEquals("전북", SidoParser.parse("전라북도 전주시 완산구 ..."));
		assertEquals("경남", SidoParser.parse("경상남도 창원시 ..."));
	}

	@Test
	void 경기도_광주시가_광주광역시로_잘못_매칭되지_않는다() {
		assertEquals("경기", SidoParser.parse("경기도 광주시 경안로 10"));
	}

	@Test
	void 광역시_약칭도_인식한다() {
		assertEquals("부산", SidoParser.parse("부산광역시 해운대구 ..."));
		assertEquals("광주", SidoParser.parse("광주광역시 동구 ..."));
	}

	@Test
	void 매칭되는_시도가_없으면_null이다() {
		assertNull(SidoParser.parse("알 수 없는 주소 형식"));
	}

	@Test
	void 주소가_null이면_null이다() {
		assertNull(SidoParser.parse(null));
	}
}

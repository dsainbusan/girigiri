package net.dsa.girigiri.util;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * StoreEntity.address(자유텍스트, 예: "서울시 용산구 한강대로 88")에서 17개 시도 중 하나를 뽑는다.
 * (2026-09-30, 통계 대시보드 "지역별 현황" — StoreEntity에 구조화된 시도 컬럼이 없어서 추가.)
 *
 * 긴 표준 명칭을 먼저 검사해야 한다 — 순서를 안 지키면 "경기도 광주시"가 "광주"(광주광역시)로
 * 잘못 매칭될 수 있다(LinkedHashMap으로 검사 순서를 고정하는 이유).
 */
public class SidoParser {

	// 화면(지역 타일맵) 배치 순서 그대로 — RegionStatDto를 이 순서로 조회할 때도 재사용한다.
	public static final List<String> SIDO_LIST = List.of(
			"서울", "인천", "경기", "강원", "충북", "충남", "세종", "대전",
			"경북", "경남", "대구", "울산", "부산", "전북", "전남", "광주", "제주");

	private static final Map<String, String> PREFIX_TO_SIDO = new LinkedHashMap<>();

	static {
		PREFIX_TO_SIDO.put("서울특별시", "서울");
		PREFIX_TO_SIDO.put("서울시", "서울");
		PREFIX_TO_SIDO.put("서울", "서울");
		PREFIX_TO_SIDO.put("부산광역시", "부산");
		PREFIX_TO_SIDO.put("부산시", "부산");
		PREFIX_TO_SIDO.put("부산", "부산");
		PREFIX_TO_SIDO.put("대구광역시", "대구");
		PREFIX_TO_SIDO.put("대구시", "대구");
		PREFIX_TO_SIDO.put("대구", "대구");
		PREFIX_TO_SIDO.put("인천광역시", "인천");
		PREFIX_TO_SIDO.put("인천시", "인천");
		PREFIX_TO_SIDO.put("인천", "인천");
		PREFIX_TO_SIDO.put("광주광역시", "광주");
		PREFIX_TO_SIDO.put("광주시", "광주");
		PREFIX_TO_SIDO.put("대전광역시", "대전");
		PREFIX_TO_SIDO.put("대전시", "대전");
		PREFIX_TO_SIDO.put("대전", "대전");
		PREFIX_TO_SIDO.put("울산광역시", "울산");
		PREFIX_TO_SIDO.put("울산시", "울산");
		PREFIX_TO_SIDO.put("울산", "울산");
		PREFIX_TO_SIDO.put("세종특별자치시", "세종");
		PREFIX_TO_SIDO.put("세종시", "세종");
		PREFIX_TO_SIDO.put("세종", "세종");
		PREFIX_TO_SIDO.put("경기도", "경기");
		PREFIX_TO_SIDO.put("경기", "경기");
		PREFIX_TO_SIDO.put("강원특별자치도", "강원");
		PREFIX_TO_SIDO.put("강원도", "강원");
		PREFIX_TO_SIDO.put("강원", "강원");
		PREFIX_TO_SIDO.put("충청북도", "충북");
		PREFIX_TO_SIDO.put("충북", "충북");
		PREFIX_TO_SIDO.put("충청남도", "충남");
		PREFIX_TO_SIDO.put("충남", "충남");
		PREFIX_TO_SIDO.put("전북특별자치도", "전북");
		PREFIX_TO_SIDO.put("전라북도", "전북");
		PREFIX_TO_SIDO.put("전북", "전북");
		PREFIX_TO_SIDO.put("전라남도", "전남");
		PREFIX_TO_SIDO.put("전남", "전남");
		PREFIX_TO_SIDO.put("경상북도", "경북");
		PREFIX_TO_SIDO.put("경북", "경북");
		PREFIX_TO_SIDO.put("경상남도", "경남");
		PREFIX_TO_SIDO.put("경남", "경남");
		PREFIX_TO_SIDO.put("제주특별자치도", "제주");
		PREFIX_TO_SIDO.put("제주도", "제주");
		PREFIX_TO_SIDO.put("제주", "제주");
	}

	private SidoParser() {
	}

	/** 주소 맨 앞이 이 목록 중 하나로 시작하면 그 시도를 돌려준다. 못 찾으면 null. */
	public static String parse(String address) {
		if (address == null) {
			return null;
		}
		String trimmed = address.trim();
		for (Map.Entry<String, String> entry : PREFIX_TO_SIDO.entrySet()) {
			if (trimmed.startsWith(entry.getKey())) {
				return entry.getValue();
			}
		}
		return null;
	}
}

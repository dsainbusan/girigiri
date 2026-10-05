package net.dsa.girigiri.util;

/**
 * "등록 대비 판매 비율"(소진율) 판정 기준 — 2026-09-30 통계 대시보드 "지역별 현황"에서 처음 쓰고,
 * 2026-10-01 지역→매장 드릴다운 화면에서 매장 단위로도 똑같이 써서 공용 유틸로 뺐다(두 화면이
 * 같은 기준을 서로 다르게 구현해 어긋나는 걸 막기 위해). 기준: 60%↑ 양호 / 40~59% 보통 /
 * 40%미만 점검 필요 / 등록 0건이면 판정 불가("등록 없음"/"미진출", 소진율은 "-").
 */
public class SellThroughClassifier {

	public static final String TILE_GOOD = "tile--good";
	public static final String TILE_MID = "tile--mid";
	public static final String TILE_LOW = "tile--low";
	public static final String TILE_NONE = "tile--none";

	public record Result(Integer percent, String tileClass, String statusLabel) {
	}

	private SellThroughClassifier() {
	}

	/** noDataLabel: 등록 0건일 때 보여줄 라벨("미진출"=지역용, "등록 없음"=매장용 등 화면마다 다름). */
	public static Result classify(int registeredCount, int soldCount, String noDataLabel) {
		if (registeredCount == 0) {
			return new Result(null, TILE_NONE, noDataLabel);
		}
		int percent = (int) Math.round(100.0 * soldCount / registeredCount);
		if (percent >= 60) {
			return new Result(percent, TILE_GOOD, "양호");
		}
		if (percent >= 40) {
			return new Result(percent, TILE_MID, "보통");
		}
		return new Result(percent, TILE_LOW, "점검 필요");
	}
}

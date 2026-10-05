package net.dsa.girigiri.util;

/**
 * "등록 대비 판매 비율"(소진율) 판정 기준 — 2026-09-30 통계 대시보드 "지역별 현황"에서 처음 쓰고,
 * 2026-10-01 지역→매장 드릴다운 화면에서 매장 단위로도 똑같이 써서 공용 유틸로 뺐다(두 화면이
 * 같은 기준을 서로 다르게 구현해 어긋나는 걸 막기 위해).
 *
 * 변경됨 (2026-10-06) — 기존 3단계(60%↑양호/40~59%보통/40%미만점검필요) 기준을 "표본이 너무 적으면
 * 퍼센트 자체를 믿지 않는다"는 새 요구로 교체. 등록 건수가 minSampleSize 미만이면 소진율이 몇 %든
 * "표본 부족"으로만 표시하고(0건이면 특히 그렇다 — 분모가 0이면 "0%"가 아니라 판정 불가), 표본이
 * 충분한 것만 lowSellThroughPercent 미만/이상으로 "점검 필요"/"정상" 이분 판정한다. 기준값은
 * DashboardPolicy(5건, 20%)에서 받아 하드코딩하지 않는다.
 */
public class SellThroughClassifier {

	/** 소진율이 충분히 좋음(점검 불필요). */
	public static final String TILE_OK = "tile--ok";
	/** 표본은 충분한데 소진율이 낮아 점검이 필요함. */
	public static final String TILE_LOW = "tile--low";
	/** 표본(등록 건수)이 너무 적어 소진율 자체를 판단할 수 없음 — 0건 포함. */
	public static final String TILE_INSUFFICIENT = "tile--insufficient";
	/** classify()가 반환하지 않는다 — 매장 자체가 없는 지역("미진출") 전용으로 호출부가 직접 쓴다. */
	public static final String TILE_NONE = "tile--none";

	public record Result(Integer percent, String tileClass, String statusLabel) {
	}

	private SellThroughClassifier() {
	}

	/**
	 * @param minSampleSize          이 값 미만으로 등록됐으면 퍼센트와 무관하게 "표본 부족"
	 * @param lowSellThroughPercent  표본이 충분할 때, 이 % 미만이면 "점검 필요"
	 */
	public static Result classify(int registeredCount, int soldCount, int minSampleSize, int lowSellThroughPercent) {
		if (registeredCount == 0) {
			return new Result(null, TILE_INSUFFICIENT, "표본 부족");
		}
		int percent = (int) Math.round(100.0 * soldCount / registeredCount);
		if (registeredCount < minSampleSize) {
			return new Result(percent, TILE_INSUFFICIENT, "표본 부족");
		}
		if (percent < lowSellThroughPercent) {
			return new Result(percent, TILE_LOW, "점검 필요");
		}
		return new Result(percent, TILE_OK, "정상");
	}
}

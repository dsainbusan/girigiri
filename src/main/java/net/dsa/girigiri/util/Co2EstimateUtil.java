package net.dsa.girigiri.util;

import java.util.Map;

/**
 * 매장 카테고리별 1개당 CO2 절감 추정치 (2026-09-12, 문창호).
 * 원래 SalesReportService 안에 있던 표를 여기로 빼서, 마이페이지 절약 가계부(LedgerService)도
 * 점주 매출 리포트와 같은 기준으로 CO2 절감량을 계산하게 한다 — 무게를 개별 입력받지 않고
 * "카테고리 1개 = 대략 이 정도 무게"로 근사하는 방식은 그대로다(정밀한 실측치는 아님).
 */
public final class Co2EstimateUtil {

	private static final Map<String, Double> CO2_PER_ITEM_KG = Map.of(
			"베이커리", 0.3, "반찬", 0.6, "도시락", 1.0, "카페", 0.3,
			"음료", 0.3, "청과", 0.3, "정육", 1.5, "기타", 0.7);
	private static final double CO2_PER_ITEM_DEFAULT = 0.5;

	// 소나무 1그루의 연간 CO2 흡수량 추정치 — 뱃지 설명(ECO_20KG "소나무 한 그루")과
	// 환경 기여도 배너의 "나무 OO그루 효과" 환산에 같은 숫자를 쓰기 위해 여기 하나로 모아둔다.
	private static final double TREE_ANNUAL_ABSORPTION_KG = 20.0;

	private Co2EstimateUtil() {
	}

	/** category가 표에 없거나 null이면 기본치(0.5kg)를 쓴다. */
	public static double perItemKg(String category) {
		return CO2_PER_ITEM_KG.getOrDefault(category, CO2_PER_ITEM_DEFAULT);
	}

	/** CO2 절감량(kg)을 "나무 몇 그루를 심은 효과"로 환산 — 숫자만으로는 안 와닿는 걸 체감되게. */
	public static double treeEquivalent(double co2Kg) {
		return co2Kg / TREE_ANNUAL_ABSORPTION_KG;
	}
}

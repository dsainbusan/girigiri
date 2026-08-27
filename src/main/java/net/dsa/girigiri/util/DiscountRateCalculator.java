package net.dsa.girigiri.util;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 추가됨 (2026-08-21) — 왜: 할인율 자동 계산 로직 (WBS 3.0, 문창호 담당).
 * 마감까지 남은 시간이 짧을수록 할인율을 높게 잡는다. URGENT_THRESHOLD_MINUTES(60분)는
 * ProductController가 "마감임박" 뱃지를 띄우는 기준과 동일하게 맞춰서, 화면에 "마감임박"이라고
 * 뜨는 상품은 항상 URGENT_RATE(50%) 할인이 걸리도록 정책과 표시가 어긋나지 않게 했다.
 *
 * 정책 (2026-08-27 팀 확정): 자동값 20/30/50. 점주는 메뉴별로 할인율을 지정해서 "더 깎을" 수는
 * 있지만 자동값보다 "덜 깎을" 수는 없다(effectiveRate가 자동값을 하한으로 끌어올린다). 원가(정가)는
 * POS 연동값으로 고정 — 점주가 못 바꾼다("가짜 할인" 방지).
 */
public final class DiscountRateCalculator {

	private static final int DEFAULT_RATE = 20;   // 마감까지 3시간 넘게 남음
	private static final int MID_RATE = 30;       // 마감까지 1~3시간
	private static final int URGENT_RATE = 50;    // 마감까지 1시간 이내 (또는 이미 마감)
	private static final int MAX_RATE = 90;       // 점주 override 상한

	private DiscountRateCalculator() {
	}

	/** closeAt이 null이면(영업시간 정보가 없거나 파싱 실패) 기본 할인율을 쓴다. */
	public static int calculateRate(LocalDateTime closeAt) {
		if (closeAt == null) {
			return DEFAULT_RATE;
		}
		long minutes = Duration.between(LocalDateTime.now(), closeAt).toMinutes();
		if (minutes <= 60) {
			return URGENT_RATE;
		}
		if (minutes <= 180) {
			return MID_RATE;
		}
		return DEFAULT_RATE;
	}

	/**
	 * 점주가 지정한 할인율(ownerRate)을 실제 적용값으로 정규화한다.
	 * - null이면 자동값
	 * - 자동값보다 낮으면 자동값으로 끌어올림 ("덜 깎기" 금지)
	 * - MAX_RATE(90%)로 상한
	 */
	public static int effectiveRate(Integer ownerRate, LocalDateTime closeAt) {
		int auto = calculateRate(closeAt);
		if (ownerRate == null) {
			return auto;
		}
		return Math.min(MAX_RATE, Math.max(auto, ownerRate));
	}

	public static int applyDiscount(int originalPrice, int rate) {
		return (int) Math.round(originalPrice * (100 - rate) / 100.0);
	}
}

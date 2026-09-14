package net.dsa.girigiri.domain;

import lombok.Getter;
import net.dsa.girigiri.util.Co2EstimateUtil;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 절약 가계부 뱃지 및 업적 정의 Enum.
 *
 * 18종의 뱃지(구제, 절약, 환경, 취향·탐험, 랭킹)를 정의하며,
 * 향후 새로운 뱃지가 추가될 때도 이 Enum에 상수 1줄만 추가하면 자동으로 도감에 반영된다.
 */
@Getter
public enum Badge {

	// ===== 1. 구제(Rescue) =====
	RESCUE_1("RESCUE_1", "구제 첫걸음", "🌱", "구제",
			"마감 직전 음식을 처음으로 구제했어요!", "픽업 완료 1회 이상",
			stats -> stats.rescuedCount() >= 1,
			stats -> new Progress(stats.rescuedCount() >= 1 ? 100 : 0, stats.rescuedCount() + " / 1개")),

	RESCUE_5("RESCUE_5", "골목길 구조대", "🦸", "구제",
			"동네 가게의 잔반 제로를 돕는 든든한 이웃", "픽업 완료 누적 5개 이상",
			stats -> stats.rescuedCount() >= 5,
			stats -> new Progress(Math.min(100, stats.rescuedCount() * 100 / 5), stats.rescuedCount() + " / 5개")),

	RESCUE_20("RESCUE_20", "푸드 히어로", "🛡️", "구제",
			"음식 20개를 버려지지 않게 지켜낸 진정한 영웅", "픽업 완료 누적 20개 이상",
			stats -> stats.rescuedCount() >= 20,
			stats -> new Progress(Math.min(100, stats.rescuedCount() * 100 / 20), stats.rescuedCount() + " / 20개")),

	RESCUE_50("RESCUE_50", "전설의 구제왕", "👑", "구제",
			"동네 사장님들이 얼굴만 봐도 반기는 구제 마스터", "픽업 완료 누적 50개 이상",
			stats -> stats.rescuedCount() >= 50,
			stats -> new Progress(Math.min(100, stats.rescuedCount() * 100 / 50), stats.rescuedCount() + " / 50개")),

	// ===== 2. 절약(Savings) =====
	SAVE_10K("SAVE_10K", "티끌 모아 태산", "🪙", "절약",
			"첫 만 원을 아끼며 현명한 소비 습관 시작!", "누적 절약 10,000원 이상",
			stats -> stats.totalSaved() >= 10000,
			stats -> new Progress(Math.min(100, (int) Math.round(stats.totalSaved() * 100.0 / 10000)),
					String.format("%,d / 10,000원", Math.min(stats.totalSaved(), 10000)))),

	SAVE_50K("SAVE_50K", "프로 짠테크러", "💵", "절약",
			"5만 원 아끼고 통장 잔고 지켜낸 알뜰 고수", "누적 절약 50,000원 이상",
			stats -> stats.totalSaved() >= 50000,
			stats -> new Progress(Math.min(100, (int) Math.round(stats.totalSaved() * 100.0 / 50000)),
					String.format("%,d / 50,000원", Math.min(stats.totalSaved(), 50000)))),

	SAVE_100K("SAVE_100K", "절약의 신", "🏆", "절약",
			"10만 원 이상 아낀 진정한 절약 마스터", "누적 절약 100,000원 이상",
			stats -> stats.totalSaved() >= 100000,
			stats -> new Progress(Math.min(100, (int) Math.round(stats.totalSaved() * 100.0 / 100000)),
					String.format("%,d / 100,000원", Math.min(stats.totalSaved(), 100000)))),

	GOAL_HIT("GOAL_HIT", "목표 저격수", "🎯", "절약",
			"내가 세운 이달의 절약 목표를 멋지게 달성했어요!", "월간 절약 목표 100% 달성",
			stats -> stats.goalPercent() >= 100,
			stats -> new Progress(Math.min(100, stats.goalPercent()), stats.goalPercent() + "% 달성")),

	// ===== 3. 환경(Eco) =====
	ECO_5KG("ECO_5KG", "초록 발자국", "🍃", "환경",
			"음식물 쓰레기를 줄여 탄소 배출을 막았어요", "CO2 5kg 이상 절감",
			stats -> stats.co2Kg() >= 5.0,
			stats -> new Progress(Math.min(100, (int) Math.round(stats.co2Kg() * 100.0 / 5.0)),
					String.format("%.1f / 5.0kg", Math.min(stats.co2Kg(), 5.0)))),

	ECO_20KG("ECO_20KG", "소나무 한 그루", "🌲", "환경",
			"소나무 1그루의 연간 흡수량만큼 탄소를 줄였어요", "CO2 20kg 이상 절감",
			stats -> stats.co2Kg() >= 20.0,
			stats -> new Progress(Math.min(100, (int) Math.round(stats.co2Kg() * 100.0 / 20.0)),
					String.format("%.1f / 20.0kg", Math.min(stats.co2Kg(), 20.0)))),

	ECO_50KG("ECO_50KG", "숲의 수호자", "🌳", "환경",
			"나무 여러 그루를 심은 것과 같은 큰 변화를 만들었어요", "CO2 50kg 이상 절감",
			stats -> stats.co2Kg() >= 50.0,
			stats -> new Progress(Math.min(100, (int) Math.round(stats.co2Kg() * 100.0 / 50.0)),
					String.format("%.1f / 50.0kg (나무 %.1f그루)", Math.min(stats.co2Kg(), 50.0),
							Co2EstimateUtil.treeEquivalent(Math.min(stats.co2Kg(), 50.0))))),

	// ===== 4. 취향·탐험(Taste & Explorer) =====
	BAKERY_LOVER("BAKERY_LOVER", "빵에 진심인 자", "🥐", "취향·탐험",
			"오늘 밤도 빵 굽는 냄새에 이끌려 마감 빵 겟!", "베이커리 카테고리 3회 이상",
			stats -> stats.getCategoryCount("베이커리") >= 3,
			stats -> new Progress(Math.min(100, stats.getCategoryCount("베이커리") * 100 / 3),
					stats.getCategoryCount("베이커리") + " / 3개")),

	LUNCHBOX_HERO("LUNCHBOX_HERO", "도시락 해결사", "🍱", "취향·탐험",
			"바쁜 일상 속 가성비 든든한 한 끼 마스터", "도시락/반찬 카테고리 3회 이상",
			stats -> (stats.getCategoryCount("도시락") + stats.getCategoryCount("반찬")) >= 3,
			stats -> {
				int count = stats.getCategoryCount("도시락") + stats.getCategoryCount("반찬");
				return new Progress(Math.min(100, count * 100 / 3), count + " / 3개");
			}),

	NIGHT_OWL("NIGHT_OWL", "올빼미 구제단", "🌙", "취향·탐험",
			"밤이 깊을수록 나의 구제 본능은 날카로워진다!", "20시 이후 픽업 1회 이상",
			BadgeStats::hasNightPickup,
			stats -> new Progress(stats.hasNightPickup() ? 100 : 0, stats.hasNightPickup() ? "달성 완료" : "0 / 1회")),

	EXPLORER_3("EXPLORER_3", "동네 마당발", "🗺️", "취향·탐험",
			"한 가게에 머물지 않고 동네 골목 상권을 두루 섭렵!", "서로 다른 매장 3곳 이상 방문",
			stats -> stats.distinctStoreCount() >= 3,
			stats -> new Progress(Math.min(100, stats.distinctStoreCount() * 100 / 3),
					stats.distinctStoreCount() + " / 3곳")),

	// ===== 5. 랭킹(Ranking) =====
	// 이 3개는 한 유저의 BadgeStats만 보고는 판정할 수 없다(다른 유저들과 비교해야 아는 값이라서) —
	// condition은 항상 false로 두고, 매달 1일 RankingBadgeScheduler가 지난달 TOP3를 직접
	// user_badge에 심어준다(LedgerService.awardMonthlyRankBadges 참고). 도감에는 "잠김"으로
	// 정상 노출되고, 조건 설명은 안내 문구로만 보여준다.
	RANK_1("RANK_1", "월간 랭킹 챔피언", "🥇", "랭킹",
			"지난달 절약 랭킹 1위! 동네에서 제일가는 알뜰 고수예요", "지난달 절약 랭킹 1위 (매달 1일 자동 집계)",
			stats -> false,
			stats -> new Progress(0, "매달 1일, 지난달 랭킹 1위에게 자동 지급돼요")),

	RANK_2("RANK_2", "월간 랭킹 준우승", "🥈", "랭킹",
			"지난달 절약 랭킹 2위를 달성했어요", "지난달 절약 랭킹 2위 (매달 1일 자동 집계)",
			stats -> false,
			stats -> new Progress(0, "매달 1일, 지난달 랭킹 2위에게 자동 지급돼요")),

	RANK_3("RANK_3", "월간 랭킹 TOP3", "🥉", "랭킹",
			"지난달 절약 랭킹 3위 안에 들었어요", "지난달 절약 랭킹 3위 (매달 1일 자동 집계)",
			stats -> false,
			stats -> new Progress(0, "매달 1일, 지난달 랭킹 3위 안에 들면 자동 지급돼요"));

	private final String code;
	private final String name;
	private final String icon;
	private final String category;
	private final String description;
	private final String conditionLabel;
	private final Predicate<BadgeStats> condition;
	private final Function<BadgeStats, Progress> progressCalculator;

	Badge(String code, String name, String icon, String category, String description,
	      String conditionLabel, Predicate<BadgeStats> condition,
	      Function<BadgeStats, Progress> progressCalculator) {
		this.code = code;
		this.name = name;
		this.icon = icon;
		this.category = category;
		this.description = description;
		this.conditionLabel = conditionLabel;
		this.condition = condition;
		this.progressCalculator = progressCalculator;
	}

	public boolean isUnlocked(BadgeStats stats) {
		return condition.test(stats);
	}

	public Progress calculateProgress(BadgeStats stats) {
		return progressCalculator.apply(stats);
	}

	public static Optional<Badge> findByCode(String code) {
		if (code == null || code.isBlank()) {
			return Optional.empty();
		}
		return Arrays.stream(values())
				.filter(b -> b.getCode().equalsIgnoreCase(code.trim()))
				.findFirst();
	}

	public record Progress(int percent, String text) {}
}

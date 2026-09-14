package net.dsa.girigiri;

import net.dsa.girigiri.domain.Badge;
import net.dsa.girigiri.domain.BadgeStats;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BadgeTest {

	@Test
	@DisplayName("구제 관련 뱃지 달성 조건 및 진행률 검증")
	void rescueBadgesEvaluation() {
		BadgeStats stats0 = new BadgeStats(0, 0, 0.0, 0, Map.of(), 0, false);
		assertFalse(Badge.RESCUE_1.isUnlocked(stats0));
		assertEquals(0, Badge.RESCUE_1.calculateProgress(stats0).percent());

		BadgeStats stats3 = new BadgeStats(3, 1000, 1.0, 10, Map.of(), 1, false);
		assertTrue(Badge.RESCUE_1.isUnlocked(stats3));
		assertFalse(Badge.RESCUE_5.isUnlocked(stats3));
		assertEquals(60, Badge.RESCUE_5.calculateProgress(stats3).percent());
		assertEquals("3 / 5개", Badge.RESCUE_5.calculateProgress(stats3).text());

		BadgeStats stats25 = new BadgeStats(25, 50000, 10.0, 50, Map.of(), 2, false);
		assertTrue(Badge.RESCUE_5.isUnlocked(stats25));
		assertTrue(Badge.RESCUE_20.isUnlocked(stats25));
		assertFalse(Badge.RESCUE_50.isUnlocked(stats25));
		assertEquals(50, Badge.RESCUE_50.calculateProgress(stats25).percent());
	}

	@Test
	@DisplayName("절약 관련 뱃지 달성 조건 검증")
	void savingsBadgesEvaluation() {
		BadgeStats stats = new BadgeStats(2, 45000, 1.0, 100, Map.of(), 1, false);
		assertTrue(Badge.SAVE_10K.isUnlocked(stats));
		assertFalse(Badge.SAVE_50K.isUnlocked(stats));
		assertTrue(Badge.GOAL_HIT.isUnlocked(stats));

		BadgeStats stats120k = new BadgeStats(10, 120000, 5.0, 120, Map.of(), 2, false);
		assertTrue(Badge.SAVE_50K.isUnlocked(stats120k));
		assertTrue(Badge.SAVE_100K.isUnlocked(stats120k));
	}

	@Test
	@DisplayName("환경 및 취향·탐험 뱃지 달성 조건 검증")
	void ecoAndTasteBadgesEvaluation() {
		BadgeStats stats = new BadgeStats(
				6,
				20000,
				6.5,
				80,
				Map.of("베이커리", 3, "도시락", 2, "반찬", 1),
				3,
				true
		);

		// 환경
		assertTrue(Badge.ECO_5KG.isUnlocked(stats));
		assertFalse(Badge.ECO_20KG.isUnlocked(stats));
		assertFalse(Badge.ECO_50KG.isUnlocked(stats));

		// 취향·탐험
		assertTrue(Badge.BAKERY_LOVER.isUnlocked(stats));
		assertTrue(Badge.LUNCHBOX_HERO.isUnlocked(stats));
		assertTrue(Badge.NIGHT_OWL.isUnlocked(stats));
		assertTrue(Badge.EXPLORER_3.isUnlocked(stats));
	}

	@Test
	@DisplayName("ECO_50KG는 50kg 이상부터 해금되고, 진행률 텍스트에 나무 환산 그루 수가 포함된다")
	void eco50kgBadgeEvaluation() {
		BadgeStats stats49 = new BadgeStats(30, 100000, 49.9, 50, Map.of(), 2, false);
		assertFalse(Badge.ECO_50KG.isUnlocked(stats49));

		BadgeStats stats60 = new BadgeStats(40, 150000, 60.0, 100, Map.of(), 3, false);
		assertTrue(Badge.ECO_50KG.isUnlocked(stats60));

		// 진행률 텍스트("X / 50.0kg (나무 Y그루)")에 나무 환산이 들어가는지 확인 — 20kg = 1그루 기준
		String text = Badge.ECO_50KG.calculateProgress(stats49).text();
		assertTrue(text.contains("그루"));
	}

	@Test
	@DisplayName("Badge.findByCode 대소문자 무관 검색 검증")
	void findByCodeTest() {
		assertTrue(Badge.findByCode("RESCUE_1").isPresent());
		assertTrue(Badge.findByCode("rescue_1").isPresent());
		assertEquals(Badge.BAKERY_LOVER, Badge.findByCode("bakery_lover").orElseThrow());
		assertTrue(Badge.findByCode("INVALID_CODE").isEmpty());
		assertTrue(Badge.findByCode(null).isEmpty());
	}

	@Test
	@DisplayName("랭킹 뱃지(RANK_1/2/3)는 한 유저의 통계만으로는 절대 해금되지 않는다 — 스케줄러가 직접 지급해야 한다")
	void rankBadgesNeverUnlockFromStatsAlone() {
		// 통계를 아무리 극단적으로 좋게 잡아도(구제 100개, 절약 100만원 등) RANK_1/2/3은 항상 잠김이어야 한다 —
		// "다른 유저와 비교"가 필요한 값이라 BadgeStats 하나만으로는 판정할 수 없기 때문.
		BadgeStats extremeStats = new BadgeStats(100, 1_000_000, 100.0, 100, Map.of(), 10, true);
		assertFalse(Badge.RANK_1.isUnlocked(extremeStats));
		assertFalse(Badge.RANK_2.isUnlocked(extremeStats));
		assertFalse(Badge.RANK_3.isUnlocked(extremeStats));
	}
}

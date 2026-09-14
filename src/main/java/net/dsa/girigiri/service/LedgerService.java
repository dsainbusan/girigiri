package net.dsa.girigiri.service;

import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.Badge;
import net.dsa.girigiri.domain.BadgeStats;
import net.dsa.girigiri.domain.dto.BadgeDto;
import net.dsa.girigiri.domain.dto.LedgerData;
import net.dsa.girigiri.domain.dto.RankingData;
import net.dsa.girigiri.domain.entity.ProductEntity;
import net.dsa.girigiri.domain.entity.ReservationEntity;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.domain.entity.UserBadgeEntity;
import net.dsa.girigiri.domain.entity.UserEntity;
import net.dsa.girigiri.repository.ProductRepository;
import net.dsa.girigiri.repository.ReservationRepository;
import net.dsa.girigiri.repository.StoreRepository;
import net.dsa.girigiri.repository.UserBadgeRepository;
import net.dsa.girigiri.repository.UserRepository;
import net.dsa.girigiri.util.Co2EstimateUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 마이페이지 절약 가계부 집계 (WBS 6.0, 문창호).
 *
 * "절약"의 기준: 픽업 완료(status=picked)한 예약만 센다. 결제만 하고 노쇼/취소된 건 실제로
 * 구제한 게 아니라서 뺀다. 절약액 = (상품 정상가 × 수량) − 실제 결제액(totalPrice, 쿠폰 할인 등
 * 포함) — Reservation엔 정상가 스냅샷이 없어서 매번 ProductEntity를 조인해서 구한다. 상품이
 * 삭제돼서 조인이 안 되면 그 건은 절약액 0으로 처리한다(구제 개수엔 그대로 포함).
 *
 * 이 서비스는 조회 전용(목표/대표뱃지 설정만 예외)이라 화면·Excel·PDF가 항상 같은 숫자를 보게 build() 하나로
 * 모은다.
 */
@Service
@RequiredArgsConstructor
public class LedgerService {

	private static final List<String> PICKED = List.of("picked");
	private static final int[] TIER_THRESHOLDS = {5, 20, 50, 100};
	private static final String[] TIER_NAMES = {"새싹", "브론즈", "실버", "골드", "플래티넘"};
	private static final String[] TIER_EMOJIS = {"🌱", "🥉", "🥈", "🥇", "💎"};
	private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy.MM.dd");

	private final ReservationRepository reservationRepository;
	private final ProductRepository productRepository;
	private final StoreRepository storeRepository;
	private final UserRepository userRepository;
	private final UserBadgeRepository userBadgeRepository;

	/** 한 건의 구제 실적으로 정규화한 내부 계산용 값. */
	private record RescueLine(LocalDate date, int hour, Long storeId, String storeName, String category,
	                          String productName, int quantity, int paidTotal, int originalTotal) {
		int saved() {
			return Math.max(0, originalTotal - paidTotal);
		}
	}

	// readOnly가 아닌 이유: 뱃지를 새로 해금했을 때 이 메서드 안에서 user_badge에 영구 기록을
	// 남긴다(아래 badges 계산부 참고) — 조회 메서드지만 "처음 본 순간 달성 기록을 남긴다"는
	// 성격상 쓰기가 같이 일어난다.
	@Transactional
	public LedgerData build(Long userId) {
		List<RescueLine> lines = loadLines(userId);

		YearMonth thisMonth = YearMonth.now();
		YearMonth lastMonth = thisMonth.minusMonths(1);
		int thisMonthSaved = sumSavedInMonth(lines, thisMonth);
		int lastMonthSaved = sumSavedInMonth(lines, lastMonth);
		int deltaAmount = thisMonthSaved - lastMonthSaved;
		Integer deltaPercent = lastMonthSaved > 0
				? (int) Math.round(100.0 * deltaAmount / lastMonthSaved)
				: null;

		int totalSaved = lines.stream().mapToInt(RescueLine::saved).sum();
		int totalOriginal = lines.stream().mapToInt(RescueLine::originalTotal).sum();
		int rescueRatePercent = totalOriginal > 0 ? (int) Math.round(100.0 * totalSaved / totalOriginal) : 0;

		UserEntity user = userRepository.findById(userId).orElseThrow();
		Integer goalAmount = user.getSavingsGoalAmount();
		// goalPercent는 게이지 폭(최대 100%)이랑 뱃지 판정에 쓰니까 100으로 캡한다. 캡 전 실제 퍼센트는
		// 초과 달성 메시지("목표보다 23% 더 아꼈어요!")에 따로 써야 해서 goalOverPercent로 분리한다.
		int goalRawPercent = (goalAmount != null && goalAmount > 0)
				? (int) Math.round(100.0 * thisMonthSaved / goalAmount)
				: 0;
		int goalPercent = Math.min(100, goalRawPercent);
		int goalOverPercent = Math.max(0, goalRawPercent - 100);

		List<Integer> monthlySaved = new ArrayList<>();
		List<String> monthlyLabel = new ArrayList<>();
		List<String> monthlyKeys = new ArrayList<>();
		for (int i = 5; i >= 0; i--) {
			YearMonth ym = thisMonth.minusMonths(i);
			monthlyLabel.add(ym.getMonthValue() + "월");
			monthlyKeys.add(String.format("%04d.%02d", ym.getYear(), ym.getMonthValue()));
			monthlySaved.add(sumSavedInMonth(lines, ym));
		}
		int maxMonthly = monthlySaved.stream().mapToInt(Integer::intValue).max().orElse(0);
		List<LedgerData.MonthPoint> monthly = new ArrayList<>();
		for (int i = 0; i < monthlySaved.size(); i++) {
			int saved = monthlySaved.get(i);
			int height = maxMonthly > 0 ? Math.max(saved > 0 ? 6 : 0, (int) Math.round(100.0 * saved / maxMonthly)) : 0;
			boolean isCurrent = (i == monthlySaved.size() - 1);
			monthly.add(new LedgerData.MonthPoint(monthlyLabel.get(i), saved, height, isCurrent, monthlyKeys.get(i)));
		}

		List<LedgerData.HistoryRow> history = lines.stream()
				.map(l -> new LedgerData.HistoryRow(l.date().format(DATE_FMT), l.storeName(), l.productName(),
						l.quantity(), l.originalTotal(), l.paidTotal(), l.saved()))
				.toList();

		List<LedgerData.CategoryRow> categories = buildCategoryBreakdown(lines, totalSaved);

		int rescuedCount = lines.stream().mapToInt(RescueLine::quantity).sum();
		double co2Kg = lines.stream()
				.mapToDouble(l -> l.quantity() * Co2EstimateUtil.perItemKg(l.category()))
				.sum();

		// 환경 기여도 배너("이번 달 N개 구제 · CO2 X.Xkg 절감")용 — 위 rescuedCount/co2Kg와 달리 누적이 아니라 이번 달만.
		List<RescueLine> thisMonthLines = lines.stream()
				.filter(l -> YearMonth.from(l.date()).equals(thisMonth))
				.toList();
		int thisMonthRescuedCount = thisMonthLines.stream().mapToInt(RescueLine::quantity).sum();
		double thisMonthCo2Kg = thisMonthLines.stream()
				.mapToDouble(l -> l.quantity() * Co2EstimateUtil.perItemKg(l.category()))
				.sum();

		int tierIndex = 0;
		for (int threshold : TIER_THRESHOLDS) {
			if (rescuedCount >= threshold) {
				tierIndex++;
			}
		}
		int nextTierAt = tierIndex < TIER_THRESHOLDS.length ? TIER_THRESHOLDS[tierIndex] : -1;

		// 뱃지 및 업적 통계 계산
		BadgeStats badgeStats = calculateBadgeStats(lines, goalPercent);
		String userRepBadgeCode = user.getRepresentativeBadge();

		// 뱃지 해금 상태는 "지금 조건을 만족하는지"가 아니라 "지금까지 한 번이라도 만족한 적이 있는지"로
		// 판정한다. GOAL_HIT(이달 목표 달성률)처럼 시간이 지나며 다시 거짓이 될 수 있는 조건도 있어서,
		// 매번 실시간 조건만 보면 이미 딴 뱃지가 다음 달에 다시 잠기는 문제가 있었다. 그래서 조건을
		// 처음 만족한 시점에 user_badge에 기록해두고, 이후로는 그 기록의 존재만으로 해금 여부를 본다.
		List<UserBadgeEntity> existingBadgeRecords = userBadgeRepository.findByUserId(userId);
		Set<String> earnedCodes = existingBadgeRecords.stream()
				.map(UserBadgeEntity::getBadgeCode)
				.collect(Collectors.toCollection(HashSet::new));

		// RANK_1/2/3처럼 이 유저 혼자만의 통계로는 판정 불가능한 뱃지는 여기서 절대 새로 해금되지
		// 않는다(Badge.condition이 항상 false) — RankingBadgeScheduler가 직접 user_badge에 심어준다.
		List<UserBadgeEntity> newlyEarned = new ArrayList<>();
		LocalDateTime now = LocalDateTime.now();
		for (Badge badge : Badge.values()) {
			if (badge.isUnlocked(badgeStats) && !earnedCodes.contains(badge.getCode())) {
				earnedCodes.add(badge.getCode());
				// notified=true로 바로 만든다 — 이번 응답의 토스트로 바로 보여줄 거라 "안 보여준 상태"로
				// 남겨둘 필요가 없다(아래 newlyUnlockedBadges 계산부 참고).
				newlyEarned.add(UserBadgeEntity.builder()
						.userId(userId).badgeCode(badge.getCode()).earnedAt(now).notified(true).build());
			}
		}
		if (!newlyEarned.isEmpty()) {
			userBadgeRepository.saveAll(newlyEarned);
		}

		// 뱃지 카드에 "OOOO.MM.DD 달성"을 보여주기 위한 코드 -> 획득일시. existingBadgeRecords와
		// newlyEarned를 합치면 이 유저가 가진 모든 뱃지의 획득 시각을 알 수 있다.
		Map<String, LocalDateTime> earnedAtByCode = new HashMap<>();
		existingBadgeRecords.forEach(e -> earnedAtByCode.put(e.getBadgeCode(), e.getEarnedAt()));
		newlyEarned.forEach(e -> earnedAtByCode.put(e.getBadgeCode(), e.getEarnedAt()));

		List<BadgeDto> badges = new ArrayList<>();
		BadgeDto repBadgeDto = null;

		for (Badge badge : Badge.values()) {
			boolean unlocked = earnedCodes.contains(badge.getCode());
			Badge.Progress progress = badge.calculateProgress(badgeStats);
			boolean isRep = unlocked && badge.getCode().equalsIgnoreCase(userRepBadgeCode);
			LocalDateTime earnedAt = earnedAtByCode.get(badge.getCode());
			String earnedDateLabel = (unlocked && earnedAt != null) ? earnedAt.format(DATE_FMT) + " 달성" : null;
			BadgeDto dto = new BadgeDto(
					badge.getCode(),
					badge.getName(),
					badge.getIcon(),
					badge.getCategory(),
					badge.getDescription(),
					badge.getConditionLabel(),
					unlocked,
					progress.percent(),
					unlocked ? "달성 완료" : progress.text(),
					isRep,
					earnedDateLabel
			);
			badges.add(dto);
			if (isRep) {
				repBadgeDto = dto;
			}
		}
		int unlockedCount = (int) badges.stream().filter(BadgeDto::unlocked).count();

		// "🎉 새 뱃지 획득!" 토스트 대상 = 아직 한 번도 안 보여준 뱃지 전부 —
		// (1) 방금 이 호출에서 라이브 스캔으로 새로 해금된 것(newlyEarned)
		// (2) RankingBadgeScheduler가 이전에 미리 심어뒀는데 이 유저가 아직 한 번도 못 본 것
		//     (existingBadgeRecords 중 notified=false). (2)를 여기서 notified=true로 바꿔두면
		//     @Transactional 안에서 조회된 영속 엔티티라 커밋 시 자동 반영된다(dirty checking).
		Set<String> pendingNotificationCodes = new HashSet<>();
		newlyEarned.forEach(e -> pendingNotificationCodes.add(e.getBadgeCode()));
		existingBadgeRecords.stream()
				.filter(e -> !e.isNotified())
				.forEach(e -> {
					pendingNotificationCodes.add(e.getBadgeCode());
					e.setNotified(true);
				});
		List<BadgeDto> newlyUnlockedBadges = badges.stream()
				.filter(b -> pendingNotificationCodes.contains(b.code()))
				.toList();

		// 획득한 뱃지를 우선적으로 앞으로 정렬
		badges.sort(Comparator.comparing(BadgeDto::unlocked).reversed());

		return new LedgerData(user.getNickname(), thisMonthSaved, deltaAmount, deltaPercent, totalSaved,
				rescueRatePercent, goalAmount, goalPercent, goalOverPercent, monthly, thisMonthRescuedCount, thisMonthCo2Kg,
				history, categories,
				rescuedCount, co2Kg, TIER_NAMES[tierIndex], TIER_EMOJIS[tierIndex], nextTierAt,
				repBadgeDto, badges, unlockedCount, Badge.values().length, newlyUnlockedBadges);
	}

	public enum GoalUpdateResult { SUCCESS, TOO_LOW }

	// 목표 프리셋 최소값(1만원)과 맞춘 하한선 — 없으면 목표를 1,000원처럼 아무 의미 없는 값으로 잡아서
	// 물건 하나만 픽업해도 바로 100% 달성 + GOAL_HIT 뱃지를 거저 따는 게 가능해진다(코드 감사, 2026-09-14).
	private static final int MIN_GOAL_AMOUNT = 10_000;

	/**
	 * 이번 달 절약 목표 설정. amount가 null이거나 0 이하면 목표를 해제(미설정)한다.
	 * 0보다 크지만 MIN_GOAL_AMOUNT 미만이면 저장하지 않고 TOO_LOW를 돌려준다.
	 */
	@Transactional
	public GoalUpdateResult updateGoal(Long userId, Integer amount) {
		if (amount != null && amount > 0 && amount < MIN_GOAL_AMOUNT) {
			return GoalUpdateResult.TOO_LOW;
		}
		UserEntity user = userRepository.findById(userId).orElseThrow();
		user.setSavingsGoalAmount(amount != null && amount > 0 ? amount : null);
		userRepository.save(user);
		return GoalUpdateResult.SUCCESS;
	}

	/**
	 * 대표 뱃지 설정.
	 * 비즈니스 로직: 사용자가 실제로 달성(해금)한 뱃지만 대표 뱃지로 등록할 수 있다.
	 * badgeCode가 null 또는 빈 문자열이면 대표 뱃지를 해제한다.
	 * 해금 여부는 user_badge의 영구 기록으로 판정한다(build()에서 처음 조건 충족 시 이미 기록됨) —
	 * 여기서 다시 통계를 재계산하지 않는 이유는 이달 목표 미달로 GOAL_HIT의 실시간 조건이 거짓이 되어도
	 * 한 번 딴 뱃지는 계속 대표 뱃지로 쓸 수 있어야 하기 때문이다.
	 */
	@Transactional
	public void updateRepresentativeBadge(Long userId, String badgeCode) {
		UserEntity user = userRepository.findById(userId).orElseThrow();
		if (badgeCode == null || badgeCode.isBlank()) {
			user.setRepresentativeBadge(null);
			userRepository.save(user);
			return;
		}

		Badge badge = Badge.findByCode(badgeCode)
				.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 뱃지입니다: " + badgeCode));

		if (!userBadgeRepository.existsByUserIdAndBadgeCode(userId, badge.getCode())) {
			throw new IllegalStateException("아직 달성하지 못한 뱃지는 대표 뱃지로 설정할 수 없습니다: " + badge.getName());
		}

		user.setRepresentativeBadge(badge.getCode());
		userRepository.save(user);
	}

	private BadgeStats calculateBadgeStats(List<RescueLine> lines, int goalPercent) {
		int rescuedCount = lines.stream().mapToInt(RescueLine::quantity).sum();
		int totalSaved = lines.stream().mapToInt(RescueLine::saved).sum();
		double co2Kg = lines.stream()
				.mapToDouble(l -> l.quantity() * Co2EstimateUtil.perItemKg(l.category()))
				.sum();

		Map<String, Integer> categoryCounts = new HashMap<>();
		Set<Long> storeIds = new HashSet<>();
		boolean hasNightPickup = false;

		for (RescueLine l : lines) {
			if (l.category() != null) {
				categoryCounts.put(l.category(), categoryCounts.getOrDefault(l.category(), 0) + l.quantity());
			}
			if (l.storeId() != null) {
				storeIds.add(l.storeId());
			}
			if (l.hour() >= 20) {
				hasNightPickup = true;
			}
		}

		return new BadgeStats(
				rescuedCount,
				totalSaved,
				co2Kg,
				goalPercent,
				categoryCounts,
				storeIds.size(),
				hasNightPickup
		);
	}

	private List<RescueLine> loadLines(Long userId) {
		List<ReservationEntity> reservations =
				reservationRepository.findByUserIdAndStatusInOrderByReservedAtDesc(userId, PICKED);
		if (reservations.isEmpty()) {
			return List.of();
		}

		Map<Long, ProductEntity> products = productRepository
				.findAllById(reservations.stream().map(ReservationEntity::getProductId).distinct().toList())
				.stream().collect(Collectors.toMap(ProductEntity::getId, p -> p));
		Map<Long, StoreEntity> stores = storeRepository
				.findAllById(reservations.stream().map(ReservationEntity::getStoreId).distinct().toList())
				.stream().collect(Collectors.toMap(StoreEntity::getId, s -> s));

		List<RescueLine> lines = new ArrayList<>();
		for (ReservationEntity r : reservations) {
			ProductEntity product = products.get(r.getProductId());
			StoreEntity store = stores.get(r.getStoreId());
			LocalDateTime eventTime = r.getPickedAt() != null ? r.getPickedAt() : r.getReservedAt();
			LocalDate date = eventTime.toLocalDate();
			int hour = eventTime.getHour();
			int quantity = r.getReservedQuantity() == null ? 0 : r.getReservedQuantity();
			int paid = r.getTotalPrice() == null ? 0 : r.getTotalPrice();
			// 상품이 삭제돼 조인이 안 되면 정상가를 알 수 없으니 절약 0(=원가를 결제액과 같다고 봄)으로.
			int original = (product != null && product.getOriginalPrice() != null)
					? product.getOriginalPrice() * quantity : paid;
			lines.add(new RescueLine(date, hour, r.getStoreId(),
					store != null ? store.getStoreName() : "매장",
					store != null ? store.getCategory() : null,
					r.getProductName(), quantity, paid, original));
		}
		return lines;
	}

	private int sumSavedInMonth(List<RescueLine> lines, YearMonth month) {
		return lines.stream()
				.filter(l -> YearMonth.from(l.date()).equals(month))
				.mapToInt(RescueLine::saved)
				.sum();
	}

	private List<LedgerData.CategoryRow> buildCategoryBreakdown(List<RescueLine> lines, int totalSaved) {
		// [0]=소비(결제액), [1]=절약액 — 카테고리 하나에 값 두 개를 같이 누적.
		Map<String, int[]> byCategory = new LinkedHashMap<>();
		for (RescueLine l : lines) {
			String category = l.category() != null ? l.category() : "기타";
			int[] sums = byCategory.computeIfAbsent(category, k -> new int[2]);
			sums[0] += l.paidTotal();
			sums[1] += l.saved();
		}
		return byCategory.entrySet().stream()
				.sorted((a, b) -> b.getValue()[1] - a.getValue()[1])
				.map(e -> new LedgerData.CategoryRow(e.getKey(), e.getValue()[0], e.getValue()[1],
						totalSaved > 0 ? (int) Math.round(100.0 * e.getValue()[1] / totalSaved) : 0))
				.toList();
	}

	/**
	 * 이번 달 절약 랭킹 (WBS 6.0). 히어로 카드의 "이번 달 절약"과 같은 기준(월간)으로 전체 유저를 줄세운다.
	 */
	@Transactional(readOnly = true)
	public RankingData buildRanking(Long userId) {
		YearMonth thisMonth = YearMonth.now();
		List<RankingData.RankingRow> allRows = computeMonthlyRanking(thisMonth);

		List<RankingData.RankingRow> top = allRows.stream().limit(10).toList();
		RankingData.RankingRow myRank = allRows.stream()
				.filter(row -> row.userId().equals(userId))
				.findFirst().orElse(null);
		boolean myRankInTop = myRank != null && myRank.rank() <= 10;

		return new RankingData(top, myRank, myRankInTop, allRows.size(), thisMonth);
	}

	/**
	 * 특정 달의 절약 랭킹 전체(순위 1위부터 꼴찌까지)를 계산한다 — buildRanking()과
	 * RankingBadgeScheduler(지난달 TOP3 뱃지 지급)가 공용으로 쓴다.
	 *
	 * 유저마다 반복 조회하지 않고, loadLines()와 동일한 패턴(예약 목록 1번 + product 배치조회 1번)으로
	 * 전체 유저의 그 달 픽업완료 예약을 한 번에 모아 자바에서 userId별로 합산한다 — 쿼리 수가 유저 수와
	 * 무관하게 고정된다.
	 */
	private List<RankingData.RankingRow> computeMonthlyRanking(YearMonth month) {
		LocalDateTime start = month.atDay(1).atStartOfDay();
		LocalDateTime end = month.atEndOfMonth().atTime(23, 59, 59);

		List<ReservationEntity> reservations =
				reservationRepository.findByStatusAndPickedAtBetween("picked", start, end);
		if (reservations.isEmpty()) {
			return List.of();
		}

		Map<Long, ProductEntity> products = productRepository
				.findAllById(reservations.stream().map(ReservationEntity::getProductId).distinct().toList())
				.stream().collect(Collectors.toMap(ProductEntity::getId, p -> p));

		Map<Long, Integer> savedByUser = new HashMap<>();
		// 절약액이 동점일 때 "먼저 그 금액을 채운 사람"을 위로 두기 위한 자료 — 새 컬럼 없이 기존
		// reservation.picked_at(마지막으로 절약액이 갱신된 시각)만으로 판정한다. loadLines()와 같은
		// 이유로 picked_at이 비어있으면 reservedAt으로 대체한다.
		Map<Long, LocalDateTime> lastEventAtByUser = new HashMap<>();
		for (ReservationEntity r : reservations) {
			ProductEntity product = products.get(r.getProductId());
			int quantity = r.getReservedQuantity() == null ? 0 : r.getReservedQuantity();
			int paid = r.getTotalPrice() == null ? 0 : r.getTotalPrice();
			int original = (product != null && product.getOriginalPrice() != null)
					? product.getOriginalPrice() * quantity : paid;
			savedByUser.merge(r.getUserId(), Math.max(0, original - paid), Integer::sum);

			LocalDateTime eventAt = r.getPickedAt() != null ? r.getPickedAt() : r.getReservedAt();
			lastEventAtByUser.merge(r.getUserId(), eventAt, (a, b) -> a.isAfter(b) ? a : b);
		}

		List<Map.Entry<Long, Integer>> sorted = savedByUser.entrySet().stream()
				.sorted((a, b) -> {
					int byAmount = b.getValue() - a.getValue(); // 절약액 내림차순
					if (byAmount != 0) {
						return byAmount;
					}
					// 동점이면 그 금액을 더 일찍 채운(마지막 픽업이 더 이른) 사람이 위.
					return lastEventAtByUser.get(a.getKey()).compareTo(lastEventAtByUser.get(b.getKey()));
				})
				.toList();

		Map<Long, UserEntity> users = userRepository
				.findAllById(sorted.stream().map(Map.Entry::getKey).toList())
				.stream().collect(Collectors.toMap(UserEntity::getId, u -> u));

		List<RankingData.RankingRow> allRows = new ArrayList<>();
		for (int i = 0; i < sorted.size(); i++) {
			Map.Entry<Long, Integer> e = sorted.get(i);
			UserEntity u = users.get(e.getKey());
			allRows.add(new RankingData.RankingRow(i + 1, e.getKey(),
					u != null ? u.getNickname() : "탈퇴한 사용자", e.getValue()));
		}
		return allRows;
	}

	// 랭킹 뱃지 순위(1~3위) → 뱃지 코드. RankingBadgeScheduler가 매달 1일에 이 순서로 지급한다.
	private static final String[] RANK_BADGE_CODES = {"RANK_1", "RANK_2", "RANK_3"};

	/**
	 * 지난달 절약 랭킹 TOP3에게 랭킹 뱃지(RANK_1/2/3)를 지급한다 (RankingBadgeScheduler, 매달 1일 00:00).
	 * 이미 그 뱃지를 갖고 있으면 다시 안 넣는다(멱등) — 스케줄러가 두 번 돌아도 안전.
	 * notified=false로 넣어서, 그 유저가 다음에 가계부를 열 때 build()에서 "🎉 새 뱃지 획득!" 토스트로 뜬다.
	 */
	@Transactional
	public int awardMonthlyRankBadges(YearMonth month) {
		List<RankingData.RankingRow> ranking = computeMonthlyRanking(month);
		int awarded = 0;
		for (int i = 0; i < Math.min(3, ranking.size()); i++) {
			Long userId = ranking.get(i).userId();
			String badgeCode = RANK_BADGE_CODES[i];
			if (userBadgeRepository.existsByUserIdAndBadgeCode(userId, badgeCode)) {
				continue;
			}
			userBadgeRepository.save(UserBadgeEntity.builder()
					.userId(userId).badgeCode(badgeCode).earnedAt(LocalDateTime.now()).notified(false).build());
			awarded++;
		}
		return awarded;
	}
}

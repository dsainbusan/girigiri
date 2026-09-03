package net.dsa.girigiri.service;

import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.DailySalesBarDto;
import net.dsa.girigiri.domain.dto.StoreDashboardStatsDto;
import net.dsa.girigiri.domain.dto.WeeklySavingsDto;
import net.dsa.girigiri.domain.entity.ProductEntity;
import net.dsa.girigiri.domain.entity.ReservationEntity;
import net.dsa.girigiri.domain.entity.SettlementEntity;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.repository.ListingTemplateRepository;
import net.dsa.girigiri.repository.MenuItemRepository;
import net.dsa.girigiri.repository.ProductRepository;
import net.dsa.girigiri.repository.ReservationRepository;
import net.dsa.girigiri.repository.SettlementRepository;
import net.dsa.girigiri.repository.StoreRepository;
import net.dsa.girigiri.util.StoreHoursUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 점주 대시보드 도메인 서비스 (2026-09-03, 레이어 규칙 2단계).
 *
 * StoreController에 흩어져 있던 Repository 직접 호출·집계·상태 변경 로직을 옮겨온다.
 */
@Service
@RequiredArgsConstructor
public class StoreService {

	private static final DateTimeFormatter DAY_LABEL_FORMAT = DateTimeFormatter.ofPattern("MM/dd");
	// 음식 1개를 구제할 때 절감되는 CO₂ 환산량(kg). TODO(팀): 근거 있는 계수로 조정 — 지금은 임의값.
	private static final double CO2_KG_PER_ITEM = 0.5;

	private final StoreRepository storeRepository;
	private final ProductRepository productRepository;
	private final ReservationRepository reservationRepository;
	private final MenuItemRepository menuItemRepository;
	private final ListingTemplateRepository listingTemplateRepository;
	private final SettlementRepository settlementRepository;
	private final SettlementService settlementService;

	@Transactional(readOnly = true)
	public StoreDashboardStatsDto buildDashboardStats(StoreEntity store) {
		LocalDateTime todayStart = LocalDate.now().atStartOfDay();
		LocalDateTime todayEnd = todayStart.plusDays(1);

		List<ProductEntity> todayProducts = fetchTodayProducts(store.getId(), todayStart, todayEnd);

		int registeredCount = todayProducts.size();
		int soldCount = todayProducts.stream()
				.mapToInt(p -> p.getQuantity() - p.getRemainingQuantity())
				.sum();
		int sellingNowCount = (int) todayProducts.stream().filter(p -> "active".equals(p.getStatus())).count();
		int expiredCount = (int) todayProducts.stream().filter(p -> "expired".equals(p.getStatus())).count();

		int totalQuantity = todayProducts.stream().mapToInt(ProductEntity::getQuantity).sum();
		int rescueRate = totalQuantity == 0 ? 0 : (int) Math.round(100.0 * soldCount / totalQuantity);

		List<Long> todayProductIds = todayProducts.stream().map(ProductEntity::getId).toList();
		List<ReservationEntity> todayProductReservations =
				todayProductIds.isEmpty() ? List.of() : reservationRepository.findByProductIdIn(todayProductIds);
		int pickedCount = todayProductReservations.stream()
				.filter(r -> "picked".equals(r.getStatus()))
				.mapToInt(ReservationEntity::getReservedQuantity)
				.sum();
		int reservedNotPickedCount = soldCount - pickedCount;
		int idleCount = totalQuantity - soldCount;

		StoreHoursUtil.ClosingInfo closingInfo = StoreHoursUtil.parse(store.getOperatingHours(), 60);
		boolean isClosed = closingInfo.closeAt() != null && !closingInfo.closeAt().isAfter(LocalDateTime.now());

		int donutPickedPct = totalQuantity == 0 ? 0 : (int) Math.round(100.0 * pickedCount / totalQuantity);
		int donutReservedCumPct = totalQuantity == 0 ? 0 : (int) Math.round(100.0 * (pickedCount + reservedNotPickedCount) / totalQuantity);
		int donutSoldCumPct = totalQuantity == 0 ? 0 : (int) Math.round(100.0 * soldCount / totalQuantity);

		List<ReservationEntity> todayReservations =
				reservationRepository.findByStoreIdAndPickupTimeBetween(store.getId(), todayStart, todayEnd);
		int reservationCount = todayReservations.size();
		int reservationWaiting = (int) todayReservations.stream().filter(r -> "confirmed".equals(r.getStatus())).count();
		int reservationDone = (int) todayReservations.stream().filter(r -> "picked".equals(r.getStatus())).count();
		int reservationCancelled = (int) todayReservations.stream().filter(r -> "cancelled".equals(r.getStatus())).count();

		long todaySales = todayReservations.stream()
				.filter(r -> !"cancelled".equals(r.getStatus()) && !"pending".equals(r.getStatus()))
				.mapToLong(ReservationEntity::getTotalPrice)
				.sum();

		LocalDateTime yesterdayStart = todayStart.minusDays(1);
		List<ReservationEntity> yesterdayReservations =
				reservationRepository.findByStoreIdAndPickupTimeBetween(store.getId(), yesterdayStart, todayStart);
		long yesterdaySales = yesterdayReservations.stream()
				.filter(r -> !"cancelled".equals(r.getStatus()) && !"pending".equals(r.getStatus()))
				.mapToLong(ReservationEntity::getTotalPrice)
				.sum();

		String salesDelta;
		String salesDeltaClass;
		if (yesterdaySales == 0) {
			salesDelta = todaySales == 0 ? "" : "어제 대비 신규 매출";
			salesDeltaClass = todaySales == 0 ? "u-mut" : "u-primary";
		} else {
			int changePct = (int) Math.round(100.0 * (todaySales - yesterdaySales) / yesterdaySales);
			if (changePct == 0) {
				salesDelta = "어제와 동일";
				salesDeltaClass = "u-mut";
			} else if (changePct > 0) {
				salesDelta = "▲ 어제 대비 +" + changePct + "%";
				salesDeltaClass = "u-primary";
			} else {
				salesDelta = "▼ 어제 대비 " + changePct + "%";
				salesDeltaClass = "u-danger";
			}
		}

		int rescueGoalPercent = store.getRescueGoalPercent() != null ? store.getRescueGoalPercent() : 70;
		String rescueGoal = rescueRate >= rescueGoalPercent
				? "목표 " + rescueGoalPercent + "% 달성"
				: "목표 " + rescueGoalPercent + "%";

		List<DailySalesBarDto> weeklySalesBars = buildWeeklySalesBars(store.getId(), isClosed);
		int weeklySoldTotal = weeklySalesBars.stream().mapToInt(DailySalesBarDto::soldCount).sum();
		int weeklyWasteTotal = weeklySalesBars.stream().mapToInt(DailySalesBarDto::wasteCount).sum();

		WeeklySavingsDto savings = buildWeeklySavings(store.getId());

		// 추가됨 (2026-08-27) — 왜: "마감 상품 자동 등록" 알림을 손님용 알림함(/user/alerts) 대신
		// 대시보드 최상단 "처리할 일" 배너로 보여준다 (점주 화면엔 알림함 진입점이 없어서).
		// 발행 대기 초안 수 + 매장 수락 대기(confirmed) 예약 수.
		long draftPendingCount = productRepository.findByStoreId(store.getId()).stream()
				.filter(p -> "draft".equals(p.getStatus()))
				.count();
		int incomingReservationCount =
				reservationRepository.findByStoreIdAndStatusOrderByReservedAtAsc(store.getId(), "confirmed").size();

		// 자동 등록(POS 연동 또는 템플릿)을 하나도 안 해둔 매장엔 "처리할 일" 배너로 넛지한다.
		boolean noAutomation = store.getPosProvider() == null
				&& listingTemplateRepository.findByStoreId(store.getId()).stream().noneMatch(t -> t.isActive());

		// 정산 계좌 미등록 넛지 — 계좌가 없으면 주간 정산이 확정돼도 지급이 보류된다 (WBS 2.0).
		boolean needsBankAccount = store.getBankName() == null || store.getBankName().isBlank();

		// 추가됨 (2026-08-31) — 왜: "돈 받는 것"이 제일 중요한데 정산 페이지 진입점이 토글 뒤에 묻혀
		// 있었다. 지표 그리드 밑에 "이번 달 정산 예정액" 한 줄 카드로 숫자+버튼을 같이 노출한다.
		String settlementPayout = formatWon(settlementService.build(store, "month").payout());

		return new StoreDashboardStatsDto(
				formatWon(todaySales), salesDelta, salesDeltaClass,
				soldCount, registeredCount, sellingNowCount,
				reservationCount, reservationWaiting, reservationDone, reservationCancelled,
				expiredCount, rescueRate, rescueGoalPercent, rescueGoal,
				totalQuantity, pickedCount, reservedNotPickedCount, idleCount,
				isClosed, closingInfo.label(),
				donutPickedPct, donutReservedCumPct, donutSoldCumPct,
				weeklySalesBars, weeklySoldTotal, weeklyWasteTotal,
				savings.rescuedCount(), formatWon(savings.recoveredAmount()), String.format("%.1f", savings.co2Kg()),
				String.format("%.1f", soldCount * CO2_KG_PER_ITEM),
				draftPendingCount, incomingReservationCount,
				noAutomation, needsBankAccount, settlementPayout
		);
	}

	public StoreDashboardStatsDto emptyDashboardStats() {
		return new StoreDashboardStatsDto(
				formatWon(0), "", "u-mut",
				0, 0, 0,
				0, 0, 0, 0,
				0, 0, 70, "목표 70%",
				0, 0, 0, 0,
				false, "",
				0, 0, 0,
				List.of(), 0, 0,
				0, formatWon(0), "0.0",
				"0.0",
				0L, 0,
				false, false, formatWon(0)
		);
	}

	@Transactional
	public void updateRescueGoal(StoreEntity store, int percent) {
		store.setRescueGoalPercent(Math.max(1, Math.min(100, percent)));
		storeRepository.save(store);
	}

	@Transactional(readOnly = true)
	public long getPosMenuCount(Long storeId) {
		return menuItemRepository.countByStoreId(storeId);
	}

	@Transactional(readOnly = true)
	public List<SettlementEntity> getSettlementHistory(Long storeId) {
		return settlementRepository.findByStoreIdOrderByPeriodStartDesc(storeId);
	}

	public boolean isEditValid(String category, String phone) {
		return !(category == null || category.isBlank() || phone == null || phone.isBlank());
	}

	/**
	 * 상호명/사업자등록번호/주소/위치(latitude/longitude 직접 입력)는 여기서 받지 않는다 — 자세한
	 * 사유는 원래 StoreController#editSubmit 주석 참고(승인 심사 근거값 보호, 카카오맵 Geocoder로
	 * 좌표만 hidden input으로 전달).
	 */
	@Transactional
	public void updateStoreInfo(StoreEntity store, String category, String phone, String operatingHours,
	                             Double latitude, Double longitude,
	                             String bankName, String bankAccount, String accountHolder) {
		store.setCategory(category.trim());
		store.setPhone(phone.trim());
		store.setOperatingHours(operatingHours != null && !operatingHours.isBlank() ? operatingHours.trim() : null);
		store.setLatitude(latitude);
		store.setLongitude(longitude);
		// 정산 입금 계좌 (WBS 2.0) — 셋 다 비면 미등록으로 둔다
		store.setBankName(trimToNull(bankName));
		store.setBankAccount(trimToNull(bankAccount));
		store.setAccountHolder(trimToNull(accountHolder));
		storeRepository.save(store);
	}

	private String trimToNull(String s) {
		return (s == null || s.isBlank()) ? null : s.trim();
	}

	private List<ProductEntity> fetchTodayProducts(Long storeId, LocalDateTime todayStart, LocalDateTime todayEnd) {
		return productRepository.findByStoreId(storeId).stream()
				// "오늘의 구제" 초안(draft) / 오늘 안 함(skipped)은 실제 판매가 아니므로 등록/판매/폐기 집계에서 제외.
				.filter(p -> !"draft".equals(p.getStatus()) && !"skipped".equals(p.getStatus()))
				.filter(p -> p.getRegisteredAt() != null
						&& !p.getRegisteredAt().isBefore(todayStart)
						&& p.getRegisteredAt().isBefore(todayEnd))
				.toList();
	}

	/**
	 * 추가됨 — 왜: "오늘 판매 현황" 도넛 옆 최근 7일 막대그래프.
	 * 변경됨 (2026-08-27) — 왜: WBS "판매/폐기 절감 통계 그래프" 항목명대로 판매만이 아니라 폐기도
	 * 같이 봐야 해서 판매(초록)/폐기(빨강) 2색 스택으로 바꿨다.
	 * - 판매: 픽업 일자(pickupTime) 기준, 취소/미결제 제외 ("오늘 매출"과 동일 정책)
	 * - 폐기:
	 *   1) 과거 날짜(어제~6일 전) 상품: 마감일이 지났으므로 미판매 잔여 수량(remainingQuantity)을 폐기로 집계.
	 *   2) 오늘 상품: 명시적 status='expired' 이거나 당일 영업 마감(isClosed=true)일 때 폐기로 집계.
	 */
	private List<DailySalesBarDto> buildWeeklySalesBars(Long storeId, boolean isClosed) {
		LocalDate today = LocalDate.now();
		LocalDate windowStart = today.minusDays(6);
		LocalDateTime rangeStart = windowStart.atStartOfDay();
		LocalDateTime rangeEnd = today.plusDays(1).atStartOfDay();

		Map<LocalDate, Integer> soldByDate = new HashMap<>();
		for (ReservationEntity r : reservationRepository.findByStoreIdAndPickupTimeBetween(storeId, rangeStart, rangeEnd)) {
			if ("cancelled".equals(r.getStatus()) || "pending".equals(r.getStatus())) {
				continue;
			}
			soldByDate.merge(r.getPickupTime().toLocalDate(), r.getReservedQuantity(), Integer::sum);
		}

		Map<LocalDate, Integer> wasteByDate = new HashMap<>();
		for (ProductEntity p : productRepository.findByStoreId(storeId)) {
			if ("draft".equals(p.getStatus()) || "skipped".equals(p.getStatus()) || p.getRegisteredAt() == null) {
				continue;
			}
			LocalDate d = p.getRegisteredAt().toLocalDate();
			if (d.isBefore(windowStart) || d.isAfter(today)) {
				continue;
			}
			int remaining = p.getRemainingQuantity() == null ? 0 : p.getRemainingQuantity();
			if (remaining <= 0) {
				continue;
			}
			if (d.isBefore(today)) {
				// 과거 일자에 등록되어 남은 수량은 마감일이 지났으므로 폐기 집계
				wasteByDate.merge(d, remaining, Integer::sum);
			} else if ("expired".equals(p.getStatus()) || isClosed) {
				// 오늘 등록 상품은 마감 완료(isClosed) 또는 명시적 expired 상태일 때 폐기 집계
				wasteByDate.merge(d, remaining, Integer::sum);
			}
		}

		int maxTotal = 0;
		for (int i = 0; i <= 6; i++) {
			LocalDate d = today.minusDays(i);
			maxTotal = Math.max(maxTotal, soldByDate.getOrDefault(d, 0) + wasteByDate.getOrDefault(d, 0));
		}

		List<DailySalesBarDto> bars = new ArrayList<>();
		for (int i = 6; i >= 0; i--) {
			LocalDate date = today.minusDays(i);
			int sold = soldByDate.getOrDefault(date, 0);
			int waste = wasteByDate.getOrDefault(date, 0);
			int soldPct = maxTotal == 0 ? 0 : (int) Math.round(100.0 * sold / maxTotal);
			int wastePct = maxTotal == 0 ? 0 : (int) Math.round(100.0 * waste / maxTotal);
			// 값이 있는데 막대가 안 보일 만큼 작으면 "0"과 구분이 안 되니 최소 높이를 준다.
			if (sold > 0 && soldPct < 4) soldPct = 4;
			if (waste > 0 && wastePct < 4) wastePct = 4;
			bars.add(new DailySalesBarDto(date.format(DAY_LABEL_FORMAT), sold, waste, soldPct, wastePct, date.equals(today)));
		}
		return bars;
	}

	/**
	 * 추가됨 (2026-08-27) — 왜: WBS "판매/폐기 절감 통계 그래프" + "음식 구제 개수·환경 뱃지".
	 * 최근 7일간 앱 판매로 폐기를 면한 개수 / 회수 매출 / CO₂ 절감량.
	 */
	private WeeklySavingsDto buildWeeklySavings(Long storeId) {
		LocalDate today = LocalDate.now();
		LocalDateTime rangeStart = today.minusDays(6).atStartOfDay();
		LocalDateTime rangeEnd = today.plusDays(1).atStartOfDay();

		int rescued = 0;
		long recovered = 0;
		for (ReservationEntity r : reservationRepository.findByStoreIdAndPickupTimeBetween(storeId, rangeStart, rangeEnd)) {
			if ("cancelled".equals(r.getStatus()) || "pending".equals(r.getStatus())) {
				continue;
			}
			rescued += r.getReservedQuantity();
			recovered += r.getTotalPrice();
		}
		return new WeeklySavingsDto(rescued, recovered, rescued * CO2_KG_PER_ITEM);
	}

	private String formatWon(long amount) {
		return String.format("%,d원", amount);
	}
}

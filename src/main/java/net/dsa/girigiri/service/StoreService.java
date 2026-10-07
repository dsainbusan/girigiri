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
import net.dsa.girigiri.util.StorePhoneUtil;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
	// "품절 임박"으로 볼 남은 수량 상한. TODO(팀): 상품 특성(대량/소량 판매)에 따라 다를 수 있어
	// 지금은 임의값 — 필요하면 매장별 설정으로 뺄 수 있다.
	private static final int LOW_STOCK_THRESHOLD = 2;

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
		int sellingNowCount = (int) todayProducts.stream().filter(p -> "active".equals(p.getStatus())).count();
		int expiredCount = (int) todayProducts.stream().filter(p -> "expired".equals(p.getStatus())).count();

		int totalQuantity = todayProducts.stream().mapToInt(ProductEntity::getQuantity).sum();
		int idleCount = todayProducts.stream().mapToInt(ProductEntity::getRemainingQuantity).sum();
		// hero 카드 "판매중 재고" 전용 — sellingNowCount와 같은 기준(status=active)으로 재고를 센다.
		int sellableStock = todayProducts.stream()
				.filter(p -> "active".equals(p.getStatus()))
				.mapToInt(ProductEntity::getRemainingQuantity).sum();
		// "처리할 일" 배너의 "품절 임박 재고" 카드용 — 판매중(active)인데 남은 수량이 얼마 안 남은 상품 개수.
		int lowStockCount = (int) todayProducts.stream()
				.filter(p -> "active".equals(p.getStatus()))
				.filter(p -> p.getRemainingQuantity() > 0 && p.getRemainingQuantity() <= LOW_STOCK_THRESHOLD)
				.count();

		// 변경됨 (2026-10-05, 코드 프리즈 직전 QA) — 왜: 이 카드의 "판매"가 reservedAt(예약 생성 시각)이
		// "오늘"인 것만 세고 있었는데, "등록 10개"는 이미 todayProductIds로 "오늘 등록된 상품"만 걸러낸
		// 상태라 거기에 시간 필터를 또 얹을 필요가 없었다(사용자 지적으로 발견) — 오히려 그 이중 필터
		// 때문에 "오늘 등록한 상품인데 예약은 다른 날 잡힌" 건이 숫자에서 빠지며 최근 7일 통계(상품
		// 등록일 기준 집계)와 어긋났다. 상품으로 이미 "오늘"이 정해졌으니 예약 쪽은 시간 필터 없이
		// 그 상품들을 향한 예약 전부를 센다. 노쇼는 "판매"에서 제외 — 손님이 안 가져간 거라 idleCount
		// (실제론 product.remainingQuantity)에 그대로 남아있고, 그게 마감 후 폐기로 집계되는 게 맞다
		// (noshow 처리 시 재고를 복구하지 않으므로 remainingQuantity가 자연히 그 1개를 계속 들고 있다).
		List<Long> todayProductIds = todayProducts.stream().map(ProductEntity::getId).toList();
		List<ReservationEntity> todayProductReservations =
				todayProductIds.isEmpty() ? List.of() : reservationRepository.findByProductIdIn(todayProductIds);
		int pickedCount = todayProductReservations.stream()
				.filter(r -> "picked".equals(r.getStatus()))
				.mapToInt(ReservationEntity::getReservedQuantity)
				.sum();
		int reservedNotPickedCount = todayProductReservations.stream()
				.filter(r -> "confirmed".equals(r.getStatus()) || "ready".equals(r.getStatus()))
				.mapToInt(ReservationEntity::getReservedQuantity)
				.sum();
		int soldCount = pickedCount + reservedNotPickedCount;
		int rescueRate = totalQuantity == 0 ? 0 : (int) Math.round(100.0 * soldCount / totalQuantity);

		StoreHoursUtil.ClosingInfo closingInfo = StoreHoursUtil.parse(store.getOperatingHours(), 60);
		// 변경됨 (2026-09-08, 코드 감사) — StoreHoursUtil.isOpen으로 위임(3곳 중복 중 하나, 여기는
		// 부정형(isClosed)이라 그대로 뒤집는다 — 판정 로직 자체는 한 곳에서만 관리).
		boolean isClosed = !StoreHoursUtil.isOpen(closingInfo.closeAt());
		boolean hoursConfigured = closingInfo.closeAt() != null;

		int donutPickedPct = totalQuantity == 0 ? 0 : (int) Math.round(100.0 * pickedCount / totalQuantity);
		int donutReservedCumPct = totalQuantity == 0 ? 0 : (int) Math.round(100.0 * (pickedCount + reservedNotPickedCount) / totalQuantity);
		int donutSoldCumPct = totalQuantity == 0 ? 0 : (int) Math.round(100.0 * soldCount / totalQuantity);

		List<ReservationEntity> todayReservations =
				reservationRepository.findByStoreIdAndPickupTimeBetween(store.getId(), todayStart, todayEnd);
		List<ReservationEntity> validTodayReservations = todayReservations.stream()
				.filter(r -> !"cancelled".equals(r.getStatus()) && !"pending".equals(r.getStatus()))
				.toList();

		int reservationWaiting = (int) validTodayReservations.stream()
				.filter(r -> "confirmed".equals(r.getStatus()) || "ready".equals(r.getStatus()))
				.count();
		int reservationDone = (int) validTodayReservations.stream()
				.filter(r -> "picked".equals(r.getStatus()))
				.count();
		int reservationCancelled = (int) todayReservations.stream()
				.filter(r -> "cancelled".equals(r.getStatus()))
				.count();
		// 대시보드의 '픽업 예약' 카드는 점주가 지금 처리해야 하는 '대기 건수'를 메인 숫자로 표시한다.
		// 모든 예약을 수령 완료(picked) 또는 취소(cancelled)하여 대기 중인 예약이 없으면 0건으로 표시된다.
		int reservationCount = reservationWaiting;

		long todaySales = validTodayReservations.stream()
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
				soldCount, registeredCount, sellingNowCount, sellableStock,
				reservationCount, reservationWaiting, reservationDone, reservationCancelled,
				expiredCount, rescueRate, rescueGoalPercent, rescueGoal,
				totalQuantity, pickedCount, reservedNotPickedCount, idleCount,
				isClosed, hoursConfigured, closingInfo.label(),
				donutPickedPct, donutReservedCumPct, donutSoldCumPct,
				weeklySalesBars, weeklySoldTotal, weeklyWasteTotal,
				savings.rescuedCount(), formatWon(savings.recoveredAmount()), String.format("%.1f", savings.co2Kg()),
				String.format("%.1f", soldCount * CO2_KG_PER_ITEM),
				draftPendingCount, incomingReservationCount,
				noAutomation, needsBankAccount, settlementPayout,
				lowStockCount
		);
	}

	public StoreDashboardStatsDto emptyDashboardStats() {
		return new StoreDashboardStatsDto(
				formatWon(0), "", "u-mut",
				0, 0, 0, 0,
				0, 0, 0, 0,
				0, 0, 70, "목표 70%",
				0, 0, 0, 0,
				false, false, "",
				0, 0, 0,
				List.of(), 0, 0,
				0, formatWon(0), "0.0",
				"0.0",
				0L, 0,
				false, false, formatWon(0),
				0
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
		return !(category == null || category.isBlank() || phone == null || phone.isBlank())
				&& StorePhoneUtil.isValid(phone);
	}

	public boolean isEditValid(String category, String phone, String operatingHours) {
		return isEditValid(category, phone) && StoreHoursUtil.isValidFormat(operatingHours);
	}

	/**
	 * 상호명/사업자등록번호/주소/위치(latitude/longitude 직접 입력)는 여기서 받지 않는다 — 자세한
	 * 사유는 원래 StoreController#editSubmit 주석 참고(승인 심사 근거값 보호, 카카오맵 Geocoder로
	 * 좌표만 hidden input으로 전달).
	 */
	// 수정됨 (2026-10-07, 계좌 보안) — bankName/bankAccount/accountHolder 파라미터를 뺐다. 승인된
	// 매장의 계좌는 더 이상 이 메서드(= /store/edit "정보 수정" 자율 저장)로 바로 안 바뀐다 — 바꾸고
	// 싶으면 BankAccountChangeService의 신청/승인 절차를 거쳐야 한다(요구사항 3). storeView/edit.html
	// 쪽 폼에서도 이 세 입력란을 지우고 마스킹 표시 + "계좌 변경 신청" 링크로 바꿨다.
	@Transactional
	public void updateStoreInfo(StoreEntity store, String category, String phone, String operatingHours,
	                             Double latitude, Double longitude) {
		store.setCategory(category.trim());
		store.setPhone(phone.trim());
		String trimmedHours = (operatingHours != null && !operatingHours.isBlank()) ? operatingHours.trim() : null;
		if (trimmedHours != null && !StoreHoursUtil.isValidFormat(trimmedHours)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "영업시간 형식이 올바르지 않아요: " + trimmedHours);
		}
		store.setOperatingHours(trimmedHours);
		store.setLatitude(latitude);
		store.setLongitude(longitude);
		storeRepository.save(store);
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
	 * 변경됨 (2026-10-05, 코드 프리즈 직전 QA) — 왜: "판매"를 pickupTime(픽업 예정 시각) 기준으로
	 * 세고 있었는데, "오늘 판매 현황" 카드(buildDashboardStats)는 상품 등록일 기준이라 같은 "오늘"인데
	 * 숫자가 서로 달랐다(사용자 지적으로 발견). "며칠치 상품을 등록해서 그중 얼마나 나갔나"를 보여주는
	 * 그래프이니, 판매도 폐기와 똑같이 **상품 등록일(registeredAt)** 기준으로 통일한다 — 그 상품을
	 * 향한 예약이면 실제 예약/픽업 시각과 무관하게 "그 상품이 등록된 날"의 막대에 집계된다. 노쇼는
	 * "판매"에서 제외(대시보드 카드와 동일 기준) — 손님이 안 가져간 건 remainingQuantity에 그대로
	 * 남아있다가 마감 후 아래 폐기 집계로 넘어간다.
	 */
	private List<DailySalesBarDto> buildWeeklySalesBars(Long storeId, boolean isClosed) {
		LocalDate today = LocalDate.now();
		LocalDate windowStart = today.minusDays(6);

		List<ProductEntity> recentProducts = productRepository.findByStoreId(storeId).stream()
				.filter(p -> p.getRegisteredAt() != null)
				.filter(p -> !"draft".equals(p.getStatus()) && !"skipped".equals(p.getStatus()))
				.filter(p -> {
					LocalDate d = p.getRegisteredAt().toLocalDate();
					return !d.isBefore(windowStart) && !d.isAfter(today);
				})
				.toList();
		Map<Long, LocalDate> productDateById = recentProducts.stream()
				.collect(Collectors.toMap(ProductEntity::getId, p -> p.getRegisteredAt().toLocalDate()));

		Map<LocalDate, Integer> soldByDate = new HashMap<>();
		if (!productDateById.isEmpty()) {
			for (ReservationEntity r : reservationRepository.findByProductIdIn(new ArrayList<>(productDateById.keySet()))) {
				if (!"picked".equals(r.getStatus())) {
					continue;
				}
				soldByDate.merge(productDateById.get(r.getProductId()), r.getReservedQuantity(), Integer::sum);
			}
		}

		Map<LocalDate, Integer> wasteByDate = new HashMap<>();
		for (ProductEntity p : recentProducts) {
			LocalDate d = productDateById.get(p.getId());
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

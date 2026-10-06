package net.dsa.girigiri.service;

import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.CalendarCellDto;
import net.dsa.girigiri.domain.dto.DailyPlatformStatsDto;
import net.dsa.girigiri.domain.dto.DailySignupBarDto;
import net.dsa.girigiri.domain.dto.DashboardCalendarDto;
import net.dsa.girigiri.domain.dto.KpiSummaryDto;
import net.dsa.girigiri.domain.dto.PendingQueueRowDto;
import net.dsa.girigiri.domain.dto.PlatformStatsDto;
import net.dsa.girigiri.domain.dto.RegionStatDto;
import net.dsa.girigiri.domain.dto.RegionSummaryDto;
import net.dsa.girigiri.domain.dto.SellThroughDailyDto;
import net.dsa.girigiri.domain.dto.SellThroughSummaryDto;
import net.dsa.girigiri.domain.dto.StoreStatsRowDto;
import net.dsa.girigiri.domain.dto.SuperAdminDashboardStatsDto;
import net.dsa.girigiri.domain.entity.ComplaintEntity;
import net.dsa.girigiri.domain.entity.InquiryCommentEntity;
import net.dsa.girigiri.domain.entity.InquiryEntity;
import net.dsa.girigiri.domain.entity.ProductEntity;
import net.dsa.girigiri.domain.entity.ReservationEntity;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.domain.entity.UserEntity;
import net.dsa.girigiri.repository.ComplaintRepository;
import net.dsa.girigiri.repository.InquiryCommentRepository;
import net.dsa.girigiri.repository.InquiryRepository;
import net.dsa.girigiri.repository.ProductRepository;
import net.dsa.girigiri.repository.ReservationRepository;
import net.dsa.girigiri.repository.StoreRepository;
import net.dsa.girigiri.repository.UserRepository;
import net.dsa.girigiri.util.DashboardPolicy;
import net.dsa.girigiri.util.SellThroughClassifier;
import net.dsa.girigiri.util.SidoParser;
import net.dsa.girigiri.util.StoreHoursUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 슈퍼어드민 대시보드/알림 도메인 서비스 (2026-09-03, 레이어 규칙 2단계 — SuperAdminController 잔여분 정리).
 *
 * SuperAdminController에 남아있던 마지막 Repository 직접 호출(대시보드 집계, 알림용 운영자 조회)을 옮겨온다.
 * codes()는 Repository 접근이 전혀 없는 정적 화면이라 그대로 컨트롤러에 둔다.
 */
@Service
@RequiredArgsConstructor
public class SuperAdminDashboardService {

	private final UserRepository userRepository;
	private final InquiryRepository inquiryRepository;
	private final InquiryCommentRepository inquiryCommentRepository;
	private final ComplaintRepository complaintRepository;
	// 추가됨 (2026-09-09) — getPlatformStats(거래량/구제량/매출 화면)에서 매장별로 예약을 집계할 때 사용.
	private final ReservationRepository reservationRepository;
	private final StoreRepository storeRepository;
	// 추가됨 (2026-09-30, 통계 대시보드 리디자인) — "마감 상품 소진 현황" 차트(등록/판매 SKU 집계)용.
	private final ProductRepository productRepository;

	// StoreService.CO2_KG_PER_ITEM과 동일한 계수(구제 1개당 0.5kg) — 마이페이지 절약 대시보드와
	// 같은 기준으로 맞춰서 "구제량 → CO2 절감량" 환산이 화면마다 다르게 보이지 않게 한다.
	private static final double CO2_KG_PER_ITEM = 0.5;

	private static final DateTimeFormatter ASOF_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy'년' M'월' d'일'");
	private static final DateTimeFormatter UPDATED_AT_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

	/**
	 * 통계 대시보드 전체 데이터 — KPI 5개 + 처리 대기 통합 리스트(순서 고정).
	 * (2026-09-30 전면 개편 — 기존 개별 카드/막대그래프/캘린더를 KpiSummaryDto/PendingQueueRowDto로 교체)
	 */
	@Transactional(readOnly = true)
	public SuperAdminDashboardStatsDto getDashboardStats() {
		LocalDateTime now = LocalDateTime.now();
		return new SuperAdminDashboardStatsDto(
				buildKpiSummary(now),
				buildPendingQueue(now),
				buildSellThrough(now.toLocalDate(), now),
				buildRegions(now.toLocalDate(), now),
				buildAsOfLabel(now.toLocalDate()),
				now.format(UPDATED_AT_FORMAT),
				now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
	}

	private String buildAsOfLabel(LocalDate date) {
		return date.format(ASOF_DATE_FORMAT) + " (" + koreanDayOfWeek(date.getDayOfWeek()) + ") 기준";
	}

	private String koreanDayOfWeek(DayOfWeek dow) {
		return switch (dow) {
			case MONDAY -> "월";
			case TUESDAY -> "화";
			case WEDNESDAY -> "수";
			case THURSDAY -> "목";
			case FRIDAY -> "금";
			case SATURDAY -> "토";
			case SUNDAY -> "일";
		};
	}

	// 추가됨 (2026-10-05) — getDailyPlatformStats가 대상 날짜 1번, 전월 같은 날짜 1번, 같은 집계
	// 로직을 두 번 돌려야 해서 뽑아낸 내부 전용 집계 결과(DTO로 노출하지 않음).
	private record RawDailyCounts(
			long memberCount, long storeCount, long transactionCount, long revenue, long rescuedQuantity,
			double co2Kg, long cancelledCount, long noshowedCount) {
	}

	// 변경됨 (2026-10-06) — "판매(거래/매출/구제량)"의 날짜 기준을 reservedAt(접수일)에서
	// pickedAt(픽업 완료 시각, KST)로 바꿨다. "판매=픽업완료"라는 확정 규칙에 맞춰, "오늘 거래"가
	// 말 그대로 "오늘 픽업까지 끝난 건"을 가리키게 하기 위함 — 접수는 며칠 전이었어도 오늘 픽업했으면
	// 오늘 집계에 잡히고, 오늘 접수됐어도 아직 픽업 전이면 안 잡힌다.
	// 취소/노쇼는 픽업이라는 개념 자체가 없는 상태라(영영 pickedAt이 안 채워짐) 그대로 접수일 기준 유지.
	private RawDailyCounts computeRawDailyCounts(LocalDate date) {
		LocalDateTime dayStart = date.atStartOfDay();
		LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();

		long newMemberCount = userRepository.findAll().stream()
				.filter(u -> u.getCreatedAt() != null)
				.filter(u -> !u.getCreatedAt().isBefore(dayStart) && u.getCreatedAt().isBefore(dayEnd))
				.count();

		long newStoreCount = storeRepository.findByApprovalStatus(StoreEntity.STATUS_APPROVED).stream()
				.filter(s -> s.getCreatedAt() != null)
				.filter(s -> !s.getCreatedAt().isBefore(dayStart) && s.getCreatedAt().isBefore(dayEnd))
				.count();

		List<ReservationEntity> pickedInDay = reservationRepository.findByStatusIn(List.of("picked")).stream()
				.filter(r -> r.getPickedAt() != null
						&& !r.getPickedAt().isBefore(dayStart) && r.getPickedAt().isBefore(dayEnd))
				.toList();
		long transactionCount = pickedInDay.size();
		long revenue = pickedInDay.stream().mapToLong(r -> r.getTotalPrice() == null ? 0 : r.getTotalPrice()).sum();
		long rescuedQuantity = pickedInDay.stream().mapToLong(r -> r.getReservedQuantity() == null ? 0 : r.getReservedQuantity()).sum();
		double co2Kg = rescuedQuantity * CO2_KG_PER_ITEM;

		List<ReservationEntity> receivedInDay = reservationRepository.findByReservedAtBetween(dayStart, dayEnd);
		long cancelledCount = receivedInDay.stream().filter(r -> "cancelled".equals(r.getStatus())).count();
		long noshowedCount = receivedInDay.stream().filter(r -> "noshowed".equals(r.getStatus())).count();

		return new RawDailyCounts(newMemberCount, newStoreCount, transactionCount, revenue, rescuedQuantity,
				co2Kg, cancelledCount, noshowedCount);
	}

	/**
	 * 추가됨 (2026-10-05) — 통계 대시보드 달력에서 날짜 하나를 클릭했을 때 그 날의 플랫폼 현황.
	 * KpiSummaryDto의 "오늘" 필드들과 같은 기준(픽업 완료=거래, reservedAt 기준 하루)으로 계산하되
	 * 대상 날짜만 임의의 과거 날짜로 바꾼 버전이다.
	 *
	 * 변경됨 (2026-10-05) — 전월 같은 날짜 대비 증감률(%)을 같이 낸다. LedgerService#build의
	 * "전월 대비" 계산(직전 값이 0이면 비교 불가 → null)과 동일한 방식. 기준은 "전월 전체"가 아니라
	 * "전월 같은 날짜"(date.minusMonths(1), 말일 보정은 LocalDate 기본 동작) — 하루 단위 리포트라서.
	 */
	@Transactional(readOnly = true)
	public DailyPlatformStatsDto getDailyPlatformStats(LocalDate date) {
		RawDailyCounts current = computeRawDailyCounts(date);
		RawDailyCounts prevMonth = computeRawDailyCounts(date.minusMonths(1));

		String dateLabel = date.format(ASOF_DATE_FORMAT) + " (" + koreanDayOfWeek(date.getDayOfWeek()) + ")";

		return new DailyPlatformStatsDto(date, dateLabel,
				current.memberCount(), deltaPercent(current.memberCount(), prevMonth.memberCount()),
				current.storeCount(), deltaPercent(current.storeCount(), prevMonth.storeCount()),
				current.transactionCount(), deltaPercent(current.transactionCount(), prevMonth.transactionCount()),
				current.revenue(), deltaPercent(current.revenue(), prevMonth.revenue()),
				current.rescuedQuantity(), deltaPercent(current.rescuedQuantity(), prevMonth.rescuedQuantity()),
				current.co2Kg(), current.cancelledCount(), current.noshowedCount());
	}

	private Integer deltaPercent(long current, long previous) {
		return previous > 0 ? (int) Math.round(100.0 * (current - previous) / previous) : null;
	}

	/**
	 * 추가됨 (2026-10-05) — 통계 대시보드 달력 그리드(6주×7일=42칸, 일요일 시작). 다음 달이 미래면
	 * (아직 그 달 데이터가 있을 수 없으니) nextDisabled로 표시해 템플릿에서 "다음" 버튼을 비활성화한다.
	 */
	public DashboardCalendarDto buildCalendar(YearMonth month, LocalDate selectedDate) {
		LocalDate firstOfMonth = month.atDay(1);
		LocalDate today = LocalDate.now();
		int leading = firstOfMonth.getDayOfWeek().getValue() % 7; // MONDAY=1..SUNDAY=7 → 일요일 시작 보정
		LocalDate gridStart = firstOfMonth.minusDays(leading);

		List<CalendarCellDto> cells = new ArrayList<>();
		for (int i = 0; i < 42; i++) {
			LocalDate d = gridStart.plusDays(i);
			cells.add(new CalendarCellDto(d, d.getDayOfMonth(), YearMonth.from(d).equals(month),
					d.equals(today), d.equals(selectedDate), d.isAfter(today)));
		}

		YearMonth currentMonth = YearMonth.from(today);
		return new DashboardCalendarDto(
				month.getYear() + "년 " + month.getMonthValue() + "월",
				month.minusMonths(1).toString(),
				month.plusMonths(1).toString(),
				!month.isBefore(currentMonth), // 이번 달 이후로는 "다음" 버튼 비활성 (미래 달은 데이터가 없음)
				cells);
	}

	/**
	 * KPI 카드 4장: 신규 회원/거래/거래액/구해낸 음식. "전체" 누적은 getPlatformStats(null)(기간
	 * 필터 없음이라 reservedAt/pickedAt 어느 기준이든 결과가 같다)을 재사용한다.
	 *
	 * 변경됨 (2026-10-06) — "오늘" 수치는 더 이상 getPlatformStats("today")(reservedAt 기준)가 아니라
	 * computeRawDailyCounts(today)(pickedAt 기준)를 쓴다 — 일별 현황 달력(getDailyPlatformStats)과
	 * 완전히 같은 메서드를 타므로, "오늘"에 대해서는 KPI와 일별 현황 숫자가 항상 일치한다(같은
	 * 코드 경로라 어긋날 수가 없다). "입점 매장" 카드와 CO2 필드는 뺐다(CO2는 2단계 리포트로 이동
	 * 예정). 카드마다 "어제 대비" 증감률(deltaPercent, LedgerData와 동일 규칙)을 추가했다 — 어제도
	 * computeRawDailyCounts로 구해서 오늘과 완전히 같은 기준으로 비교한다.
	 */
	private KpiSummaryDto buildKpiSummary(LocalDateTime now) {
		LocalDate today = now.toLocalDate();
		LocalDate yesterday = today.minusDays(1);
		Map<LocalDate, Long> signupsByDate = userRepository.findAll().stream()
				.filter(u -> u.getCreatedAt() != null)
				.collect(Collectors.groupingBy(u -> u.getCreatedAt().toLocalDate(), Collectors.counting()));
		long memberTodayCount = signupsByDate.getOrDefault(today, 0L);
		long memberYesterdayCount = signupsByDate.getOrDefault(yesterday, 0L);
		long memberWeekCount = 0;
		for (int i = 0; i <= 6; i++) {
			memberWeekCount += signupsByDate.getOrDefault(today.minusDays(i), 0L);
		}

		PlatformStatsDto allTimeStats = getPlatformStats(null);
		RawDailyCounts todayCounts = computeRawDailyCounts(today);
		RawDailyCounts yesterdayCounts = computeRawDailyCounts(yesterday);

		return new KpiSummaryDto(
				userRepository.count(), memberTodayCount, memberWeekCount,
				deltaPercent(memberTodayCount, memberYesterdayCount),
				allTimeStats.totalTransactionCount(), todayCounts.transactionCount(),
				deltaPercent(todayCounts.transactionCount(), yesterdayCounts.transactionCount()),
				allTimeStats.totalRevenue(), todayCounts.revenue(),
				deltaPercent(todayCounts.revenue(), yesterdayCounts.revenue()),
				allTimeStats.totalRescuedQuantity(), todayCounts.rescuedQuantity(),
				deltaPercent(todayCounts.rescuedQuantity(), yesterdayCounts.rescuedQuantity()));
	}

	/**
	 * 처리 대기 통합 리스트 — 순서는 항상 신고 접수 → 입점 신청 → 매장 문의 → 유저 문의로 고정한다
	 * (건수와 무관, 신고가 가장 긴급하다는 운영 판단을 화면 순서로 드러낸다).
	 */
	private List<PendingQueueRowDto> buildPendingQueue(LocalDateTime now) {
		List<ComplaintEntity> pendingComplaints = complaintRepository.findAll().stream()
				.filter(c -> ComplaintEntity.STATUS_PENDING.equals(c.getStatus()))
				.toList();
		List<StoreEntity> pendingStores = storeRepository.findByApprovalStatus(StoreEntity.STATUS_PENDING);

		Map<Long, Long> commentCounts = inquiryCommentRepository.findAll().stream()
				.collect(Collectors.groupingBy(InquiryCommentEntity::getInquiryId, Collectors.counting()));
		List<InquiryEntity> pendingInquiries = inquiryRepository.findAll().stream()
				.filter(inquiry -> !commentCounts.containsKey(inquiry.getId()))
				.toList();
		// storeId 있음 = 매장 문의(reports.html tab=store), 없음 = 유저 문의(tab=user) — SuperAdminSupportService와 동일 기준.
		List<InquiryEntity> pendingStoreInquiries = pendingInquiries.stream().filter(i -> i.getStoreId() != null).toList();
		List<InquiryEntity> pendingUserInquiries = pendingInquiries.stream().filter(i -> i.getStoreId() == null).toList();

		List<PendingQueueRowDto> rows = new ArrayList<>();
		rows.add(pendingRow("환불 신청", "i-bell", pendingComplaints.size(),
				oldestOf(pendingComplaints.stream().map(ComplaintEntity::getCreatedAt)), now,
				DashboardPolicy.SLA_REPORT_HOURS, "/superadmin/reports?tab=report", true));
		rows.add(pendingRow("입점 신청", "i-box", pendingStores.size(),
				oldestOf(pendingStores.stream().map(StoreEntity::getCreatedAt)), now,
				DashboardPolicy.SLA_OTHER_HOURS, "/superadmin/stores", false));
		rows.add(pendingRow("매장 문의", "i-list", pendingStoreInquiries.size(),
				oldestOf(pendingStoreInquiries.stream().map(InquiryEntity::getCreatedAt)), now,
				DashboardPolicy.SLA_OTHER_HOURS, "/superadmin/reports?tab=store", false));
		rows.add(pendingRow("유저 문의", "i-user", pendingUserInquiries.size(),
				oldestOf(pendingUserInquiries.stream().map(InquiryEntity::getCreatedAt)), now,
				DashboardPolicy.SLA_OTHER_HOURS, "/superadmin/reports?tab=user", false));
		return rows;
	}

	// 변경됨 (2026-10-06) — SLA 초과 여부(overSla)를 같이 계산하려면 "가장 오래된 건" 시각 자체가
	// 필요해서, 문자열(oldestAgoLabel)이 아니라 LocalDateTime을 받도록 바꿨다. SLA 시간은
	// DashboardPolicy 상수(신고 24시간/나머지 72시간)에서 받아 하드코딩하지 않는다.
	private PendingQueueRowDto pendingRow(String label, String iconId, long count, LocalDateTime oldest,
	                                       LocalDateTime now, long slaHours, String linkUrl, boolean priority) {
		String oldestAgoLabel = count > 0 && oldest != null ? formatAgo(oldest, now) : null;
		boolean overSla = count > 0 && oldest != null && Duration.between(oldest, now).toHours() >= slaHours;
		return new PendingQueueRowDto(label, iconId, count, oldestAgoLabel, linkUrl, priority, overSla);
	}

	private LocalDateTime oldestOf(Stream<LocalDateTime> timestamps) {
		return timestamps.filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null);
	}

	private String formatAgo(LocalDateTime from, LocalDateTime now) {
		long minutes = Duration.between(from, now).toMinutes();
		if (minutes < 1) {
			return "방금 전";
		}
		if (minutes < 60) {
			return minutes + "분 전";
		}
		long hours = minutes / 60;
		if (hours < 24) {
			return hours + "시간 전";
		}
		return hours / 24 + "일 전";
	}

	private static final DateTimeFormatter SELL_THROUGH_DAY_FORMAT = DateTimeFormatter.ofPattern("M/d");

	// 추가됨 (2026-10-06) — "마감 상품 소진 현황"/"지역별 현황"을 전부 "상품 마감일시" 기준으로
	// 다시 짜면서, 두 메서드가 공유하는 "이 상품은 언제 마감됐나/이미 마감이 지났나" 계산을 뺐다.
	// 마감일시는 DB 컬럼이 아니라 ListingDraftScheduler.sellingWindowOver와 같은 공식(등록일 당일
	// + 그 매장의 그날 마감 시각, StoreHoursUtil)으로 그때그때 계산한다 — 스케줄러와 다른 공식을
	// 새로 추측해서 만들지 않는다.
	private record DeadlineTaggedProduct(ProductEntity product, LocalDate deadlineDate, boolean deadlinePassed) {
	}

	private List<DeadlineTaggedProduct> tagProductsWithDeadline(LocalDate windowStart, LocalDate windowEnd, LocalDateTime now) {
		List<ProductEntity> candidates = productRepository.findAll().stream()
				.filter(p -> !"draft".equals(p.getStatus()) && !"skipped".equals(p.getStatus()))
				.filter(p -> p.getRegisteredAt() != null)
				.filter(p -> !p.getRegisteredAt().toLocalDate().isBefore(windowStart)
						&& !p.getRegisteredAt().toLocalDate().isAfter(windowEnd))
				.toList();
		if (candidates.isEmpty()) {
			return List.of();
		}

		Map<Long, StoreEntity> storesById = storeRepository.findAll().stream()
				.collect(Collectors.toMap(StoreEntity::getId, s -> s));

		List<DeadlineTaggedProduct> tagged = new ArrayList<>();
		for (ProductEntity p : candidates) {
			StoreEntity store = storesById.get(p.getStoreId());
			if (store == null) {
				continue; // 매장을 못 찾으면 마감을 계산할 수 없다 — 지어내지 않고 제외한다.
			}
			StoreHoursUtil.ClosingInfo closing = StoreHoursUtil.parse(
					store.getOperatingHours(), StoreHoursUtil.URGENT_THRESHOLD_MINUTES, p.getRegisteredAt());
			if (closing.closeAt() == null) {
				continue; // 영업시간 형식을 못 읽으면 마감을 계산할 수 없다 — 지어내지 않고 제외한다.
			}
			tagged.add(new DeadlineTaggedProduct(p, closing.closeAt().toLocalDate(), !closing.closeAt().isAfter(now)));
		}
		return tagged;
	}

	/** "픽업완료 예약이 1건이라도 있는 product_id" 집합 — Set이라 자연히 COUNT(DISTINCT product_id)와 같은 효과. */
	private Set<Long> pickedProductIds() {
		return reservationRepository.findByStatusIn(List.of("picked")).stream()
				.map(ReservationEntity::getProductId)
				.collect(Collectors.toSet());
	}

	/**
	 * "마감 상품 소진 현황"(최근 7일, 마감일 기준) — SKU(상품 건수) 기준(사용자 확인 완료, 수량
	 * 기준 아님). registeredCount = 그날 마감된 상품 수 중 "이미 마감이 지난" 것만(확정 집계) ·
	 * soldCount = 그중 picked 예약이 1건 이상 있는 상품 수(=판매). 등록 0건이거나, 그 날짜(=오늘)에
	 * 아직 마감 안 지난 상품이 섞여 있으면(inProgress) 소진율은 null(화면에서 "-"/"집계 중").
	 */
	private SellThroughSummaryDto buildSellThrough(LocalDate today, LocalDateTime now) {
		LocalDate windowStart = today.minusDays(6);
		// registeredAt(등록일) 기준으로 1차 후보를 넉넉히 뽑고(자정 넘는 영업시간이면 마감일이 등록일
		// 다음날일 수 있어 windowEnd를 today+1까지 잡는다), 실제 묶음은 deadlineDate로 다시 거른다.
		List<DeadlineTaggedProduct> tagged = tagProductsWithDeadline(windowStart, today.plusDays(1), now).stream()
				.filter(t -> !t.deadlineDate().isBefore(windowStart) && !t.deadlineDate().isAfter(today))
				.toList();
		Set<Long> pickedProductIds = pickedProductIds();

		Map<LocalDate, List<DeadlineTaggedProduct>> byDeadlineDate = tagged.stream()
				.collect(Collectors.groupingBy(DeadlineTaggedProduct::deadlineDate));

		Map<LocalDate, Integer> registeredByDate = new HashMap<>();
		Map<LocalDate, Integer> soldByDate = new HashMap<>();
		Map<LocalDate, Boolean> inProgressByDate = new HashMap<>();
		for (int i = 0; i <= 6; i++) {
			LocalDate date = windowStart.plusDays(i);
			List<DeadlineTaggedProduct> dayProducts = byDeadlineDate.getOrDefault(date, List.of());
			List<DeadlineTaggedProduct> passed = dayProducts.stream().filter(DeadlineTaggedProduct::deadlinePassed).toList();
			registeredByDate.put(date, passed.size());
			soldByDate.put(date, (int) passed.stream().filter(t -> pickedProductIds.contains(t.product().getId())).count());
			inProgressByDate.put(date, dayProducts.size() > passed.size());
		}

		int maxRegistered = registeredByDate.values().stream().mapToInt(Integer::intValue).max().orElse(0);

		List<SellThroughDailyDto> days = new ArrayList<>();
		for (int i = 0; i <= 6; i++) {
			LocalDate date = windowStart.plusDays(i);
			int registered = registeredByDate.get(date);
			int sold = soldByDate.get(date);
			boolean inProgress = inProgressByDate.get(date);
			Integer percent = (registered == 0 || inProgress) ? null : (int) Math.round(100.0 * sold / registered);

			int registeredHeightPercent = maxRegistered == 0 ? 0 : (int) Math.round(100.0 * registered / maxRegistered);
			int soldHeightPercent = maxRegistered == 0 ? 0 : (int) Math.round(100.0 * sold / maxRegistered);
			// 값이 있는데 막대가 안 보일 만큼 작으면 "0"과 구분이 안 되니 최소 높이를 준다(storeView 대시보드와 동일 규칙).
			if (registered > 0 && registeredHeightPercent < 4) {
				registeredHeightPercent = 4;
			}

			String dateLabel = date.format(SELL_THROUGH_DAY_FORMAT) + " " + koreanDayOfWeek(date.getDayOfWeek());
			days.add(new SellThroughDailyDto(dateLabel, date.equals(today), inProgress, registered, sold, percent,
					registeredHeightPercent, soldHeightPercent));
		}

		int weekRegisteredTotal = days.stream().mapToInt(SellThroughDailyDto::registeredCount).sum();
		int weekSoldTotal = days.stream().mapToInt(SellThroughDailyDto::soldCount).sum();
		Integer weekPercent = weekRegisteredTotal == 0 ? null : (int) Math.round(100.0 * weekSoldTotal / weekRegisteredTotal);

		return new SellThroughSummaryDto(days, weekRegisteredTotal, weekSoldTotal, weekPercent);
	}

	/**
	 * "지역별 현황"(최근 7일, 마감일 기준, SKU 기준) — StoreEntity.sido(2026-09-30 신설 컬럼)로
	 * 매장을 시도별로 묶고, 그 매장들의 "마감 지난" 상품의 등록/판매(picked 1건 이상) 수를 합산한다.
	 * sido가 null인 매장(주소 파싱 실패 등)은 어느 지역에도 잡히지 않는다 — 지어내지 않는다.
	 */
	private RegionSummaryDto buildRegions(LocalDate today, LocalDateTime now) {
		LocalDate windowStart = today.minusDays(6);

		List<StoreEntity> approvedStores = storeRepository.findByApprovalStatus(StoreEntity.STATUS_APPROVED);
		Map<Long, String> sidoByStoreId = approvedStores.stream()
				.filter(s -> s.getSido() != null)
				.collect(Collectors.toMap(StoreEntity::getId, StoreEntity::getSido));
		Map<String, Long> storeCountBySido = approvedStores.stream()
				.filter(s -> s.getSido() != null)
				.collect(Collectors.groupingBy(StoreEntity::getSido, Collectors.counting()));

		List<DeadlineTaggedProduct> tagged = tagProductsWithDeadline(windowStart, today.plusDays(1), now).stream()
				.filter(t -> !t.deadlineDate().isBefore(windowStart) && !t.deadlineDate().isAfter(today))
				.filter(DeadlineTaggedProduct::deadlinePassed) // 지역표는 확정 집계만 — 마감 안 지난 건 제외
				.filter(t -> sidoByStoreId.containsKey(t.product().getStoreId()))
				.toList();
		Set<Long> pickedProductIds = pickedProductIds();

		Map<String, Integer> registeredBySido = new HashMap<>();
		Map<String, Integer> soldBySido = new HashMap<>();
		for (DeadlineTaggedProduct t : tagged) {
			String sido = sidoByStoreId.get(t.product().getStoreId());
			registeredBySido.merge(sido, 1, Integer::sum);
			if (pickedProductIds.contains(t.product().getId())) {
				soldBySido.merge(sido, 1, Integer::sum);
			}
		}

		Map<String, RegionStatDto> tiles = new LinkedHashMap<>();
		for (String sido : SidoParser.SIDO_LIST) {
			long storeCount = storeCountBySido.getOrDefault(sido, 0L);
			int registered = registeredBySido.getOrDefault(sido, 0);
			int sold = soldBySido.getOrDefault(sido, 0);
			tiles.put(sido, buildRegionRow(sido, storeCount, registered, sold));
		}

		// 변경됨 (2026-10-06) — "점검 필요"가 위로 오게 정렬해달라는 요청. 소진율 숫자만으로 정렬하면
		// 표본 부족 지역이 어쩌다 퍼센트가 낮아서(또는 높아서) 점검 필요 지역보다 위/아래로 섞여
		// 들어올 수 있어, 상태 우선순위(점검 필요 → 정상 → 표본 부족 → 미진출)를 1차 기준으로 두고
		// 같은 상태 안에서만 소진율 오름차순으로 2차 정렬한다.
		List<RegionStatDto> tableRows = tiles.values().stream()
				.sorted(Comparator.comparing((RegionStatDto r) -> regionRowPriority(r.statusLabel()))
						.thenComparing(r -> r.sellThroughPercent() == null ? Integer.MAX_VALUE : r.sellThroughPercent()))
				.toList();

		long coveredRegionCount = tiles.values().stream().filter(r -> r.storeCount() > 0).count();
		long totalStoreCount = tiles.values().stream().mapToLong(RegionStatDto::storeCount).sum();
		int totalRegisteredCount = tiles.values().stream().mapToInt(RegionStatDto::registeredCount).sum();
		int totalSoldCount = tiles.values().stream().mapToInt(RegionStatDto::soldCount).sum();
		Integer totalPercent = totalRegisteredCount == 0 ? null : (int) Math.round(100.0 * totalSoldCount / totalRegisteredCount);

		return new RegionSummaryDto(tiles, tableRows,
				coveredRegionCount, SidoParser.SIDO_LIST.size() - coveredRegionCount,
				totalStoreCount, totalRegisteredCount, totalSoldCount, totalPercent);
	}

	private RegionStatDto buildRegionRow(String sido, long storeCount, int registered, int sold) {
		if (storeCount == 0) {
			return new RegionStatDto(sido, 0, registered, sold, null, SellThroughClassifier.TILE_NONE, "미진출");
		}
		var result = SellThroughClassifier.classify(registered, sold,
				DashboardPolicy.REGION_SAMPLE_SIZE_MIN, DashboardPolicy.REGION_LOW_SELLTHROUGH_PERCENT);
		return new RegionStatDto(sido, storeCount, registered, sold, result.percent(), result.tileClass(), result.statusLabel());
	}

	private int regionRowPriority(String statusLabel) {
		return switch (statusLabel) {
			case "점검 필요" -> 0;
			case "정상" -> 1;
			case "표본 부족" -> 2;
			default -> 3; // "미진출"
		};
	}

	// getPlatformStats의 기간 필터 — 셋 다 reservedAt(주문 접수 시점) 기준으로 자른다. 값이 이 넷 중
	// 어디에도 안 걸리면(잘못된 쿼리스트링 등) "all"로 취급한다.
	private static final List<String> STATS_PERIODS = List.of("today", "7d", "30d", "all");

	/**
	 * 추가됨 (2026-09-09) — 대시보드 "거래량/구제량/매출" 카드를 눌렀을 때 가는 "플랫폼 통계" 화면용.
	 * period(오늘/7일/30일/전체)로 자른 기간 안에서 결제까지 간 주문(pending 제외)을 매장별로 묶어,
	 * 픽업 완료(status="picked")분만 거래량/구제량/매출로 집계하고 취소율/노쇼율도 같이 계산한다.
	 * 매출 내림차순으로 정렬. 일별 추이 그래프(dailyTransactionBars)는 기간 선택과 무관하게 항상
	 * 최근 14일 픽업완료 건수를 보여준다 — "오늘"만 골랐을 때 막대가 1개뿐이면 추이를 볼 수 없어서.
	 */
	@Transactional(readOnly = true)
	public PlatformStatsDto getPlatformStats(String period) {
		// List.of(...)는 불변 리스트라 contains(null)조차 NPE를 던진다 — 카드에서 처음 들어올 땐
		// period 파라미터 자체가 없어(dashboard.html이 /superadmin/stats를 그냥 호출) null이 정상 경로다.
		String normalizedPeriod = period != null && STATS_PERIODS.contains(period) ? period : "all";

		List<ReservationEntity> inPeriod = "all".equals(normalizedPeriod)
				? reservationRepository.findAll()
				: reservationRepository.findByReservedAtBetween(periodStart(normalizedPeriod), LocalDateTime.now());
		List<ReservationEntity> nonPending = inPeriod.stream()
				.filter(r -> !"pending".equals(r.getStatus()))
				.toList();

		Map<Long, StoreEntity> storesById = storeRepository.findAll().stream()
				.collect(Collectors.toMap(StoreEntity::getId, s -> s));

		Map<Long, List<ReservationEntity>> byStore = nonPending.stream()
				.collect(Collectors.groupingBy(ReservationEntity::getStoreId));

		List<StoreStatsRowDto> storeRows = byStore.entrySet().stream()
				.map(e -> {
					List<ReservationEntity> storeReservations = e.getValue();
					List<ReservationEntity> picked = storeReservations.stream()
							.filter(r -> "picked".equals(r.getStatus())).toList();
					long revenue = picked.stream().mapToLong(r -> r.getTotalPrice() == null ? 0 : r.getTotalPrice()).sum();
					long rescued = picked.stream().mapToLong(r -> r.getReservedQuantity() == null ? 0 : r.getReservedQuantity()).sum();
					long total = storeReservations.size();
					long cancelled = storeReservations.stream().filter(r -> "cancelled".equals(r.getStatus())).count();
					long noshowed = storeReservations.stream().filter(r -> "noshowed".equals(r.getStatus())).count();
					StoreEntity store = storesById.get(e.getKey());
					return new StoreStatsRowDto(
							e.getKey(),
							store != null ? store.getStoreName() : "알 수 없음",
							picked.size(),
							rescued,
							revenue,
							total,
							total == 0 ? 0.0 : Math.round(cancelled * 1000.0 / total) / 10.0,
							total == 0 ? 0.0 : Math.round(noshowed * 1000.0 / total) / 10.0);
				})
				.sorted(Comparator.comparingLong(StoreStatsRowDto::revenue).reversed())
				.toList();

		long totalTransactionCount = storeRows.stream().mapToLong(StoreStatsRowDto::transactionCount).sum();
		long totalRescuedQuantity = storeRows.stream().mapToLong(StoreStatsRowDto::rescuedQuantity).sum();
		long totalRevenue = storeRows.stream().mapToLong(StoreStatsRowDto::revenue).sum();
		double totalCo2Kg = totalRescuedQuantity * CO2_KG_PER_ITEM;

		List<DailySignupBarDto> dailyTransactionBars = buildDailyTransactionBars();

		return new PlatformStatsDto(normalizedPeriod, periodLabel(normalizedPeriod),
				totalTransactionCount, totalRescuedQuantity, totalRevenue, totalCo2Kg, storeRows, dailyTransactionBars);
	}

	private LocalDateTime periodStart(String period) {
		LocalDate today = LocalDate.now();
		return switch (period) {
			case "today" -> today.atStartOfDay();
			case "7d" -> today.minusDays(6).atStartOfDay();
			case "30d" -> today.minusDays(29).atStartOfDay();
			default -> LocalDate.of(2000, 1, 1).atStartOfDay();
		};
	}

	private String periodLabel(String period) {
		return switch (period) {
			case "today" -> "오늘";
			case "7d" -> "최근 7일";
			case "30d" -> "최근 30일";
			default -> "전체";
		};
	}

	// 최근 14일간 픽업 완료(status="picked") 건수 막대그래프 — 막대 높이는 그 구간 최댓값 대비 %를
	// 미리 계산해서 넘긴다(Thymeleaf에서 나눗셈 직접 안 함). 7일이 아니라 14일인 이유는 거래량
	// 추이는 가입자 수보다 변동이 완만해서 좀 더 긴 구간을 봐야 흐름이 보이기 때문.
	private List<DailySignupBarDto> buildDailyTransactionBars() {
		LocalDate today = LocalDate.now();
		Map<LocalDate, Long> pickedByDate = reservationRepository.findByStatusIn(List.of("picked")).stream()
				.filter(r -> r.getPickedAt() != null)
				.collect(Collectors.groupingBy(r -> r.getPickedAt().toLocalDate(), Collectors.counting()));

		int max = 0;
		for (int i = 0; i <= 13; i++) {
			max = Math.max(max, pickedByDate.getOrDefault(today.minusDays(i), 0L).intValue());
		}

		DateTimeFormatter dayLabelFormat = DateTimeFormatter.ofPattern("M/d");
		List<DailySignupBarDto> bars = new ArrayList<>();
		for (int i = 13; i >= 0; i--) {
			LocalDate date = today.minusDays(i);
			int count = pickedByDate.getOrDefault(date, 0L).intValue();
			int heightPercent = max == 0 ? 0 : (int) Math.round(count * 100.0 / max);
			bars.add(new DailySignupBarDto(date.format(dayLabelFormat), count, heightPercent, date.equals(today)));
		}
		return bars;
	}

}

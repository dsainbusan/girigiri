package net.dsa.girigiri.service;

import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.CalendarDayDto;
import net.dsa.girigiri.domain.dto.DailySignupBarDto;
import net.dsa.girigiri.domain.dto.PlatformStatsDto;
import net.dsa.girigiri.domain.dto.StoreStatsRowDto;
import net.dsa.girigiri.domain.dto.SuperAdminDashboardStatsDto;
import net.dsa.girigiri.domain.entity.ComplaintEntity;
import net.dsa.girigiri.domain.entity.InquiryCommentEntity;
import net.dsa.girigiri.domain.entity.InquiryEntity;
import net.dsa.girigiri.domain.entity.ReservationEntity;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.domain.entity.UserEntity;
import net.dsa.girigiri.repository.ComplaintRepository;
import net.dsa.girigiri.repository.InquiryCommentRepository;
import net.dsa.girigiri.repository.InquiryRepository;
import net.dsa.girigiri.repository.ReservationRepository;
import net.dsa.girigiri.repository.StoreRepository;
import net.dsa.girigiri.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

	// StoreService.CO2_KG_PER_ITEM과 동일한 계수(구제 1개당 0.5kg) — 마이페이지 절약 대시보드와
	// 같은 기준으로 맞춰서 "구제량 → CO2 절감량" 환산이 화면마다 다르게 보이지 않게 한다.
	private static final double CO2_KG_PER_ITEM = 0.5;

	/**
	 * "최근 처리 대기"를 실제 대기 건수 요약으로 계산한다. 승인대기 매장 수(pendingStoreCount)는
	 * SuperAdminNotificationAdvice가 이미 모든 슈퍼어드민 페이지에 공급 중이라 여기서 따로 계산 안 함.
	 * 대시보드에 "최근 7일 신규 가입" 막대그래프 + 이번 달 미니 캘린더(가입자 있는 날에 점 표시)도
	 * 같은 가입일 집계로 만든다.
	 */
	@Transactional(readOnly = true)
	public SuperAdminDashboardStatsDto getDashboardStats() {
		Map<Long, Long> commentCounts = inquiryCommentRepository.findAll().stream()
				.collect(Collectors.groupingBy(InquiryCommentEntity::getInquiryId, Collectors.counting()));
		List<InquiryEntity> pendingInquiries = inquiryRepository.findAll().stream()
				.filter(inquiry -> !commentCounts.containsKey(inquiry.getId()))
				.toList();
		// storeId 있음 = 매장 문의(reports.html tab=store), 없음 = 유저 문의(tab=user) — SuperAdminSupportService와
		// 동일한 기준(2026-09-09, 대시보드 카드를 탭별로 나누면서 추가).
		long pendingStoreInquiryCount = pendingInquiries.stream().filter(i -> i.getStoreId() != null).count();
		long pendingUserInquiryCount = pendingInquiries.stream().filter(i -> i.getStoreId() == null).count();

		long pendingComplaintCount = complaintRepository.findAll().stream()
				.filter(c -> ComplaintEntity.STATUS_PENDING.equals(c.getStatus()))
				.count();

		Map<LocalDate, Long> signupsByDate = userRepository.findAll().stream()
				.filter(u -> u.getCreatedAt() != null)
				.collect(Collectors.groupingBy(u -> u.getCreatedAt().toLocalDate(), Collectors.counting()));

		List<DailySignupBarDto> weeklySignupBars = buildWeeklySignupBars(signupsByDate);
		List<CalendarDayDto> calendarDays = buildSignupCalendar(signupsByDate);
		String calendarMonthLabel = YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy년 M월"));

		return new SuperAdminDashboardStatsDto(
				pendingStoreInquiryCount, pendingUserInquiryCount, pendingComplaintCount,
				weeklySignupBars, calendarDays, calendarMonthLabel);
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

	// 최근 14일간 픽업 완료(status="picked") 건수 막대그래프 — buildWeeklySignupBars와 같은 방식
	// (막대 높이는 그 구간 최댓값 대비 %를 미리 계산), 7일이 아니라 14일인 이유는 거래량 추이는
	// 가입자 수보다 변동이 완만해서 좀 더 긴 구간을 봐야 흐름이 보이기 때문.
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

	private List<DailySignupBarDto> buildWeeklySignupBars(Map<LocalDate, Long> signupsByDate) {
		LocalDate today = LocalDate.now();

		int max = 0;
		for (int i = 0; i <= 6; i++) {
			max = Math.max(max, signupsByDate.getOrDefault(today.minusDays(i), 0L).intValue());
		}

		DateTimeFormatter dayLabelFormat = DateTimeFormatter.ofPattern("M/d");
		List<DailySignupBarDto> bars = new ArrayList<>();
		for (int i = 6; i >= 0; i--) {
			LocalDate date = today.minusDays(i);
			int count = signupsByDate.getOrDefault(date, 0L).intValue();
			int heightPercent = max == 0 ? 0 : (int) Math.round(count * 100.0 / max);
			bars.add(new DailySignupBarDto(date.format(dayLabelFormat), count, heightPercent, date.equals(today)));
		}
		return bars;
	}

	// 이번 달 1일이 시작하는 요일 앞으로 지난 달 날짜를 채워 월요일 시작 7칸 격자를 만들고, 항상 42칸
	// (6주)로 고정해 어떤 달이 와도 레이아웃이 흔들리지 않게 한다.
	private List<CalendarDayDto> buildSignupCalendar(Map<LocalDate, Long> signupsByDate) {
		LocalDate today = LocalDate.now();
		YearMonth month = YearMonth.from(today);
		LocalDate firstOfMonth = month.atDay(1);
		int leadingBlanks = firstOfMonth.getDayOfWeek().getValue() - 1;
		LocalDate cursor = firstOfMonth.minusDays(leadingBlanks);

		List<CalendarDayDto> days = new ArrayList<>();
		for (int i = 0; i < 42; i++) {
			boolean inMonth = YearMonth.from(cursor).equals(month);
			int count = signupsByDate.getOrDefault(cursor, 0L).intValue();
			days.add(new CalendarDayDto(cursor.getDayOfMonth(), inMonth, cursor.equals(today), count));
			cursor = cursor.plusDays(1);
		}
		return days;
	}

	/**
	 * 알림 패널(openNotification/readAllNotifications)용 — 슈퍼어드민 세션/식별자가 아직 없어
	 * 임시로 role=ADMIN인 첫 계정을 "그 운영자"로 쓴다.
	 */
	@Transactional(readOnly = true)
	public Long findAdminIdOrNull() {
		return userRepository.findFirstByRole(UserEntity.ROLE_ADMIN).map(UserEntity::getId).orElse(null);
	}
}

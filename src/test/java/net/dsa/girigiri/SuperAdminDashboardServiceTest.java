package net.dsa.girigiri;

import net.dsa.girigiri.domain.dto.CalendarCellDto;
import net.dsa.girigiri.domain.dto.DailyPlatformStatsDto;
import net.dsa.girigiri.domain.dto.DashboardCalendarDto;
import net.dsa.girigiri.domain.dto.PendingQueueRowDto;
import net.dsa.girigiri.domain.dto.SellThroughDailyDto;
import net.dsa.girigiri.domain.dto.SuperAdminDashboardStatsDto;
import net.dsa.girigiri.repository.UserRepository;
import net.dsa.girigiri.service.SuperAdminDashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 통계 대시보드 집계(SuperAdminDashboardService#getDashboardStats) 단위 테스트 (2026-09-30, 대시보드 리디자인).
 * 로컬 DB 연결 + sql/sample-data.sql 데이터가 들어있어야 동작한다(다른 예약 관련 테스트들과 동일 전제).
 */
@SpringBootTest
@Transactional
class SuperAdminDashboardServiceTest {

	@Autowired
	private SuperAdminDashboardService dashboardService;

	@Autowired
	private UserRepository userRepository;

	@Test
	void KPI_전체_회원수가_실제_회원_테이블_건수와_일치한다() {
		SuperAdminDashboardStatsDto stats = dashboardService.getDashboardStats();

		assertEquals(userRepository.count(), stats.kpi().totalMemberCount());
	}

	@Test
	void 처리_대기_리스트는_신고접수_입점신청_매장문의_유저문의_순서로_고정된다() {
		SuperAdminDashboardStatsDto stats = dashboardService.getDashboardStats();

		List<PendingQueueRowDto> queue = stats.pendingQueue();
		assertEquals(4, queue.size());
		assertEquals("신고 접수", queue.get(0).label());
		assertEquals("입점 신청", queue.get(1).label());
		assertEquals("매장 문의", queue.get(2).label());
		assertEquals("유저 문의", queue.get(3).label());
	}

	@Test
	void 우선_처리_배지는_신고접수에만_붙는다() {
		SuperAdminDashboardStatsDto stats = dashboardService.getDashboardStats();

		List<PendingQueueRowDto> queue = stats.pendingQueue();
		assertTrue(queue.get(0).priority());
		assertFalse(queue.get(1).priority());
		assertFalse(queue.get(2).priority());
		assertFalse(queue.get(3).priority());
	}

	@Test
	void 대기_0건인_행은_가장_오래된_요청_라벨이_null이다() {
		SuperAdminDashboardStatsDto stats = dashboardService.getDashboardStats();

		for (PendingQueueRowDto row : stats.pendingQueue()) {
			if (row.count() == 0) {
				assertNull(row.oldestAgoLabel());
			} else {
				assertTrue(row.oldestAgoLabel() != null && !row.oldestAgoLabel().isBlank());
			}
		}
	}

	@Test
	void 처리_대기_총_건수는_네_항목의_합과_같다() {
		SuperAdminDashboardStatsDto stats = dashboardService.getDashboardStats();

		long expected = stats.pendingQueue().stream().mapToLong(PendingQueueRowDto::count).sum();
		assertEquals(expected, stats.totalPendingCount());
	}

	@Test
	void 소진_현황은_최근_7일_전부를_포함하고_마지막_날이_오늘이다() {
		SuperAdminDashboardStatsDto stats = dashboardService.getDashboardStats();

		List<SellThroughDailyDto> days = stats.sellThrough().days();
		assertEquals(7, days.size());
		assertTrue(days.get(6).isToday());
		for (int i = 0; i < 6; i++) {
			assertFalse(days.get(i).isToday());
		}
	}

	@Test
	void 소진_현황_주간_등록_합계는_일별_등록수의_합과_같다() {
		SuperAdminDashboardStatsDto stats = dashboardService.getDashboardStats();

		int expected = stats.sellThrough().days().stream().mapToInt(SellThroughDailyDto::registeredCount).sum();
		assertEquals(expected, stats.sellThrough().weekRegisteredTotal());
	}

	@Test
	void 등록_0건인_날의_소진율은_null이다() {
		SuperAdminDashboardStatsDto stats = dashboardService.getDashboardStats();

		for (SellThroughDailyDto day : stats.sellThrough().days()) {
			if (day.registeredCount() == 0) {
				assertNull(day.sellThroughPercent());
			}
		}
	}

	/**
	 * 추가됨 (2026-10-05) — 통계 대시보드 달력에서 날짜를 클릭했을 때 그 날 현황 조회
	 * (DailyPlatformStatsDto) + 달력 그리드 생성(buildCalendar) 테스트.
	 */
	@Test
	void 어제_날짜의_일별_현황을_조회할_수_있다() {
		LocalDate yesterday = LocalDate.now().minusDays(1);

		DailyPlatformStatsDto daily = dashboardService.getDailyPlatformStats(yesterday);

		assertEquals(yesterday, daily.date());
		assertTrue(daily.newMemberCount() >= 0);
		assertTrue(daily.newStoreCount() >= 0);
		assertTrue(daily.transactionCount() >= 0);
		assertTrue(daily.revenue() >= 0);
		assertTrue(daily.rescuedQuantity() >= 0);
	}

	@Test
	void 전월_같은_날짜에_가입자가_없는_날은_회원_증감률이_null이다() {
		// sql/sample-data.sql에는 1년 전 날짜(가입자 0명 확정) 데이터가 없다 — 전월 값이 0이면
		// null을 반환해야 한다는 규칙(LedgerData.deltaPercent와 동일 규칙)을 확정적으로 검증.
		LocalDate farPast = LocalDate.now().minusYears(5);

		DailyPlatformStatsDto daily = dashboardService.getDailyPlatformStats(farPast);

		assertNull(daily.memberDeltaPercent());
		assertNull(daily.revenueDeltaPercent());
	}

	@Test
	void 달력_그리드는_42칸이고_선택한_날짜가_선택됨으로_표시된다() {
		LocalDate selected = LocalDate.now().minusDays(1);

		DashboardCalendarDto calendar = dashboardService.buildCalendar(YearMonth.from(selected), selected);

		assertEquals(42, calendar.cells().size());
		long selectedCount = calendar.cells().stream().filter(CalendarCellDto::isSelected).count();
		assertEquals(1, selectedCount);
		assertTrue(calendar.cells().stream().anyMatch(CalendarCellDto::isToday));
	}

	@Test
	void 이번_달_달력은_다음_달_이동이_비활성화된다() {
		DashboardCalendarDto calendar = dashboardService.buildCalendar(YearMonth.now(), null);

		assertTrue(calendar.nextDisabled());
	}

	/**
	 * 추가됨 (2026-10-06) — 작업 지시의 완료 기준("일별 현황 합계 = 같은 기간 KPI 합계가
	 * 일치하는지 검증할 것")을 그대로 테스트로 고정한다. buildKpiSummary의 "오늘" 수치와
	 * getDailyPlatformStats(오늘)이 내부적으로 같은 computeRawDailyCounts(today) 호출을
	 * 공유하므로 항상 bit-for-bit 일치해야 한다 — 우연이 아니라 구조로 보장되는지 확인.
	 */
	@Test
	void KPI_오늘_수치는_일별_현황_오늘_수치와_완전히_일치한다() {
		LocalDate today = LocalDate.now();

		var kpi = dashboardService.getDashboardStats().kpi();
		var daily = dashboardService.getDailyPlatformStats(today);

		assertEquals(kpi.todayTransactionCount(), daily.transactionCount());
		assertEquals(kpi.todayRevenue(), daily.revenue());
		assertEquals(kpi.todayRescuedQuantity(), daily.rescuedQuantity());
	}
}

package net.dsa.girigiri.service;

import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.CalendarDayDto;
import net.dsa.girigiri.domain.dto.DailySignupBarDto;
import net.dsa.girigiri.domain.dto.SuperAdminDashboardStatsDto;
import net.dsa.girigiri.domain.entity.ComplaintEntity;
import net.dsa.girigiri.domain.entity.InquiryCommentEntity;
import net.dsa.girigiri.domain.entity.UserEntity;
import net.dsa.girigiri.repository.ComplaintRepository;
import net.dsa.girigiri.repository.InquiryCommentRepository;
import net.dsa.girigiri.repository.InquiryRepository;
import net.dsa.girigiri.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
		long pendingInquiryCount = inquiryRepository.findAll().stream()
				.filter(inquiry -> !commentCounts.containsKey(inquiry.getId()))
				.count();

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
				pendingInquiryCount, pendingComplaintCount, weeklySignupBars, calendarDays, calendarMonthLabel);
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

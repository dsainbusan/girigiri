package net.dsa.girigiri.domain.dto;

/**
 * 슈퍼어드민 대시보드 미니 캘린더의 칸 하나. 이번 달 1일이 시작되는 요일에 맞춰 앞뒤로 다른 달 날짜도
 * 채워 넣어(inCurrentMonth=false) 7일씩 딱 떨어지는 격자를 만든다. signupCount는 그 날짜의 신규 가입자
 * 수 — 0이면 점 표시를 안 한다(달력에 실제 활동이 있는 날만 눈에 띄게).
 */
public record CalendarDayDto(
		int dayOfMonth,
		boolean inCurrentMonth,
		boolean isToday,
		int signupCount
) {
}

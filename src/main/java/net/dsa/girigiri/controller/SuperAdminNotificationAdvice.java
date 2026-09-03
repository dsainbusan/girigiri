package net.dsa.girigiri.controller;

import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.AdminNotificationRowDto;
import net.dsa.girigiri.service.SuperAdminNotificationService;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 슈퍼어드민 화면 전용 — 상단 크롬(통계 스트립 + 알림 벨/패널)이 모든 superAdminView/*.html 페이지에
 * 공통으로 떠야 해서, 페이지마다 컨트롤러에서 중복으로 채우는 대신 @ModelAttribute로 한 곳에서 공급한다.
 * 알림 수신자는 AdminNotificationTriggerScheduler와 동일한 스톱갭(role=ADMIN 첫 계정)을 쓴다.
 *
 * 2026-09-03 — SuperAdminController를 도메인별(Member/Store/Notice/Support)로 분리하면서 assignableTypes를
 * 5개 컨트롤러 전부로 넓혔다 — 예전엔 SuperAdminController 하나가 전체 34개 엔드포인트를 갖고 있어서 이
 * 한 줄로 전체 화면이 커버됐지만, 분리 후 그대로 두면 나머지 4개 컨트롤러 화면엔 이 공통 크롬이 안 뜬다.
 */
@ControllerAdvice(assignableTypes = {
		SuperAdminController.class,
		SuperAdminMemberController.class,
		SuperAdminStoreController.class,
		SuperAdminNoticeController.class,
		SuperAdminSupportController.class
})
@RequiredArgsConstructor
public class SuperAdminNotificationAdvice {

	private static final DateTimeFormatter TODAY_LABEL_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

	private final SuperAdminNotificationService notificationService;

	@ModelAttribute("unreadNotificationCount")
	public int unreadNotificationCount() {
		return notificationService.getUnreadNotificationCount();
	}

	@ModelAttribute("groupedNotifications")
	public Map<String, List<AdminNotificationRowDto>> groupedNotifications() {
		return notificationService.getGroupedNotifications();
	}

	@ModelAttribute("todayNewMemberCount")
	public int todayNewMemberCount() {
		return notificationService.getTodayNewMemberCount();
	}

	// 추가됨 — 왜: 상단바에 "(오늘)"이라고만 적혀 있으면 정확히 어느 날짜 기준인지 안 보여서, "오늘"이 실제로
	// 가리키는 날짜(yyyy-MM-dd)를 같이 보여달라는 요청.
	@ModelAttribute("todayLabel")
	public String todayLabel() {
		return LocalDate.now().format(TODAY_LABEL_FORMAT);
	}

	@ModelAttribute("pendingStoreCount")
	public int pendingStoreCount() {
		return notificationService.getPendingStoreCount();
	}
}

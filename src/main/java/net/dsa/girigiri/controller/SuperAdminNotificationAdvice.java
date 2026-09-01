package net.dsa.girigiri.controller;

import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.AdminNotificationRowDto;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.domain.entity.UserEntity;
import net.dsa.girigiri.repository.StoreRepository;
import net.dsa.girigiri.repository.UserRepository;
import net.dsa.girigiri.service.NotificationService;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 슈퍼어드민 화면 전용 — 상단 크롬(통계 스트립 + 알림 벨/패널)이 모든 superAdminView/*.html 페이지에
 * 공통으로 떠야 해서, 페이지마다 컨트롤러에서 중복으로 채우는 대신 @ModelAttribute로 한 곳에서 공급한다.
 * 알림 수신자는 AdminNotificationTriggerScheduler와 동일한 스톱갭(role=ADMIN 첫 계정)을 쓴다.
 */
@ControllerAdvice(assignableTypes = SuperAdminController.class)
@RequiredArgsConstructor
public class SuperAdminNotificationAdvice {

	private final NotificationService notificationService;
	private final UserRepository userRepository;
	private final StoreRepository storeRepository;

	@ModelAttribute("unreadNotificationCount")
	public int unreadNotificationCount() {
		UserEntity admin = userRepository.findFirstByRole(UserEntity.ROLE_ADMIN).orElse(null);
		return admin == null ? 0 : notificationService.getUnreadCount(admin.getId());
	}

	@ModelAttribute("groupedNotifications")
	public Map<String, List<AdminNotificationRowDto>> groupedNotifications() {
		UserEntity admin = userRepository.findFirstByRole(UserEntity.ROLE_ADMIN).orElse(null);
		if (admin == null) {
			return Map.of();
		}
		// 최신순으로 이미 정렬돼 있으므로(NotificationService#getAdminNotifications), LinkedHashMap으로
		// 묶어야 날짜 그룹 순서(최근 날짜 먼저)가 유지된다 — 기본 groupingBy는 HashMap이라 순서가 깨짐.
		return notificationService.getAdminNotifications(admin.getId()).stream()
				.collect(Collectors.groupingBy(AdminNotificationRowDto::dateLabel, LinkedHashMap::new, Collectors.toList()));
	}

	private static final DateTimeFormatter TODAY_LABEL_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

	/**
	 * 변경됨 — 왜: "이번주" 기준은 모호하다는 지적을 받아 "오늘"(자정~자정) 단위로 바꿨다. 상한(내일 자정)도
	 * 같이 걸어서 "확실히" 오늘 하루 구간만 세도록 함 — createdAt이 없거나(가입 미완료 등) 오늘 구간 밖이면
	 * 제외.
	 */
	@ModelAttribute("todayNewMemberCount")
	public int todayNewMemberCount() {
		LocalDateTime todayStart = LocalDate.now().atStartOfDay();
		LocalDateTime tomorrowStart = todayStart.plusDays(1);
		return (int) userRepository.findAll().stream()
				.filter(u -> u.getCreatedAt() != null
						&& !u.getCreatedAt().isBefore(todayStart)
						&& u.getCreatedAt().isBefore(tomorrowStart))
				.count();
	}

	// 추가됨 — 왜: 상단바에 "(오늘)"이라고만 적혀 있으면 정확히 어느 날짜 기준인지 안 보여서, "오늘"이 실제로
	// 가리키는 날짜(yyyy-MM-dd)를 같이 보여달라는 요청.
	@ModelAttribute("todayLabel")
	public String todayLabel() {
		return LocalDate.now().format(TODAY_LABEL_FORMAT);
	}

	@ModelAttribute("pendingStoreCount")
	public int pendingStoreCount() {
		return storeRepository.findByApprovalStatus(StoreEntity.STATUS_PENDING).size();
	}
}

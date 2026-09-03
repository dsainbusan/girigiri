package net.dsa.girigiri.service;

import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.AdminNotificationRowDto;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.domain.entity.UserEntity;
import net.dsa.girigiri.repository.StoreRepository;
import net.dsa.girigiri.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 슈퍼어드민 공통 크롬(상단 통계 스트립 + 알림 벨/패널)용 도메인 서비스 (2026-09-03, 레이어 규칙 2단계 —
 * SuperAdminNotificationAdvice에 흩어져 있던 Repository 직접 호출 이관).
 *
 * @ControllerAdvice도 레이어 규칙상 Controller와 동일하게 취급한다 — Repository를 직접 주입받지 않는다.
 */
@Service
@RequiredArgsConstructor
public class SuperAdminNotificationService {

	private final NotificationService notificationService;
	private final UserRepository userRepository;
	private final StoreRepository storeRepository;

	@Transactional(readOnly = true)
	public int getUnreadNotificationCount() {
		UserEntity admin = userRepository.findFirstByRole(UserEntity.ROLE_ADMIN).orElse(null);
		return admin == null ? 0 : notificationService.getUnreadCount(admin.getId());
	}

	@Transactional(readOnly = true)
	public Map<String, List<AdminNotificationRowDto>> getGroupedNotifications() {
		UserEntity admin = userRepository.findFirstByRole(UserEntity.ROLE_ADMIN).orElse(null);
		if (admin == null) {
			return Map.of();
		}
		// 최신순으로 이미 정렬돼 있으므로(NotificationService#getAdminNotifications), LinkedHashMap으로
		// 묶어야 날짜 그룹 순서(최근 날짜 먼저)가 유지된다 — 기본 groupingBy는 HashMap이라 순서가 깨짐.
		return notificationService.getAdminNotifications(admin.getId()).stream()
				.collect(Collectors.groupingBy(AdminNotificationRowDto::dateLabel, LinkedHashMap::new, Collectors.toList()));
	}

	/**
	 * "이번주" 기준은 모호하다는 지적을 받아 "오늘"(자정~자정) 단위로 바꿨다. 상한(내일 자정)도 같이
	 * 걸어서 "확실히" 오늘 하루 구간만 세도록 함 — createdAt이 없거나(가입 미완료 등) 오늘 구간 밖이면
	 * 제외.
	 *
	 * 성능 메모: userRepository.findAll()로 전체를 읽어서 메모리에서 필터링한다 — 아쉽지만 이번 이관은
	 * 위치만 옮기는 작업이라 최적화(예: 날짜 범위 쿼리)는 하지 않는다. 별도 작업으로 남겨둔다.
	 */
	@Transactional(readOnly = true)
	public int getTodayNewMemberCount() {
		LocalDateTime todayStart = LocalDate.now().atStartOfDay();
		LocalDateTime tomorrowStart = todayStart.plusDays(1);
		return (int) userRepository.findAll().stream()
				.filter(u -> u.getCreatedAt() != null
						&& !u.getCreatedAt().isBefore(todayStart)
						&& u.getCreatedAt().isBefore(tomorrowStart))
				.count();
	}

	@Transactional(readOnly = true)
	public int getPendingStoreCount() {
		return storeRepository.findByApprovalStatus(StoreEntity.STATUS_PENDING).size();
	}
}

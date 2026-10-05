package net.dsa.girigiri.service;

import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.AdminNotificationRowDto;
import net.dsa.girigiri.domain.entity.UserEntity;
import net.dsa.girigiri.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 슈퍼어드민 공통 크롬(알림 벨/패널)용 도메인 서비스 (2026-09-03, 레이어 규칙 2단계 —
 * SuperAdminNotificationAdvice에 흩어져 있던 Repository 직접 호출 이관).
 *
 * 변경됨 (2026-10-06) — 상단 "처리 대기 N건(신고 N)" 통계 스트립을 같은 날 추가했다가, 대시보드에
 * 이미 더 자세한 처리 대기 카드가 있어 중복이라는 피드백으로 다시 뺐다(getPendingTotalCount/
 * getPendingReportCount 및 그 전에 있던 getTodayNewMemberCount/getPendingStoreCount 전부 삭제).
 *
 * @ControllerAdvice도 레이어 규칙상 Controller와 동일하게 취급한다 — Repository를 직접 주입받지 않는다.
 */
@Service
@RequiredArgsConstructor
public class SuperAdminNotificationService {

	private final NotificationService notificationService;
	private final UserRepository userRepository;

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
}

package net.dsa.girigiri.service;

import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.AdminNotificationRowDto;
import net.dsa.girigiri.domain.entity.ComplaintEntity;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.domain.entity.UserEntity;
import net.dsa.girigiri.repository.ComplaintRepository;
import net.dsa.girigiri.repository.InquiryRepository;
import net.dsa.girigiri.repository.StoreRepository;
import net.dsa.girigiri.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
	private final ComplaintRepository complaintRepository;
	private final InquiryRepository inquiryRepository;

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
	 * 변경됨 (2026-10-06) — "신규회원/승인대기 매장"을 "처리 대기 N건(신고 N)"으로 바꾸면서
	 * getTodayNewMemberCount/getPendingStoreCount(둘 다 findAll()/findByXxx(...).size()로 전체를
	 * 읽어오던 무거운 쿼리)를 이 두 COUNT 메서드로 교체했다. 이 두 메서드는 모든 슈퍼어드민
	 * 화면에서 요청마다 호출되므로(@ControllerAdvice) 반드시 COUNT 전용 쿼리여야 한다 —
	 * SuperAdminDashboardService#buildPendingQueue는 "가장 오래된 요청" 시각까지 보여줘야 해서
	 * 목록을 통째로 읽지만, 여기는 숫자만 필요하다.
	 */
	@Transactional(readOnly = true)
	public int getPendingTotalCount() {
		long reportCount = complaintRepository.countByStatus(ComplaintEntity.STATUS_PENDING);
		long storeCount = storeRepository.countByApprovalStatus(StoreEntity.STATUS_PENDING);
		long inquiryCount = inquiryRepository.countPending();
		return (int) (reportCount + storeCount + inquiryCount);
	}

	@Transactional(readOnly = true)
	public int getPendingReportCount() {
		return (int) complaintRepository.countByStatus(ComplaintEntity.STATUS_PENDING);
	}
}

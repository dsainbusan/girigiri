package net.dsa.girigiri.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dsa.girigiri.domain.entity.UserEntity;
import net.dsa.girigiri.repository.UserRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 탈퇴 회원 실제 삭제 배치 — 2026-09-22 신규 (송채현, 보미 피드백 반영 — soft delete 정책 후속).
 *
 * MypageService#withdraw / SuperAdminMemberService#withdraw가 users.deletedAt만 채우고 실제
 * 로우는 남겨두는(soft delete) 방식으로 바뀌면서, 법정 보존기간이 지난 뒤 실제로 지워주는 배치가
 * 필요해졌다. 이미 withdraw 시점에 user_archive로 최소 식별 정보를 분리보관해뒀기 때문에, 여기서는
 * users 로우만 하드 삭제하면 된다 (다른 스케줄러들과 동일한 구조 — SettlementScheduler 참고).
 *
 * ⚠️ RETENTION_YEARS(보존기간)는 전자상거래법상 대금결제 기록 보존기간(5년)에 맞춘 잠정값이다 —
 * users 테이블 자체가 그 법령의 직접 대상은 아니라서, 실제 법무 검토 후 조정이 필요할 수 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserPurgeScheduler {

	private static final int RETENTION_YEARS = 5;

	private final UserRepository userRepository;

	// 매일 새벽 4시 (트래픽 적은 시간대 — NoShowScheduler 등 다른 배치와 겹치지 않는 시간으로 잡음)
	@Scheduled(cron = "0 0 4 * * *")
	@Transactional
	public void purgeExpiredWithdrawals() {
		LocalDateTime cutoff = LocalDateTime.now().minusYears(RETENTION_YEARS);
		List<UserEntity> targets = userRepository.findByDeletedAtBefore(cutoff);
		if (targets.isEmpty()) {
			return;
		}
		userRepository.deleteAll(targets);
		log.info("> [UserPurgeScheduler] 보존기간 경과 탈퇴 회원 {}명 실제 삭제", targets.size());
	}
}

package net.dsa.girigiri.repository;

import net.dsa.girigiri.domain.entity.NotificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<NotificationEntity, Long> {

	// 스캔 기반 트리거의 중복 생성 방지용 — NotificationEntity.sourceKey 주석 참고.
	boolean existsBySourceKey(String sourceKey);

	// 추가됨 (2026-09-08, 코드 감사) — NotificationService#getUnreadCount(헤더 배지 숫자, 홈 화면 포함
	// 거의 모든 화면에서 호출됨)가 findAll() 후 자바에서 세던 걸 DB 쿼리로 옮긴다.
	long countByUserIdAndReadFalse(Long userId);
}

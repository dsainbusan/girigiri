package net.dsa.girigiri.repository;

import net.dsa.girigiri.domain.entity.NotificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<NotificationEntity, Long> {

	// 스캔 기반 트리거의 중복 생성 방지용 — NotificationEntity.sourceKey 주석 참고.
	boolean existsBySourceKey(String sourceKey);
}

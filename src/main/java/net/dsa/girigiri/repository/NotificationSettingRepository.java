package net.dsa.girigiri.repository;

import net.dsa.girigiri.domain.entity.NotificationSettingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NotificationSettingRepository extends JpaRepository<NotificationSettingEntity, Long> {
	Optional<NotificationSettingEntity> findByUserId(Long userId);
}

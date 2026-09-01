package net.dsa.girigiri.repository;

import net.dsa.girigiri.domain.entity.NoticeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoticeRepository extends JpaRepository<NoticeEntity, Long> {
}

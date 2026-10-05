package net.dsa.girigiri.repository;

import net.dsa.girigiri.domain.entity.ReservationStatusHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationStatusHistoryRepository extends JpaRepository<ReservationStatusHistoryEntity, Long> {
}

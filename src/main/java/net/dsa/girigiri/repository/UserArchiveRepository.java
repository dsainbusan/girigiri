package net.dsa.girigiri.repository;

import net.dsa.girigiri.domain.entity.UserArchiveEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserArchiveRepository extends JpaRepository<UserArchiveEntity, Long> {
}

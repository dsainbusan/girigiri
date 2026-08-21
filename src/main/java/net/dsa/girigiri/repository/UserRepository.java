package net.dsa.girigiri.repository;

import net.dsa.girigiri.domain.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long> {

	Optional<UserEntity> findByOauthProviderAndOauthId(String oauthProvider, String oauthId);
}

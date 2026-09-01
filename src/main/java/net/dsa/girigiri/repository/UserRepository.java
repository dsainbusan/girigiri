package net.dsa.girigiri.repository;

import net.dsa.girigiri.domain.entity.UserEntity;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long> {

	Optional<UserEntity> findByOauthProviderAndOauthId(String oauthProvider, String oauthId);

	Optional<UserEntity> findFirstByRole(String role);

	List<UserEntity> findByNicknameContainingIgnoreCaseOrEmailContainingIgnoreCase(
			String nickname, String email, Sort sort);
}

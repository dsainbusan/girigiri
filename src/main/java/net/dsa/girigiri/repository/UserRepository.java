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

	// 이메일 찾기·비밀번호 재설정 — 번호에 유니크 제약(uk_users_phone)이 있어 단건.
	Optional<UserEntity> findByPhone(String phone);

	boolean existsByPhone(String phone);

	List<UserEntity> findByNicknameContainingIgnoreCaseOrEmailContainingIgnoreCase(
			String nickname, String email, Sort sort);
}

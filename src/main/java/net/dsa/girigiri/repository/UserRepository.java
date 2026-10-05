package net.dsa.girigiri.repository;

import net.dsa.girigiri.domain.entity.UserEntity;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long> {

	Optional<UserEntity> findByOauthProviderAndOauthId(String oauthProvider, String oauthId);

	// UserPurgeScheduler 전용 — soft delete(deletedAt)된 지 법정 보존기간이 지난 회원을 실제로
	// 지우기 위한 대상 조회.
	List<UserEntity> findByDeletedAtBefore(LocalDateTime cutoff);

	Optional<UserEntity> findFirstByRole(String role);

	// 추가됨 (2026-09-30, 통계 대시보드 리디자인) — KPI "입점 매장" 카드의 "점주 N명" 보조 지표용.
	long countByRole(String role);

	// 이메일 찾기·비밀번호 재설정 — 번호에 유니크 제약(uk_users_phone)이 있어 단건.
	Optional<UserEntity> findByPhone(String phone);

	boolean existsByPhone(String phone);

	// 추가됨 (2026-09-24) — 이메일 가입 시 "다른 provider로 이미 가입된 이메일"을 찾기 위해 필요.
	// email 컬럼엔 유니크 제약이 없어(같은 이메일로 소셜 여러 개 가입 이력이 있을 수 있음) 단건 조회로
	// 안전하게 쓰려고 findFirst를 쓴다 — 여러 건이어도 예외 없이 그중 하나만 돌려준다.
	Optional<UserEntity> findFirstByEmail(String email);

	List<UserEntity> findByNicknameContainingIgnoreCaseOrEmailContainingIgnoreCase(
			String nickname, String email, Sort sort);
}

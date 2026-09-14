package net.dsa.girigiri.repository;

import net.dsa.girigiri.domain.entity.UserBadgeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserBadgeRepository extends JpaRepository<UserBadgeEntity, Long> {

	// 뱃지 도감 화면에서 이 사용자가 지금까지 영구 획득한 뱃지 전체를 가져올 때 사용 (LedgerService.build)
	List<UserBadgeEntity> findByUserId(Long userId);

	// 대표 뱃지 설정 시 "실제로 딴 뱃지인지" 검증용 (LedgerService.updateRepresentativeBadge)
	boolean existsByUserIdAndBadgeCode(Long userId, String badgeCode);

	// 회원 탈퇴 시 뱃지 획득 기록도 같이 지우기 위한 것 (MypageService.withdraw) — user_badge는
	// users를 참조하는 DB 레벨 FK가 없어서, 안 지우면 탈퇴 후에도 고아 행으로 남는다.
	void deleteByUserId(Long userId);
}

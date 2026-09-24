package net.dsa.girigiri.repository;

import net.dsa.girigiri.domain.entity.SocialAccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SocialAccountRepository extends JpaRepository<SocialAccountEntity, Long> {

	Optional<SocialAccountEntity> findByProviderAndProviderId(String provider, String providerId);

	List<SocialAccountEntity> findAllByUserId(Long userId);

	boolean existsByProviderAndProviderId(String provider, String providerId);

	void deleteByUserIdAndProvider(Long userId, String provider);
}

package net.dsa.girigiri.repository;

import net.dsa.girigiri.domain.entity.ListingTemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * "오늘의 구제 자동 등록" 템플릿 저장소 — 2026-08-26 신규 (문창호).
 */
@Repository
public interface ListingTemplateRepository extends JpaRepository<ListingTemplateEntity, Long> {

	List<ListingTemplateEntity> findByStoreId(Long storeId);

	// ListingDraftScheduler가 매 주기마다 활성 템플릿만 훑는다.
	List<ListingTemplateEntity> findByActiveTrue();
}

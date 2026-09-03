package net.dsa.girigiri.service;

import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.entity.ListingTemplateEntity;
import net.dsa.girigiri.repository.ListingTemplateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 점주용 상품(재고) 목록 화면 도메인 서비스 (2026-09-03, 레이어 규칙 2단계 —
 * StoreProductController에 남아있던 Repository 직접 호출 이관).
 */
@Service
@RequiredArgsConstructor
public class StoreProductService {

	private final ListingTemplateRepository listingTemplateRepository;

	@Transactional(readOnly = true)
	public boolean hasActiveTemplate(Long storeId) {
		return listingTemplateRepository.findByStoreId(storeId).stream().anyMatch(ListingTemplateEntity::isActive);
	}
}

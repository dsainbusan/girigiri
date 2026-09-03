package net.dsa.girigiri.service;

import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.entity.ProductEntity;
import net.dsa.girigiri.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 소비자용 "가게 상세" 화면 도메인 서비스 (2026-09-03, 레이어 규칙 2단계).
 *
 * StoreDetailController에 흩어져 있던 Repository 직접 호출을 옮겨온다.
 */
@Service
@RequiredArgsConstructor
public class StoreDetailService {

	private final ProductRepository productRepository;

	@Transactional(readOnly = true)
	public List<ProductEntity> getActiveProducts(Long storeId) {
		return productRepository.findAll().stream()
				.filter(p -> storeId.equals(p.getStoreId()))
				.filter(p -> "active".equals(p.getStatus()))
				.filter(p -> p.getRemainingQuantity() != null && p.getRemainingQuantity() > 0)
				.toList();
	}
}

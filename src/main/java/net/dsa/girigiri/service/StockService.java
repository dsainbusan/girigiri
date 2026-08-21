package net.dsa.girigiri.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.entity.ProductEntity;
import net.dsa.girigiri.exception.OutOfStockException;
import net.dsa.girigiri.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 마감세일 상품 재고 차감 서비스.
 * 여러 사람이 동시에 같은 상품을 예약해도 PESSIMISTIC_WRITE 락으로 한 트랜잭션씩만
 * 처리되게 해서, 재고보다 많이 예약되는(오버셀) 상황을 막는다.
 *
 * 예약(Reservation)/로그인 없이 productId + 수량만으로 독립적으로 테스트 가능하다.
 */
@Service
@RequiredArgsConstructor
public class StockService {

	private final ProductRepository productRepository;

	/**
	 * 재고를 차감한다. 재고가 부족하면 OutOfStockException을 던진다.
	 * 반드시 @Transactional 안에서 락을 잡고 → 확인 → 차감 → 저장까지 한 번에 처리해야
	 * 동시 요청 사이에 재고를 잘못 읽는 레이스 컨디션을 막을 수 있다.
	 */
	@Transactional
	public void decreaseStock(Long productId, int quantity) {
		ProductEntity product = productRepository.findByIdForUpdate(productId)
				.orElseThrow(() -> new EntityNotFoundException("상품을 찾을 수 없습니다. id=" + productId));

		if (product.getRemainingQuantity() < quantity) {
			throw new OutOfStockException("남은 수량이 없어요. (남은 수량: " + product.getRemainingQuantity() + ")");
		}

		product.setRemainingQuantity(product.getRemainingQuantity() - quantity);
		if (product.getRemainingQuantity() == 0) {
			product.setStatus("sold");
		}
		productRepository.save(product);
	}

	/**
	 * 예약 취소 시 재고를 다시 돌려놓는다 (decreaseStock의 반대).
	 * 재고가 0이라 "sold"로 바뀌어 있었다면, 다시 살 수 있는 상태이니 "active"로 되돌린다.
	 * 이것도 락을 잡고 처리해서, 취소와 새 예약이 동시에 일어나도 안전하게 처리된다.
	 */
	@Transactional
	public void restoreStock(Long productId, int quantity) {
		ProductEntity product = productRepository.findByIdForUpdate(productId)
				.orElseThrow(() -> new EntityNotFoundException("상품을 찾을 수 없습니다. id=" + productId));

		product.setRemainingQuantity(product.getRemainingQuantity() + quantity);
		if ("sold".equals(product.getStatus()) && product.getRemainingQuantity() > 0) {
			product.setStatus("active");
		}
		productRepository.save(product);
	}
}

package net.dsa.girigiri;

import net.dsa.girigiri.domain.entity.ProductEntity;
import net.dsa.girigiri.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 추가됨 (2026-10-06, 코드 리뷰 #1 회귀 확인용) — ProductController가 StoreRepository를 직접
 * 주입받던 걸 LookupService.findStore(Optional)로 바꿨다. store_id가 FK로 강제되지 않아서
 * (ProductEntity 참고) 매장이 없는 "고아 상품"이 있을 수 있는데, 원래 코드가 이 경우도 404 없이
 * store=null로 화면을 띄워주던 동작을 그대로 지키는지 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProductDetailOrphanStoreRenderTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ProductRepository productRepository;

	@Test
	@WithMockUser
	@DisplayName("정상 상품(매장 있음)의 상세 화면이 200으로 렌더링된다")
	void detailWithStoreRenders() throws Exception {
		mockMvc.perform(get("/user/products/{id}", 1L))
				.andExpect(status().isOk());
	}

	@Test
	@WithMockUser
	@DisplayName("매장이 없는 고아 상품도 404 없이 200으로 렌더링된다 (기존 동작 유지 확인)")
	void detailWithoutStoreStillRenders() throws Exception {
		ProductEntity orphan = productRepository.save(ProductEntity.builder()
				.storeId(999_999L)
				.name("고아 상품")
				.originalPrice(5000)
				.discountedPrice(2500)
				.quantity(1)
				.remainingQuantity(1)
				.status("active")
				.build());

		mockMvc.perform(get("/user/products/{id}", orphan.getId()))
				.andExpect(status().isOk());
	}
}

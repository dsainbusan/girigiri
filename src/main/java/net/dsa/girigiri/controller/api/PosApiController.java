package net.dsa.girigiri.controller.api;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.PosProductDto;
import net.dsa.girigiri.domain.entity.ProductEntity;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.repository.ProductRepository;
import net.dsa.girigiri.repository.StoreRepository;
import net.dsa.girigiri.util.DiscountRateCalculator;
import net.dsa.girigiri.util.StoreHoursUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * POS json 자동 수신 연동 (가정, WBS 3.0 문창호 담당).
 *
 * 실제 POS 단말기 연동 전까지는, 매장의 POS가 이 형태의 JSON을 보낸다고 가정하고 그대로 받아서
 * 상품(ProductEntity)으로 등록한다. 등록 시 할인율은 DiscountRateCalculator로 자동 계산해서 붙인다
 * (마감까지 남은 시간 기준 — StoreHoursUtil 재사용).
 *
 * 로그인한 점주 세션 기준으로 본인 매장에만 등록할 수 있다 (storeId를 요청 바디로 안 받고
 * session.userId → StoreRepository.findByOwnerId로 찾는다 — 다른 매장에 등록하는 걸 막기 위해).
 */
@RestController
@RequestMapping("/api/pos")
@RequiredArgsConstructor
public class PosApiController {

	private final ProductRepository productRepository;
	private final StoreRepository storeRepository;

	@PostMapping("/products")
	public ResponseEntity<Map<String, Object>> receiveProduct(@RequestBody PosProductDto dto, HttpSession session) {
		Long userId = (Long) session.getAttribute("userId");
		if (userId == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "login_required"));
		}

		StoreEntity store = storeRepository.findByOwnerId(userId).orElse(null);
		if (store == null) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "store_not_found"));
		}

		if (dto.getName() == null || dto.getName().isBlank()
				|| dto.getOriginalPrice() == null || dto.getOriginalPrice() <= 0
				|| dto.getQuantity() == null || dto.getQuantity() <= 0) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "invalid_payload"));
		}

		StoreHoursUtil.ClosingInfo closingInfo = StoreHoursUtil.parse(store.getOperatingHours(), 60);
		int discountRate = DiscountRateCalculator.calculateRate(closingInfo.closeAt());
		int discountedPrice = DiscountRateCalculator.applyDiscount(dto.getOriginalPrice(), discountRate);

		ProductEntity product = ProductEntity.builder()
				.storeId(store.getId())
				.name(dto.getName().trim())
				.originalPrice(dto.getOriginalPrice())
				.discountedPrice(discountedPrice)
				.quantity(dto.getQuantity())
				.remainingQuantity(dto.getQuantity())
				.status("active")
				.build();
		productRepository.save(product);

		return ResponseEntity.ok(Map.of(
				"productId", product.getId(),
				"discountRate", discountRate,
				"discountedPrice", discountedPrice
		));
	}
}

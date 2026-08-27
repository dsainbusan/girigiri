package net.dsa.girigiri.controller.api;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.PosMenuItemDto;
import net.dsa.girigiri.domain.dto.PosProductDto;
import net.dsa.girigiri.domain.dto.PosStockDto;
import net.dsa.girigiri.domain.entity.ProductEntity;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.repository.ProductRepository;
import net.dsa.girigiri.repository.StoreRepository;
import net.dsa.girigiri.service.PosCatalogService;
import net.dsa.girigiri.util.DiscountRateCalculator;
import net.dsa.girigiri.util.StoreHoursUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
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
	private final PosCatalogService posCatalogService;

	/**
	 * 추가됨 (2026-08-27) — 왜: "POS json 카탈로그 연동". POS가 매장 메뉴 카탈로그를 배열로 push하는 경로.
	 * 예: [ {"posSku":"BR001","name":"크루아상","originalPrice":3500}, ... ]
	 * (연동 화면 /store/pos 의 [연동하기]는 mock 샘플을 넣지만, 실제 POS 연동이면 이 엔드포인트로 들어온다.)
	 */
	@PostMapping("/catalog")
	public ResponseEntity<Map<String, Object>> receiveCatalog(@RequestBody List<PosMenuItemDto> items, HttpSession session) {
		Long userId = (Long) session.getAttribute("userId");
		if (userId == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "login_required"));
		}
		if (items == null || items.isEmpty()) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "empty_payload"));
		}
		try {
			int applied = posCatalogService.applyCatalog(userId, items);
			return ResponseEntity.ok(Map.of("applied", applied));
		} catch (ResponseStatusException e) {
			return ResponseEntity.status(e.getStatusCode()).body(Map.of("error", "store_not_found"));
		}
	}

	/**
	 * 추가됨 (2026-08-27) — 왜: "POS json 재고 스냅샷 (B안)". POS가 현재 재고를 배열로 push하는 경로.
	 * 예: [ {"posSku":"BR001","remaining":7}, {"posSku":"LB001","remaining":4} ]
	 * 마감 무렵 스케줄러가 이 재고로 "지금 이만큼 남았는데 팔래요?" 초안을 만든다.
	 * 시연에서는 /store/pos/sim(POS 시뮬레이터)이 이 엔드포인트를 대신 호출한다.
	 */
	@PostMapping("/stock")
	public ResponseEntity<Map<String, Object>> receiveStock(@RequestBody List<PosStockDto> items, HttpSession session) {
		Long userId = (Long) session.getAttribute("userId");
		if (userId == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "login_required"));
		}
		if (items == null || items.isEmpty()) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "empty_payload"));
		}
		try {
			int applied = posCatalogService.applyStock(userId, items);
			return ResponseEntity.ok(Map.of("applied", applied));
		} catch (ResponseStatusException e) {
			return ResponseEntity.status(e.getStatusCode()).body(Map.of("error", "store_not_found"));
		}
	}

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

package net.dsa.girigiri.controller.api;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.PosMenuItemDto;
import net.dsa.girigiri.domain.dto.PosStockDto;
import net.dsa.girigiri.service.PosCatalogService;
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
 * 실제 POS 단말 연동은 하지 않는다. "매장 POS가 이 규격의 JSON을 보낸다"고 가정하고,
 * 시연은 /store/pos/sim(POS 시뮬레이터)로 한다. json 포맷 명세는 docs/pos-json-spec.md.
 *
 * 두 경로:
 *  - POST /api/pos/catalog : 메뉴 카탈로그(품목·가격) 배열. posSku 기준 upsert → menu_item.
 *  - POST /api/pos/stock   : 현재 재고 스냅샷 배열. posSku 기준 menu_item.stock_quantity 갱신.
 *                            마감 무렵 스케줄러가 이 재고로 "오늘의 구제" 초안을 만든다(B안).
 *
 * 매장 식별은 요청 바디가 아니라 로그인 세션(session.userId → store.owner_id)으로 한다
 * (다른 매장 데이터를 건드리는 걸 막기 위해).
 */
@RestController
@RequestMapping("/api/pos")
@RequiredArgsConstructor
public class PosApiController {

	private final PosCatalogService posCatalogService;

	/**
	 * 메뉴 카탈로그 수신.
	 * 예: [ {"posSku":"BR001","name":"크루아상","originalPrice":3500}, ... ]
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
	 * 재고 스냅샷 수신 (B안).
	 * 예: [ {"posSku":"BR001","remaining":7}, {"posSku":"LB001","remaining":4} ]
	 * 마감 무렵 스케줄러가 이 재고로 "지금 이만큼 남았는데 팔래요?" 초안을 만든다.
	 * 시연에서는 /store/pos/sim(POS 시뮬레이터)이 이 규격을 대신 태운다.
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
}

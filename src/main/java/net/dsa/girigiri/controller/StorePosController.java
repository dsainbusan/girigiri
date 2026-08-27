package net.dsa.girigiri.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.service.PosCatalogService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.time.format.DateTimeFormatter;

/**
 * 점주 "POS 연동 관리" 화면 — 2026-08-27 신규 (문창호).
 * 라우팅은 /store/pos/**. 실제 서비스(배민/Toast/Shopify)도 연동은 가입폼·매장수정이 아니라
 * 이런 전용 화면에 둔다.
 *
 * B안(재고 스냅샷): "미리 만들어 파는 집" 가정. POS가 현재 재고를 push하면 마감 무렵 앱이
 * "지금 이만큼 남았는데 팔래요?" 초안을 만든다. 진짜 POS가 없어서 /store/pos/sim(시뮬레이터)로 시연.
 */
@Controller
@RequestMapping("/store/pos")
@RequiredArgsConstructor
public class StorePosController {

	private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("M/d HH:mm");

	private final PosCatalogService posCatalogService;

	private Long ownerId(HttpSession session) {
		return (Long) session.getAttribute("userId");
	}

	@GetMapping
	public String connectScreen(HttpSession session, Model model) {
		Long ownerId = ownerId(session);
		if (ownerId == null) {
			return "redirect:/auth/loginForm";
		}
		StoreEntity store;
		try {
			store = posCatalogService.requireStore(ownerId);
		} catch (ResponseStatusException e) {
			return "redirect:/auth/owner-apply";
		}

		boolean connected = store.getPosProvider() != null;
		model.addAttribute("connected", connected);
		model.addAttribute("providerLabel", providerLabel(store.getPosProvider()));
		model.addAttribute("lastSyncLabel", store.getPosLastSyncAt() == null ? "" : store.getPosLastSyncAt().format(TS));
		model.addAttribute("menuItems", connected ? posCatalogService.listMenu(ownerId) : java.util.List.of());
		model.addAttribute("promptTime", store.getPosDraftPromptTime() == null ? "" : store.getPosDraftPromptTime().toString());
		return "storeView/posConnect";
	}

	@PostMapping("/connect")
	public String connect(@RequestParam(required = false) String provider,
	                      @RequestParam(required = false) String storeCode,
	                      HttpSession session) {
		Long ownerId = ownerId(session);
		if (ownerId == null) {
			return "redirect:/auth/loginForm";
		}
		try {
			posCatalogService.connect(ownerId, provider, storeCode);
		} catch (ResponseStatusException e) {
			return "redirect:/store/pos?error";
		}
		return "redirect:/store/pos?connected";
	}

	@PostMapping("/resync")
	public String resync(HttpSession session) {
		Long ownerId = ownerId(session);
		if (ownerId == null) {
			return "redirect:/auth/loginForm";
		}
		posCatalogService.resync(ownerId);
		return "redirect:/store/pos?synced";
	}

	@PostMapping("/disconnect")
	public String disconnect(HttpSession session) {
		Long ownerId = ownerId(session);
		if (ownerId == null) {
			return "redirect:/auth/loginForm";
		}
		posCatalogService.disconnect(ownerId);
		return "redirect:/store/pos";
	}

	/**
	 * 메뉴별 "앱 판매 켬/끔" + 할인율 + 앱 판매 최대 수량.
	 * discountRate 비우면 마감시간 기준 자동. appSaleQuantity 비우면 재고 전량.
	 */
	@PostMapping("/menu/{id}/settings")
	public String menuSettings(@PathVariable Long id,
	                           @RequestParam(defaultValue = "false") boolean appSaleEnabled,
	                           @RequestParam(required = false) String discountRate,
	                           @RequestParam(required = false) String appSaleQuantity,
	                           HttpSession session) {
		Long ownerId = ownerId(session);
		if (ownerId == null) {
			return "redirect:/auth/loginForm";
		}
		Integer rate = parseNullableInt(discountRate);
		Integer qty = parseNullableInt(appSaleQuantity);
		if ((discountRate != null && !discountRate.isBlank() && rate == null)
				|| (appSaleQuantity != null && !appSaleQuantity.isBlank() && qty == null)) {
			return "redirect:/store/pos?error";
		}
		try {
			posCatalogService.updateMenuSaleSettings(ownerId, id, appSaleEnabled, rate, qty);
		} catch (ResponseStatusException e) {
			boolean rateIssue = e.getReason() != null && e.getReason().contains("할인율");
			return "redirect:/store/pos?error" + (rateIssue ? "=rate" : "");
		}
		return "redirect:/store/pos?saved";
	}

	/** 매일 몇 시에 POS 재고로 물어볼지. 빈 값이면 자동 생성 끔. */
	@PostMapping("/prompt-time")
	public String promptTime(@RequestParam(required = false) String promptTime, HttpSession session) {
		Long ownerId = ownerId(session);
		if (ownerId == null) {
			return "redirect:/auth/loginForm";
		}
		try {
			posCatalogService.updateDraftPromptTime(ownerId, promptTime);
		} catch (Exception e) {
			return "redirect:/store/pos?error";
		}
		return "redirect:/store/pos?timeSaved";
	}

	// --- POS 시뮬레이터 (시연용) --------------------------------------

	@GetMapping("/sim")
	public String simulator(HttpSession session, Model model) {
		Long ownerId = ownerId(session);
		if (ownerId == null) {
			return "redirect:/auth/loginForm";
		}
		StoreEntity store;
		try {
			store = posCatalogService.requireStore(ownerId);
		} catch (ResponseStatusException e) {
			return "redirect:/auth/owner-apply";
		}
		model.addAttribute("connected", store.getPosProvider() != null);
		model.addAttribute("storeName", store.getStoreName());
		model.addAttribute("menuItems", posCatalogService.listMenu(ownerId));
		return "storeView/posSimulator";
	}

	@PostMapping("/sim/restock")
	public String simRestock(HttpSession session) {
		Long ownerId = ownerId(session);
		if (ownerId == null) {
			return "redirect:/auth/loginForm";
		}
		posCatalogService.simRestock(ownerId);
		return "redirect:/store/pos/sim";
	}

	@PostMapping("/sim/selldown")
	public String simSellDown(HttpSession session) {
		Long ownerId = ownerId(session);
		if (ownerId == null) {
			return "redirect:/auth/loginForm";
		}
		posCatalogService.simSellDown(ownerId);
		return "redirect:/store/pos/sim";
	}

	@PostMapping("/sim/stock/{id}")
	public String simSetStock(@PathVariable Long id, @RequestParam int remaining, HttpSession session) {
		Long ownerId = ownerId(session);
		if (ownerId == null) {
			return "redirect:/auth/loginForm";
		}
		try {
			posCatalogService.simSetStock(ownerId, id, remaining);
		} catch (ResponseStatusException e) {
			return "redirect:/store/pos/sim?error";
		}
		return "redirect:/store/pos/sim";
	}

	/** "마감 임박 → 앱에 물어보기" — 스케줄러를 기다리지 않고 지금 재고로 초안 생성. */
	@PostMapping("/sim/prompt")
	public String simPrompt(HttpSession session) {
		Long ownerId = ownerId(session);
		if (ownerId == null) {
			return "redirect:/auth/loginForm";
		}
		int created = posCatalogService.generateDraftsFromStock(posCatalogService.requireStore(ownerId));
		return "redirect:/store/products" + (created > 0 ? "?drafted=" + created : "?drafted=0");
	}

	private String providerLabel(String provider) {
		return PosCatalogService.providerLabel(provider);
	}

	/** 빈 문자열/null이면 null, 숫자면 Integer, 파싱 실패면 null (호출부에서 원문 확인해 에러 처리). */
	private Integer parseNullableInt(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		try {
			return Integer.parseInt(raw.trim());
		} catch (NumberFormatException e) {
			return null;
		}
	}
}

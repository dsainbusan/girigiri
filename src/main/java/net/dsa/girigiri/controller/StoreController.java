package net.dsa.girigiri.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.ProductRegisterForm;
import net.dsa.girigiri.domain.dto.StoreSettingsForm;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.domain.entity.UserEntity;
import net.dsa.girigiri.repository.StoreRepository;
import net.dsa.girigiri.service.ProductService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

/**
 * TODO(송채현): ProductRepository 연동, 실제 등록/판매/폐기 통계로 교체.
 * 현재는 ADMIN 화면 디자인 통일성 확인용 데모 데이터다 (storeName만 실데이터로 교체됨).
 */
@Controller
@RequestMapping("/store")
@RequiredArgsConstructor
public class StoreController {

	private final StoreRepository storeRepository;
	private final ProductService productService;

	// 추가됨 (2026-08-21) — 왜: role 라우팅 + 유저/점주 모드 분기 작업 중 HomeController가 OWNER_MODE
	// 세션을 이 화면으로 리다이렉트하게 됐는데, 지금까지 로그인 여부와 무관하게 완전히 공개돼 있었다.
	// 실데이터/권한 연동은 송채현 담당(TODO 위 참고)이라 그대로 두고, 최소한 로그인 가드만 추가한다.
	@GetMapping("/dashboard")
	public String dashboard(HttpSession session, Model model) {
		Long userId = (Long) session.getAttribute("userId");
		if (userId == null) {
			return "redirect:/auth/loginForm";
		}

		// 변경됨 (2026-08-21) — 왜: "보미네 베이커리" 하드코딩 대신 로그인한 점주가 실제로 등록한
		// store.store_name을 보여달라는 요청. registeredCount 등 나머지 통계는 Product 연동이
		// 아직 없어 데모값 그대로 둔다(위 TODO 참고).
		String storeName = storeRepository.findByOwnerId(userId)
				.map(store -> store.getStoreName())
				.orElse("매장 정보 없음");

		model.addAttribute("storeName", storeName);
		model.addAttribute("registeredCount", 12);
		model.addAttribute("soldCount", 8);
		model.addAttribute("expiredCount", 1);
		model.addAttribute("rescueRate", 67);
		return "storeView/dashboard";
	}

	/**
	 * 매장 정보(카테고리·위치·영업시간 등) 수정 화면.
	 * /auth/owner-apply에서 최초 등록된 매장 정보를 승인 이후 다시 열람·수정할 때 쓴다.
	 */
	@GetMapping("/settings")
	public String settingsForm(HttpSession session, Model model) {
		StoreEntity store = currentStore(session);
		model.addAttribute("storeSettingsForm", StoreSettingsForm.from(store));
		return "storeView/settings";
	}

	@PostMapping("/settings")
	public String updateSettings(@ModelAttribute StoreSettingsForm form, HttpSession session) {
		StoreEntity store = currentStore(session);

		store.setStoreName(form.getStoreName() == null ? null : form.getStoreName().trim());
		store.setCategory(form.getCategory());
		store.setAddress(form.getAddress() == null ? null : form.getAddress().trim());
		store.setLatitude(form.getLatitude());
		store.setLongitude(form.getLongitude());
		store.setPhone(form.getPhone() == null ? null : form.getPhone().trim());
		store.setOperatingHours(form.getOperatingHours() == null || form.getOperatingHours().isBlank()
				? null : form.getOperatingHours().trim());

		storeRepository.save(store);
		return "redirect:/store/settings?saved";
	}

	@GetMapping("/products/new")
	public String productForm(Model model) {
		model.addAttribute("productRegisterForm", new ProductRegisterForm());
		return "storeView/productForm";
	}

	@PostMapping("/products")
	public String registerProduct(@ModelAttribute ProductRegisterForm productRegisterForm, HttpSession session) {
		StoreEntity store = currentStore(session);
		productService.register(store.getId(), productRegisterForm);
		return "redirect:/store/dashboard";
	}

	/**
	 * TODO(송보미): role/viewMode 세션에 storeId가 아직 없어(WBS 인증 문서 참고)
	 * 매 요청마다 findByOwnerId로 다시 조회한다. storeId가 세션에 실리면 이 조회는 제거한다.
	 */
	private StoreEntity currentStore(HttpSession session) {
		Long userId = (Long) session.getAttribute("userId");
		if (userId == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
		}
		return storeRepository.findByOwnerId(userId)
				.filter(store -> UserEntity.ROLE_OWNER.equals(store.getRole()))
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "매장 정보를 찾을 수 없습니다. 입점 신청을 먼저 진행해 주세요."));
	}
}

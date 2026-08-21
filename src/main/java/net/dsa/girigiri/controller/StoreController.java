package net.dsa.girigiri.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.repository.StoreRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * TODO(송채현): ProductRepository 연동, 실제 등록/판매/폐기 통계로 교체.
 * 현재는 ADMIN 화면 디자인 통일성 확인용 데모 데이터다 (storeName만 실데이터로 교체됨).
 */
@Controller
@RequestMapping("/store")
@RequiredArgsConstructor
public class StoreController {

	private final StoreRepository storeRepository;

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
}

package net.dsa.girigiri.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * TODO(송채현): StoreRepository/ProductRepository 연동, storeId 기준 실데이터 조회로 교체.
 * 현재는 ADMIN 화면 디자인 통일성 확인용 데모 데이터다.
 */
@Controller
@RequestMapping("/store")
public class StoreController {

	@GetMapping("/dashboard")
	public String dashboard(Model model) {
		model.addAttribute("storeName", "보미네 베이커리");
		model.addAttribute("registeredCount", 12);
		model.addAttribute("soldCount", 8);
		model.addAttribute("expiredCount", 1);
		model.addAttribute("rescueRate", 67);
		return "storeView/dashboard";
	}
}

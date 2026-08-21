package net.dsa.girigiri.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * TODO(김태훈): ProductRepository 연동, productId 기준 실데이터 조회로 교체.
 * 현재는 USER 화면 디자인 통일성 확인용 데모 데이터다.
 */
@Controller
@RequestMapping("/product")
public class ProductController {

	@GetMapping("/detail")
	public String detail(Model model) {
		model.addAttribute("name", "크루아상 5개 세트");
		model.addAttribute("originalPrice", 12000);
		model.addAttribute("discountedPrice", 4800);
		model.addAttribute("discountRate", 60);
		model.addAttribute("remainingQuantity", 3);
		model.addAttribute("pickupTime", "오늘 21:00 ~ 21:30");
		model.addAttribute("description", "마감 전 남은 크루아상을 합리적인 가격에 만나보세요.");
		return "productView/detail";
	}
}

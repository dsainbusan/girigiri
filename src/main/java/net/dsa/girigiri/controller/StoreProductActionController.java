package net.dsa.girigiri.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.service.ProductService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 점주용 상품(재고) 상태 전환 액션 — 발행/보류/품절/판매재개/삭제.
 * 2026-09-08 — StoreProductController(311줄, 자체 300줄 분리 기준 초과)에서 분리했다(코드 감사 LOW
 * 항목 대응). 목록/등록/수정(CRUD)은 StoreProductController에 남기고, 여기는 전부 "id 하나 받아서
 * ProductService에 위임 후 목록으로 리다이렉트"만 하는 단순 액션 5개만 모았다.
 * @RequestMapping("/store/products")은 그대로라 URL은 하나도 안 바뀐다.
 */
@Controller
@RequestMapping("/store/products")
@RequiredArgsConstructor
public class StoreProductActionController {

	private final ProductService productService;

	@PostMapping("/{id}/publish")
	public String publish(@PathVariable Long id, HttpSession session) {
		Long ownerId = (Long) session.getAttribute("userId");
		if (ownerId == null) {
			return "redirect:/auth/loginForm";
		}
		boolean ok = productService.publishDraft(ownerId, id);
		return ok ? "redirect:/store/products" : "redirect:/store/products?tooLate";
	}

	@PostMapping("/{id}/discard")
	public String discard(@PathVariable Long id, HttpSession session) {
		Long ownerId = (Long) session.getAttribute("userId");
		if (ownerId == null) {
			return "redirect:/auth/loginForm";
		}
		productService.discardDraft(ownerId, id);
		return "redirect:/store/products";
	}

	@PostMapping("/{id}/soldout")
	public String soldOut(@PathVariable Long id, HttpSession session) {
		Long ownerId = (Long) session.getAttribute("userId");
		if (ownerId == null) {
			return "redirect:/auth/loginForm";
		}
		productService.markSoldOut(ownerId, id);
		return "redirect:/store/products";
	}

	@PostMapping("/{id}/resume")
	public String resume(@PathVariable Long id, HttpSession session) {
		Long ownerId = (Long) session.getAttribute("userId");
		if (ownerId == null) {
			return "redirect:/auth/loginForm";
		}
		productService.resumeSelling(ownerId, id);
		return "redirect:/store/products";
	}

	@PostMapping("/{id}/delete")
	public String delete(@PathVariable Long id, HttpSession session) {
		Long ownerId = (Long) session.getAttribute("userId");
		if (ownerId == null) {
			return "redirect:/auth/loginForm";
		}
		productService.delete(ownerId, id);
		return "redirect:/store/products";
	}
}

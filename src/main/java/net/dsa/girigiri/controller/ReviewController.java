package net.dsa.girigiri.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.service.ReviewService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/user/stores/{storeId}/reviews")
@RequiredArgsConstructor
public class ReviewController {

	private final ReviewService reviewService;

	@PostMapping
	public String submit(@PathVariable Long storeId,
						  @RequestParam int rating,
						  @RequestParam(required = false) String content,
						  @RequestParam(required = false) String imageUrl,
						  HttpSession session) {
		Long userId = (Long) session.getAttribute("userId");
		if (userId == null) {
			return "redirect:/auth/loginForm";
		}
		reviewService.submitReview(userId, storeId, rating, content, imageUrl);
		return "redirect:/user/stores/" + storeId;
	}

	/** 가게 사장님은 지울 수 없다 — 작성자 본인 / 관리자만. */
	@PostMapping("/{reviewId}/delete")
	public String delete(@PathVariable Long storeId, @PathVariable Long reviewId, HttpSession session) {
		Long userId = (Long) session.getAttribute("userId");
		if (userId == null) {
			return "redirect:/auth/loginForm";
		}
		String role = (String) session.getAttribute("role");
		reviewService.deleteReview(userId, role, reviewId);
		return "redirect:/user/stores/" + storeId;
	}
}

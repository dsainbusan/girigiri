package net.dsa.girigiri.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.security.LoginRequired;
import net.dsa.girigiri.service.ReviewService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * "내 리뷰 관리" — 내가 작성한 리뷰를 전체 매장에 걸쳐 모아 보는 화면.
 */
@Controller
@RequestMapping("/user/reviews")
@RequiredArgsConstructor
public class MyReviewController {

	private final ReviewService reviewService;

	@LoginRequired
	@GetMapping("/my")
	public String my(HttpSession session, Model model) {
		Long userId = (Long) session.getAttribute("userId");

		model.addAttribute("reviews", reviewService.getMyReviews(userId));
		
		return "reviewView/my";
	}
}
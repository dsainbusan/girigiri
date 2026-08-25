package net.dsa.girigiri.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.service.ReviewService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 강노은: "내 리뷰 관리" — 매장 하나에 종속된 화면이 아니라 내가 쓴 리뷰를 전체 매장에 걸쳐 모아 보는
 * 화면이라 ReviewController(/user/stores/{storeId}/reviews)와 분리했다. 수정/삭제 자체는 여전히
 * 해당 매장 상세 페이지의 기존 폼을 그대로 쓴다(수정은 "선택 후에" 하도록 이미 만들어둔 흐름 재사용).
 */
@Controller
@RequestMapping("/user/reviews")
@RequiredArgsConstructor
public class MyReviewController {

	private final ReviewService reviewService;

	@GetMapping("/my")
	public String my(HttpSession session, Model model) {
		Long userId = (Long) session.getAttribute("userId");
		if (userId == null) {
			return "redirect:/auth/loginForm";
		}
		model.addAttribute("reviews", reviewService.getMyReviews(userId));
		return "reviewView/my";
	}
}

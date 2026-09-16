package net.dsa.girigiri.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.security.LoginRequired;
import net.dsa.girigiri.service.ReviewService;
import net.dsa.girigiri.service.StoreAccessService;
import net.dsa.girigiri.service.StoreReviewService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 가게 사장님이 자기 매장 리뷰를 보고 답글을 남기는 화면 (WBS 3.0 "문의 답변/리뷰 답글",
 * 원래 김태훈 담당 → 문창호가 인수, 2026-09-17). 리뷰 조회는 ReviewService(강노은 담당)를 그대로
 * 재사용하고, 답글 등록/수정/삭제만 StoreReviewService에 위임한다.
 */
@Controller
@RequestMapping("/store/reviews")
@RequiredArgsConstructor
@LoginRequired
public class StoreReviewController {

	private final ReviewService reviewService;
	private final StoreReviewService storeReviewService;
	private final StoreAccessService storeAccessService;

	@GetMapping
	public String list(HttpSession session, Model model) {
		Long userId = (Long) session.getAttribute("userId");
		String role = (String) session.getAttribute("role");
		StoreEntity store = storeAccessService.findMyStore(userId).orElse(null);
		if (store == null) {
			return "redirect:/store/dashboard";
		}

		model.addAttribute("reviews", reviewService.getReviews(store.getId(), userId, role));
		model.addAttribute("averageRating", reviewService.getAverageRating(store.getId()));
		model.addAttribute("reviewCount", reviewService.getReviewCount(store.getId()));
		return "storeView/reviews";
	}

	@PostMapping("/{reviewId}/reply")
	public String reply(@PathVariable Long reviewId, @RequestParam String content, HttpSession session) {
		Long userId = (Long) session.getAttribute("userId");
		storeReviewService.reply(userId, reviewId, content);
		return "redirect:/store/reviews";
	}

	@PostMapping("/{reviewId}/reply/delete")
	public String deleteReply(@PathVariable Long reviewId, HttpSession session) {
		Long userId = (Long) session.getAttribute("userId");
		storeReviewService.deleteReply(userId, reviewId);
		return "redirect:/store/reviews";
	}
}

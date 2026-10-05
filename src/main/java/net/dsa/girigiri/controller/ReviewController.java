package net.dsa.girigiri.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.security.LoginRequired;
import net.dsa.girigiri.service.ReviewService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;



@Controller
@RequestMapping("/user/stores/{storeId}/reviews")
@RequiredArgsConstructor
public class ReviewController {

	private final ReviewService reviewService;

	// 추가됨 (강노은) — 왜: "내 리뷰 관리"(reviewView/my.html) 페이지에서 가게 상세로 안 보내고
	// 그 자리에서 바로 수정/삭제할 수 있게 하면서, 처리 후에도 원래 있던 페이지로 되돌아가야 함.
	// returnTo는 화이트리스트 값 하나만 허용 — 임의 문자열을 그대로 redirect에 쓰면 오픈 리다이렉트가 되니
	// "내 리뷰 관리" 한 곳만 허용하고, 그 외(없음/이상한 값)는 기존처럼 가게 상세로 돌려보낸다.
	private static final String MY_REVIEWS_PATH = "/user/reviews/my";

	private String resolveRedirect(String returnTo, Long storeId) {
		if (MY_REVIEWS_PATH.equals(returnTo)) {
			return "redirect:" + MY_REVIEWS_PATH;
		}
		return "redirect:/user/stores/" + storeId;
	}

	// 변경됨 (강노은, 2026-10-01) — "매장당 리뷰 1건"에서 "픽업완료 예약당 1건"으로 바뀌면서 작성/수정이
	// 더 이상 같은 (storeId, userId) 키로 묶이지 않는다(한 유저가 같은 매장에 리뷰를 여러 개 가질 수
	// 있음) — 그래서 작성(reservationId로 대상 지정)과 수정(reviewId로 대상 지정)을 별 엔드포인트로 뗐다.
	//
	// imagePhoto: 새로 올린 파일(선택). removeImage: 새 파일 없이 "기존 사진 삭제"만 요청하는 체크박스.
	// 둘 다 없으면 기존 사진을 그대로 유지한다(폼이 파일 input이라 기존 값을 다시 제출할 방법이 없어서).
	@LoginRequired
	@PostMapping
	public String create(@PathVariable Long storeId,
						  @RequestParam Long reservationId,
						  @RequestParam int rating,
						  @RequestParam(required = false) String content,
						  @RequestParam(required = false) MultipartFile imagePhoto,
						  @RequestParam(required = false) String returnTo,
						  HttpSession session,
						  RedirectAttributes redirectAttributes) {
		Long userId = (Long) session.getAttribute("userId");
		reviewService.createReview(userId, storeId, reservationId, rating, content, imagePhoto);
		redirectAttributes.addFlashAttribute("reviewMessage", "리뷰가 등록되었습니다.");
		return resolveRedirect(returnTo, storeId);
	}

	@LoginRequired
	@PostMapping("/{reviewId}/edit")
	public String update(@PathVariable Long storeId,
						  @PathVariable Long reviewId,
						  @RequestParam int rating,
						  @RequestParam(required = false) String content,
						  @RequestParam(required = false) MultipartFile imagePhoto,
						  @RequestParam(required = false, defaultValue = "false") boolean removeImage,
						  @RequestParam(required = false) String returnTo,
						  HttpSession session,
						  RedirectAttributes redirectAttributes) {
		Long userId = (Long) session.getAttribute("userId");
		reviewService.updateReview(userId, reviewId, rating, content, imagePhoto, removeImage);
		redirectAttributes.addFlashAttribute("reviewMessage", "리뷰가 수정되었습니다.");
		return resolveRedirect(returnTo, storeId);
	}

	/** 가게 사장님은 지울 수 없다 — 작성자 본인 / 관리자만. */
	@LoginRequired
	@PostMapping("/{reviewId}/delete")
	public String delete(@PathVariable Long storeId, @PathVariable Long reviewId,
						  @RequestParam(required = false) String returnTo,
						  HttpSession session) {
		Long userId = (Long) session.getAttribute("userId");
		String role = (String) session.getAttribute("role");
		reviewService.deleteReview(userId, role, reviewId);
		return resolveRedirect(returnTo, storeId);
	}
}

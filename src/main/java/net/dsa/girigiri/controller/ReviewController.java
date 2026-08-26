package net.dsa.girigiri.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
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

	// 변경됨 (강노은) — 왜: 사진 리뷰를 URL 문자열 입력에서 실제 파일 업로드로 바꿈.
	// imagePhoto: 새로 올린 파일(선택). removeImage: 새 파일 없이 "기존 사진 삭제"만 요청하는 체크박스.
	// 둘 다 없으면 기존 사진을 그대로 유지한다(폼이 파일 input이라 기존 값을 다시 제출할 방법이 없어서).
	//
	// 추가됨 (강노은) — 왜: 리뷰 등록/수정 버튼을 눌러도 처리 결과가 화면에 아무 표시 없이 그냥
	// 새로고침되는 것처럼 보여서, "등록/수정됐다"는 걸 알려주는 1회성 안내를 넣었다. flash
	// attribute라 리다이렉트된 화면에 한 번만 뜨고 새로고침하면 사라진다(예약 취소/수락 안내와 동일한 방식).
	@PostMapping
	public String submit(@PathVariable Long storeId,
						  @RequestParam int rating,
						  @RequestParam(required = false) String content,
						  @RequestParam(required = false) MultipartFile imagePhoto,
						  @RequestParam(required = false, defaultValue = "false") boolean removeImage,
						  @RequestParam(required = false) String returnTo,
						  HttpSession session,
						  RedirectAttributes redirectAttributes) {
		Long userId = (Long) session.getAttribute("userId");
		if (userId == null) {
			return "redirect:/auth/loginForm";
		}
		boolean isNew = reviewService.submitReview(userId, storeId, rating, content, imagePhoto, removeImage);
		redirectAttributes.addFlashAttribute("reviewMessage", isNew ? "리뷰가 등록되었습니다." : "리뷰가 수정되었습니다.");
		return resolveRedirect(returnTo, storeId);
	}

	/** 가게 사장님은 지울 수 없다 — 작성자 본인 / 관리자만. */
	@PostMapping("/{reviewId}/delete")
	public String delete(@PathVariable Long storeId, @PathVariable Long reviewId,
						  @RequestParam(required = false) String returnTo,
						  HttpSession session) {
		Long userId = (Long) session.getAttribute("userId");
		if (userId == null) {
			return "redirect:/auth/loginForm";
		}
		String role = (String) session.getAttribute("role");
		reviewService.deleteReview(userId, role, reviewId);
		return resolveRedirect(returnTo, storeId);
	}
}

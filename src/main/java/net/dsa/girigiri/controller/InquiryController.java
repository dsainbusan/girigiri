package net.dsa.girigiri.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.entity.InquiryEntity;
import net.dsa.girigiri.service.InquiryService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * 문의 게시판 (등록/상세/댓글). 특정 가게에 대한 문의(storeId 있음)와 서비스 전체에 대한
 * 일반 문의(storeId 없음)를 같은 게시판에서 다룬다.
 *
 * 열람 권한: 작성자 본인 / 문의 대상 가게 사장님 / 관리자만 볼 수 있다 — 공개 게시판이 아니다.
 * 그래서 WebSecurityConfig의 공개 경로 목록에도 없고(로그인 필수), 목록도 "전체"가 아니라
 * "이 사용자가 볼 수 있는 것"만 내려준다.
 */
@Controller
@RequestMapping("/user/inquiries")
@RequiredArgsConstructor
public class InquiryController {

	private final InquiryService inquiryService;

	@GetMapping
	public String list(HttpSession session, Model model) {
		Long userId = (Long) session.getAttribute("userId");
		if (userId == null) {
			return "redirect:/auth/loginForm";
		}
		String role = (String) session.getAttribute("role");
		model.addAttribute("inquiries", inquiryService.getInquiriesForUser(userId, role));
		return "inquiryView/list";
	}

	@GetMapping("/new")
	public String newForm(@RequestParam(required = false) Long storeId, HttpSession session, Model model) {
		if (session.getAttribute("userId") == null) {
			return "redirect:/auth/loginForm";
		}
		model.addAttribute("storeId", storeId);
		model.addAttribute("storeName", inquiryService.getStoreName(storeId));
		return "inquiryView/form";
	}

	@PostMapping
	public String create(@RequestParam(required = false) Long storeId,
						  @RequestParam String title,
						  @RequestParam String content,
						  HttpSession session) {
		Long userId = (Long) session.getAttribute("userId");
		if (userId == null) {
			return "redirect:/auth/loginForm";
		}
		Long inquiryId = inquiryService.createInquiry(userId, storeId, title, content);
		return "redirect:/user/inquiries/" + inquiryId;
	}

	@GetMapping("/{id}")
	public String detail(@PathVariable Long id, HttpSession session, Model model) {
		Long userId = (Long) session.getAttribute("userId");
		if (userId == null) {
			return "redirect:/auth/loginForm";
		}
		String role = (String) session.getAttribute("role");

		InquiryEntity inquiry = inquiryService.getInquiry(id);
		if (!inquiryService.canView(inquiry, userId, role)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "이 문의를 볼 수 있는 권한이 없습니다.");
		}

		String createdAtDisplay = inquiry.getCreatedAt() == null ? ""
				: inquiry.getCreatedAt().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));

		model.addAttribute("inquiry", inquiry);
		model.addAttribute("createdAtDisplay", createdAtDisplay);
		model.addAttribute("authorName", inquiryService.getAuthorName(inquiry.getUserId()));
		model.addAttribute("storeName", inquiryService.getStoreName(inquiry.getStoreId()));
		model.addAttribute("comments", inquiryService.getComments(id, userId, role));
		model.addAttribute("loggedIn", true);
		model.addAttribute("canDeleteInquiry", inquiryService.canDeleteInquiry(inquiry, userId, role));
		return "inquiryView/detail";
	}

	@PostMapping("/{id}/comments")
	public String addComment(@PathVariable Long id, @RequestParam String content, HttpSession session) {
		Long userId = (Long) session.getAttribute("userId");
		if (userId == null) {
			return "redirect:/auth/loginForm";
		}
		String role = (String) session.getAttribute("role");

		InquiryEntity inquiry = inquiryService.getInquiry(id);
		if (!inquiryService.canView(inquiry, userId, role)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "이 문의에 댓글을 남길 권한이 없습니다.");
		}

		inquiryService.addComment(userId, id, content);
		return "redirect:/user/inquiries/" + id;
	}

	/** 작성자 본인 / 관리자만 — 열람 권한(가게 사장님 포함)보다 좁다. 삭제하면 댓글도 같이 지운다. */
	@PostMapping("/{id}/delete")
	public String delete(@PathVariable Long id, HttpSession session) {
		Long userId = (Long) session.getAttribute("userId");
		if (userId == null) {
			return "redirect:/auth/loginForm";
		}
		String role = (String) session.getAttribute("role");
		inquiryService.deleteInquiry(userId, role, id);
		return "redirect:/user/inquiries";
	}

	@PostMapping("/{id}/comments/{commentId}/delete")
	public String deleteComment(@PathVariable Long id, @PathVariable Long commentId, HttpSession session) {
		Long userId = (Long) session.getAttribute("userId");
		if (userId == null) {
			return "redirect:/auth/loginForm";
		}
		String role = (String) session.getAttribute("role");
		inquiryService.deleteComment(userId, role, commentId);
		return "redirect:/user/inquiries/" + id;
	}
}

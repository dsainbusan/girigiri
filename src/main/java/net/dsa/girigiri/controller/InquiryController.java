package net.dsa.girigiri.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.entity.InquiryEntity;
import net.dsa.girigiri.domain.entity.ReservationEntity;
import net.dsa.girigiri.exception.ReservationAccessDeniedException;
import net.dsa.girigiri.service.InquiryService;
import net.dsa.girigiri.service.LookupService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
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
	// 추가됨 (2026-09-08) — "예약 상세에서 문의하기" 버튼. reservationId로 들어온 경우 폼에 예약
	// 요약을 보여주고 본인 예약인지 확인하기 위해서만 쓴다(저장 자체는 InquiryService가 다시 확인).
	private final LookupService lookupService;

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
	public String newForm(@RequestParam(required = false) Long storeId,
	                       @RequestParam(required = false) Long reservationId,
	                       HttpSession session, Model model) {
		Long userId = (Long) session.getAttribute("userId");
		if (userId == null) {
			return "redirect:/auth/loginForm";
		}

		if (reservationId != null) {
			ReservationEntity reservation = lookupService.getReservation(reservationId);
			if (!reservation.getUserId().equals(userId)) {
				throw new ReservationAccessDeniedException("본인 예약만 문의할 수 있어요.");
			}
			model.addAttribute("reservationId", reservationId);
			model.addAttribute("reservation", reservation);
			model.addAttribute("storeName", inquiryService.getStoreName(reservation.getStoreId()));
		} else {
			model.addAttribute("storeId", storeId);
			model.addAttribute("storeName", inquiryService.getStoreName(storeId));
		}
		return "inquiryView/form";
	}

	// 변경됨 (강노은) — 왜: 문의에 사진 첨부 기능 추가(수정은 없어서 새 파일 업로드만 받으면 됨).
	@PostMapping
	public String create(@RequestParam(required = false) Long storeId,
						  @RequestParam(required = false) Long reservationId,
						  @RequestParam String title,
						  @RequestParam String content,
						  @RequestParam(required = false) MultipartFile imagePhoto,
						  HttpSession session) {
		Long userId = (Long) session.getAttribute("userId");
		if (userId == null) {
			return "redirect:/auth/loginForm";
		}
		Long inquiryId = inquiryService.createInquiry(userId, storeId, reservationId, title, content, imagePhoto);
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
		model.addAttribute("reservationSummary", inquiryService.getReservationSummary(inquiry.getReservationId()));
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
		// 강노은: 원래 있던 "고객센터"(/user/support)의 "내 문의내역" 탭으로 돌려보낸다 — 이 컨트롤러의
		// list()/inquiryView/list.html은 그 화면과 중복이라 삭제 후에도 더는 거치지 않는다.
		return "redirect:/user/support";
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

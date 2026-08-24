package net.dsa.girigiri.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.InquiryRowDto;
import net.dsa.girigiri.service.InquiryService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

/**
 * 고객센터. 가게 상세의 "내 문의 내역" 버튼을 눌러 들어오고, 안에서 FAQ / 내 문의내역 / 문의하기를
 * 탭으로 전환한다(페이지 이동 없이 JS로 탭만 바꿈). "문의하기" 탭은 특정 가게가 아니라
 * 운영자(관리자)에게 보내는 일반 문의다 — storeId 없이 등록한다.
 */
@Controller
@RequestMapping("/user/support")
@RequiredArgsConstructor
public class SupportController {

	private final InquiryService inquiryService;

	@GetMapping
	public String home(HttpSession session, Model model) {
		Long userId = (Long) session.getAttribute("userId");
		boolean loggedIn = userId != null;
		model.addAttribute("loggedIn", loggedIn);

		if (loggedIn) {
			String role = (String) session.getAttribute("role");
			model.addAttribute("myInquiries", inquiryService.getMyInquiries(userId, role));
		}
		return "supportView/home";
	}
}

package net.dsa.girigiri.controller;

import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.service.BankAccountChangeService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpSession;

/**
 * 추가됨 (2026-10-07, 계좌 보안) — 슈퍼어드민 "계좌 변경 심사" 화면. settlements.html(지급 대기 +
 * 최근 이력 2단 구성)과 동일한 패턴. 승인/반려 둘 다 BankAccountChangeService로 위임하고, 이 컨트롤러는
 * 조회 조립 + 라우팅만 한다(레이어 규칙).
 */
@Controller
@RequestMapping("/superadmin/bank-account-requests")
@RequiredArgsConstructor
public class SuperAdminBankAccountController {

	private final BankAccountChangeService bankAccountChangeService;

	@GetMapping
	public String list(Model model) {
		model.addAttribute("pending", bankAccountChangeService.getPendingRequestRows());
		model.addAttribute("recent", bankAccountChangeService.getRecentRequestRows());
		return "superAdminView/bankAccountRequests";
	}

	@PostMapping("/{id}/approve")
	public String approve(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
		Long adminId = (Long) session.getAttribute("userId");
		bankAccountChangeService.approve(id, adminId);
		redirectAttributes.addFlashAttribute("resultMessage", "계좌 변경을 승인했어요.");
		return "redirect:/superadmin/bank-account-requests";
	}

	@PostMapping("/{id}/reject")
	public String reject(@PathVariable Long id, @RequestParam(required = false) String reason,
	                      HttpSession session, RedirectAttributes redirectAttributes) {
		Long adminId = (Long) session.getAttribute("userId");
		bankAccountChangeService.reject(id, adminId, reason);
		redirectAttributes.addFlashAttribute("resultMessage", "계좌 변경 신청을 반려했어요.");
		return "redirect:/superadmin/bank-account-requests";
	}
}

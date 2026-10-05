package net.dsa.girigiri.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.security.LoginRequired;
import net.dsa.girigiri.service.SettingsService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 마이페이지 하위 "환경설정" 화면 (2026-09-22, 문창호, WBS 6.1 인수 범위).
 * 조회/저장 로직은 전부 SettingsService에 위임한다.
 */
@Controller
@RequestMapping("/user/settings")
@RequiredArgsConstructor
@LoginRequired
public class SettingsController {

	private final SettingsService settingsService;

	@GetMapping
	public String settings(HttpSession session, Model model) {
		Long userId = (Long) session.getAttribute("userId");
		model.addAttribute("settings", settingsService.getSettingsView(userId));
		return "settingsView/settings";
	}

	@PostMapping("/marketing")
	public String updateMarketing(@RequestParam(required = false) Boolean marketingAgreed, HttpSession session) {
		Long userId = (Long) session.getAttribute("userId");
		settingsService.updateMarketingAgreed(userId, Boolean.TRUE.equals(marketingAgreed));
		return "redirect:/user/settings";
	}

	@PostMapping("/store-alerts/settlement")
	public String updateSettlementAlert(@RequestParam(required = false) Boolean enabled, HttpSession session) {
		Long userId = (Long) session.getAttribute("userId");
		settingsService.updateSettlementAlert(userId, Boolean.TRUE.equals(enabled));
		return "redirect:/user/settings";
	}

	@PostMapping("/store-alerts/automation")
	public String updateAutomationAlert(@RequestParam(required = false) Boolean enabled, HttpSession session) {
		Long userId = (Long) session.getAttribute("userId");
		settingsService.updateAutomationAlert(userId, Boolean.TRUE.equals(enabled));
		return "redirect:/user/settings";
	}

	// 추가됨 (2026-10-05, 사용자 요청) — 원래 NotificationController의 "알림 설정" 화면
	// (/user/alerts/settings)에서만 바꿀 수 있던 PUSH/찜한 가게 알림을, 뎁스 하나 줄이려고 이
	// 화면에서도 바로 바꿀 수 있게 한다. 값의 주인(NotificationService)은 그대로다 — 진입점만 하나 더.
	// store-alerts/settlement·automation과 같은 이유로 토글 2개를 각자 별도 폼·엔드포인트로 둔다 —
	// 한 폼에 합치면 토글 하나만 바꿔 제출해도 폼에 없는 다른 토글 값이 "체크 안 됨"으로 같이
	// 날아가(버튼에 required=false라 null→false로 처리됨) 덩달아 꺼져버린다.
	@PostMapping("/alerts/push")
	public String updatePushAlert(@RequestParam(required = false) Boolean enabled, HttpSession session) {
		Long userId = (Long) session.getAttribute("userId");
		settingsService.updatePushAlert(userId, Boolean.TRUE.equals(enabled));
		return "redirect:/user/settings";
	}

	@PostMapping("/alerts/like")
	public String updateLikeAlert(@RequestParam(required = false) Boolean enabled, HttpSession session) {
		Long userId = (Long) session.getAttribute("userId");
		settingsService.updateLikeAlert(userId, Boolean.TRUE.equals(enabled));
		return "redirect:/user/settings";
	}
}

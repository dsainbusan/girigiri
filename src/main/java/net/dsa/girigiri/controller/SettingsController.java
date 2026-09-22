package net.dsa.girigiri.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.Language;
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

	@GetMapping("/language")
	public String languageForm(HttpSession session, Model model) {
		Long userId = (Long) session.getAttribute("userId");
		model.addAttribute("currentCode", settingsService.getLanguageCode(userId));
		model.addAttribute("languages", Language.values());
		return "settingsView/language";
	}

	@PostMapping("/language")
	public String updateLanguage(@RequestParam String language, HttpSession session) {
		Long userId = (Long) session.getAttribute("userId");
		settingsService.updateLanguage(userId, language);
		return "redirect:/user/settings/language";
	}
}

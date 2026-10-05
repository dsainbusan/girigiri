package net.dsa.girigiri.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.security.LoginRequired;
import net.dsa.girigiri.service.UsageHistoryService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 추가됨 (강노은, 2026-10-05) — 마이페이지 "나의 활동 → 이용 내역". 픽업완료/노쇼/취소 내역을
 * 한 화면에서 기간별로 훑어볼 수 있게 한다.
 */
@Controller
@RequestMapping("/user/usage-history")
@RequiredArgsConstructor
public class UsageHistoryController {

	private final UsageHistoryService usageHistoryService;

	@LoginRequired
	@GetMapping
	public String list(HttpSession session, Model model) {
		Long userId = (Long) session.getAttribute("userId");
		model.addAttribute("history", usageHistoryService.getUsageHistory(userId));
		return "usageHistoryView/list";
	}
}

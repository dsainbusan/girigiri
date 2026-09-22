package net.dsa.girigiri.controller;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * 이용약관/개인정보처리방침 (2026-09-22, 문창호). authView/signup.html의 가입 동의 화면에 있던
 * "(임시) 정식 문서로 교체 예정" 안내를 그대로 화면 하나로 보여준다 — 환경설정에서 링크로 연결하려고
 * 신설했다. 실제 로그인 여부와 무관하게 봐도 되는 정적 안내라 @LoginRequired는 붙이지 않지만,
 * WebSecurityConfig의 PUBLIC_URLS에는 없어서 비로그인 상태로 들어오면 로그인 화면으로 먼저 간다
 * (지금은 환경설정 안에서만 링크하므로 문제없음 — 비로그인 접근이 필요해지면 그때 공개 목록에 추가).
 */
@Controller
@RequestMapping("/policy")
public class PolicyController {

	private static final Map<String, String> TITLES = Map.of(
			"terms", "이용약관",
			"privacy", "개인정보처리방침"
	);

	@GetMapping("/{type}")
	public String view(@PathVariable String type, Model model) {
		String title = TITLES.get(type);
		if (title == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND);
		}
		model.addAttribute("title", title);
		return "settingsView/policy";
	}
}

package net.dsa.girigiri.controller;

import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.service.MarketingService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 회사/서비스 소개용 마케팅 홈페이지 (2026-09-17 추가).
 *
 * "/"는 원래 지도 기반 앱 홈(HomeController)이 차지하고 있었는데, Too Good To Go·배달의민족처럼
 * 실제 서비스 진입 전에 회사 소개 페이지가 먼저 보이는 구조로 바꾸면서 여기로 옮겨왔다. 지도 앱
 * 홈은 "/app"으로 이동했고(HomeController 참고), 이 페이지의 "기리기리 서비스 이용하기" 버튼이
 * 그 경로로 연결된다.
 *
 * 완전히 다른 화면(데스크톱 반응형 풀와이드)이라 common/layout.html(모바일 420px 고정폭 앱 셸)을
 * 쓰지 않고 자체 HTML 문서 + 전용 CSS(marketing.css)를 쓴다 — 브랜드 컬러(tokens.css)만 공유.
 */
@Controller
@RequiredArgsConstructor
public class MarketingController {

	private final MarketingService marketingService;

	@GetMapping("/")
	public String home(Model model) {
		model.addAttribute("stats", marketingService.getImpactStats());
		return "marketingView/home";
	}
}

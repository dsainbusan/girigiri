package net.dsa.girigiri.controller;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 스타일 가이드 페이지 (개발 참고용).
 * 접속: GET /styleguide
 * 공통 컴포넌트(버튼·카드·칩·뱃지 등)를 한 화면에서 확인하는 용도.
 *
 * 수정됨 (2026-10-06, 제출 전 점검) — 왜: 로그인 없이 누구나 볼 수 있었다(WebSecurityConfig의
 * PUBLIC_URLS). 운영 배포 전 dev 프로필 한정 노출로 바꾸라는 TODO를 처리 — @Profile("dev")라
 * dev 프로필(application.properties 기본값)이 아니면 이 컨트롤러 빈 자체가 안 만들어져서
 * /styleguide·/styleguide/admin이 404가 된다. WebSecurityConfig의 permitAll 목록은 그대로
 * 둬도 된다(매핑 자체가 없으면 permitAll이어도 404라 보안에 영향 없음).
 */
@Profile("dev")
@Controller
public class StyleguideController {

	@GetMapping("/styleguide")
	public String styleguide() {
		return "common/styleguide";
	}

	@GetMapping("/styleguide/admin")
	public String styleguideAdmin() {
		return "common/styleguide-admin";
	}
}

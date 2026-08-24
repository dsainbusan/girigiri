package net.dsa.girigiri.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 스타일 가이드 페이지 (개발 참고용).
 * 접속: GET /styleguide
 * 공통 컴포넌트(버튼·카드·칩·뱃지 등)를 한 화면에서 확인하는 용도.
 *
 * TODO(송보미): 운영 배포 전 dev 프로필 한정 노출 등으로 교체할 것.
 */
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

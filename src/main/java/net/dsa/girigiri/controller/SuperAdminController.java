package net.dsa.girigiri.controller;

import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.repository.UserRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 슈퍼어드민(플랫폼 운영자) 화면 라우팅.
 * common/layout-admin 을 쓰는 wide 레이아웃 전용 — 나머지 컨트롤러(common/layout, 420px)와는 별도 트랙.
 *
 * TODO(송보미): 회원 관리(members)만 우선 UserRepository로 실데이터 연동했다. 나머지(매장 승인/신고·문의/
 *   공지/코드)는 해당 엔티티가 아직 없어 계속 데모 데이터 — Repository 생기는 대로 순서대로 교체할 것.
 *   문창호의 role 분리 작업이 끝나면 role=SUPERADMIN 기준 접근 제어를 여기(혹은 시큐리티 설정)에 추가할 것.
 */
@Controller
@RequestMapping("/superadmin")
@RequiredArgsConstructor
public class SuperAdminController {

	private final UserRepository userRepository;

	@GetMapping("/dashboard")
	public String dashboard() {
		return "superAdminView/dashboard";
	}

	@GetMapping("/members")
	public String members(Model model) {
		model.addAttribute("users", userRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")));
		return "superAdminView/members";
	}

	@GetMapping("/stores")
	public String stores() {
		return "superAdminView/stores";
	}

	@GetMapping("/reports")
	public String reports() {
		return "superAdminView/reports";
	}

	@GetMapping("/notices")
	public String notices() {
		return "superAdminView/notices";
	}

	@GetMapping("/codes")
	public String codes() {
		return "superAdminView/codes";
	}
}

package net.dsa.girigiri.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dsa.girigiri.domain.entity.UserEntity;
import net.dsa.girigiri.repository.StoreRepository;
import net.dsa.girigiri.repository.UserRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 마이페이지 및 회원정보 관리 컨트롤러
 * 담당: 문창호 (WBS 2.1 인증 및 회원정보 수정 / 6.1 마이페이지)
 */
@Slf4j
@Controller
@RequestMapping("/mypage")
@RequiredArgsConstructor
public class MypageController {

	private final UserRepository userRepository;
	private final StoreRepository storeRepository;

	/**
	 * 마이페이지 메인 화면
	 */
	@GetMapping
	public String mypage(HttpSession session, Model model) {
		Long userId = (Long) session.getAttribute("userId");
		if (userId == null) {
			return "redirect:/auth/loginForm";
		}

		userRepository.findById(userId).ifPresent(user -> {
			model.addAttribute("user", user);

			// 가입/절약 시작일 계산 (절약 N일째)
			long daysJoined = 1;
			if (user.getCreatedAt() != null) {
				daysJoined = ChronoUnit.DAYS.between(user.getCreatedAt().toLocalDate(), LocalDate.now()) + 1;
			}
			model.addAttribute("daysJoined", daysJoined);

			// TODO(문창호): WBS 6.1 절약 집계 로직 완성 시 실데이터로 교체 (현재는 기획 목업용 기본 절약액)
			model.addAttribute("monthlySavings", "42,300");
		});

		storeRepository.findByOwnerId(userId).ifPresent(store -> model.addAttribute("store", store));

		return "mypageView/mypage";
	}

	/**
	 * 회원정보 수정 화면
	 */
	@GetMapping("/edit")
	public String editForm(HttpSession session, Model model) {
		Long userId = (Long) session.getAttribute("userId");
		if (userId == null) {
			return "redirect:/auth/loginForm";
		}

		userRepository.findById(userId).ifPresent(user -> model.addAttribute("user", user));
		storeRepository.findByOwnerId(userId).ifPresent(store -> model.addAttribute("store", store));

		return "mypageView/edit";
	}

	/**
	 * 회원정보 수정 처리
	 */
	@PostMapping("/edit")
	public String updateProfile(@RequestParam String nickname,
	                            @RequestParam(required = false) String region,
	                            HttpSession session) {
		Long userId = (Long) session.getAttribute("userId");
		if (userId == null) {
			return "redirect:/auth/loginForm";
		}

		String trimmedNickname = nickname == null ? "" : nickname.trim();
		if (trimmedNickname.length() < 2 || trimmedNickname.length() > 10) {
			return "redirect:/mypage/edit?error";
		}

		UserEntity user = userRepository.findById(userId).orElseThrow();
		user.setNickname(trimmedNickname);
		user.setRegion(region == null || region.isBlank() ? null : region.trim());
		userRepository.save(user);

		return "redirect:/mypage";
	}

	/**
	 * 회원 탈퇴 처리
	 * TODO(담당 미정): 점주(OWNER)가 매장을 보유한 채로 탈퇴하면 StoreEntity.ownerId가 가리키는
	 * 유저가 사라져 고아 데이터가 된다 — FK 매핑/ERD가 아직 확정 전이라 지금은 손대지 않음.
	 */
	@PostMapping("/withdraw")
	public String withdraw(HttpSession session) {
		Long userId = (Long) session.getAttribute("userId");
		if (userId == null) {
			return "redirect:/auth/loginForm";
		}

		userRepository.deleteById(userId);
		session.invalidate();

		return "redirect:/";
	}
}

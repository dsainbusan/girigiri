package net.dsa.girigiri.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dsa.girigiri.security.LoginRequired;
import net.dsa.girigiri.service.MypageService;
import net.dsa.girigiri.service.StoreAccessService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 마이페이지 및 회원정보 관리 컨트롤러
 * 담당: 문창호 (WBS 2.1 인증 및 회원정보 수정 / 6.1 마이페이지)
 */
@Slf4j
@Controller
@RequestMapping("/mypage")
@RequiredArgsConstructor
public class MypageController {

	private final MypageService mypageService;
	private final StoreAccessService storeAccessService;

	// 추가됨 — 왜: 회원정보 수정 화면의 "GPS로 활동 지역 채우기" 버튼용(카카오 지오코더 좌표→주소).
	@Value("${kakao.map.js-key}")
	private String kakaoMapJsKey;

	/**
	 * 마이페이지 메인 화면
	 */
	@LoginRequired
	@GetMapping
	public String mypage(HttpSession session, Model model) {
		Long userId = (Long) session.getAttribute("userId");
		mypageService.findUser(userId).ifPresent(user -> {
			model.addAttribute("user", user);

			// 가입/절약 시작일 계산 (절약 N일째)
			model.addAttribute("daysJoined", mypageService.calculateDaysJoined(user));

			// TODO(문창호): WBS 6.1 절약 집계 로직 완성 시 실데이터로 교체 (현재는 기획 목업용 기본 절약액)
			model.addAttribute("monthlySavings", "42,300");
		});

		storeAccessService.findMyStore(userId).ifPresent(store -> model.addAttribute("store", store));

		return "mypageView/mypage";
	}

	/**
	 * 회원정보 수정 화면
	 */
	@LoginRequired
	@GetMapping("/edit")
	public String editForm(HttpSession session, Model model) {
		Long userId = (Long) session.getAttribute("userId");
		mypageService.findUser(userId).ifPresent(user -> model.addAttribute("user", user));
		storeAccessService.findMyStore(userId).ifPresent(store -> model.addAttribute("store", store));
		model.addAttribute("kakaoMapJsKey", kakaoMapJsKey);

		return "mypageView/edit";
	}

	/**
	 * 회원정보 수정 처리 (닉네임·활동지역·휴대폰)
	 */
	@LoginRequired
	@PostMapping("/edit")
	public String updateProfile(@RequestParam String nickname,
	                            @RequestParam(required = false) String phone,
	                            @RequestParam(required = false) String region,
	                            HttpSession session) {
		Long userId = (Long) session.getAttribute("userId");
		return switch (mypageService.updateProfile(userId, nickname, phone, region)) {
			case SUCCESS -> "redirect:/mypage";
			case INVALID_NICKNAME -> "redirect:/mypage/edit?error";
			case INVALID_PHONE -> "redirect:/mypage/edit?error=phone";
			case PHONE_TAKEN -> "redirect:/mypage/edit?error=phonedup";
		};
	}

	/**
	 * 비밀번호 변경 (이메일 계정만). 새 비밀번호 확인 불일치는 화면 JS로도 막지만 서버에서도 본다.
	 */
	@LoginRequired
	@PostMapping("/password")
	public String changePassword(@RequestParam String currentPassword,
	                             @RequestParam String newPassword,
	                             @RequestParam String newPasswordConfirm,
	                             HttpSession session) {
		if (!newPassword.equals(newPasswordConfirm)) {
			return "redirect:/mypage/edit?pwError=mismatch";
		}
		Long userId = (Long) session.getAttribute("userId");
		return switch (mypageService.changePassword(userId, currentPassword, newPassword)) {
			case SUCCESS -> "redirect:/mypage/edit?pwChanged";
			case WRONG_CURRENT -> "redirect:/mypage/edit?pwError=current";
			case TOO_SHORT -> "redirect:/mypage/edit?pwError=short";
			case NOT_EMAIL_ACCOUNT -> "redirect:/mypage/edit";
		};
	}

	/**
	 * 회원 탈퇴 처리. 미완료 예약이 있으면 막는다 — 사유는 MypageService.canWithdraw 참고.
	 */
	@LoginRequired
	@PostMapping("/withdraw")
	public String withdraw(HttpSession session) {
		Long userId = (Long) session.getAttribute("userId");
		if (!mypageService.canWithdraw(userId)) {
			return "redirect:/mypage/edit?withdrawError";
		}

		mypageService.withdraw(userId);
		session.invalidate();

		// 탈퇴 완료 안내 화면으로 (세션이 없어졌으므로 PUBLIC_URLS에 등록된 경로).
		return "redirect:/auth/withdraw-complete";
	}
}

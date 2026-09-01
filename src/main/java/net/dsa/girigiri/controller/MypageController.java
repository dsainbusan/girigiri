package net.dsa.girigiri.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.domain.entity.UserEntity;
import net.dsa.girigiri.repository.ReservationRepository;
import net.dsa.girigiri.repository.StoreRepository;
import net.dsa.girigiri.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 마이페이지 및 회원정보 관리 컨트롤러
 * 담당: 문창호 (WBS 2.1 인증 및 회원정보 수정 / 6.1 마이페이지)
 */
@Slf4j
@Controller
@RequestMapping("/mypage")
@RequiredArgsConstructor
public class MypageController {

	private static final List<String> INCOMPLETE_RESERVATION_STATUSES = List.of("pending", "confirmed");

	private final UserRepository userRepository;
	private final StoreRepository storeRepository;
	private final ReservationRepository reservationRepository;

	// 추가됨 — 왜: 회원정보 수정 화면의 "GPS로 활동 지역 채우기" 버튼용(카카오 지오코더 좌표→주소).
	@Value("${kakao.map.js-key}")
	private String kakaoMapJsKey;

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
		model.addAttribute("kakaoMapJsKey", kakaoMapJsKey);

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
	 * 미완료 예약(결제대기/진행중, 아직 픽업·취소·노쇼 처리가 안 된 건)이 있으면 탈퇴를 막는다 —
	 * 손님 입장에서 결제만 하고 계정이 사라지면 픽업/환불 처리가 불가능해지고, 점주 입장에서도
	 * 매장에 남은 예약이 붕 뜨기 때문. 그 외 케이스(예: 점주가 매장을 보유한 채 탈퇴)는 기존 그대로
	 * 둔다 — StoreEntity.ownerId 고아 데이터 문제는 FK 매핑/ERD 확정 전이라 별도 논의 필요.
	 */
	@PostMapping("/withdraw")
	public String withdraw(HttpSession session) {
		Long userId = (Long) session.getAttribute("userId");
		if (userId == null) {
			return "redirect:/auth/loginForm";
		}

		if (reservationRepository.existsByUserIdAndStatusIn(userId, INCOMPLETE_RESERVATION_STATUSES)) {
			return "redirect:/mypage/edit?withdrawError";
		}

		StoreEntity store = storeRepository.findByOwnerId(userId).orElse(null);
		if (store != null
				&& reservationRepository.existsByStoreIdAndStatusIn(store.getId(), INCOMPLETE_RESERVATION_STATUSES)) {
			return "redirect:/mypage/edit?withdrawError";
		}

		userRepository.deleteById(userId);
		session.invalidate();

		return "redirect:/";
	}
}

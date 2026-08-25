package net.dsa.girigiri.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.entity.NotificationSettingEntity;
import net.dsa.girigiri.service.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 강노은: 인앱 알림함 + 알림 설정. 알림이 실제로 생기는 트리거는 아직 안 붙어서(NotificationService
 * 상단 주석 참고) 지금은 빈 목록만 보일 수 있다 — UI/읽음 처리/설정 자체는 완결적으로 동작한다.
 */
@Controller
@RequestMapping("/user/alerts")
@RequiredArgsConstructor
public class NotificationController {

	private final NotificationService notificationService;

	@GetMapping
	public String list(HttpSession session, Model model) {
		Long userId = (Long) session.getAttribute("userId");
		if (userId == null) {
			return "redirect:/auth/loginForm";
		}
		model.addAttribute("notifications", notificationService.getNotifications(userId));
		model.addAttribute("unreadCount", notificationService.getUnreadCount(userId));
		return "alertView/list";
	}

	/**
	 * 강노은: 실시간 알림(SSE) 구독. 브라우저의 EventSource가 이 연결을 열어두고 있다가,
	 * 새 알림이 생기거나 읽음 처리될 때마다 서버가 최신 안읽음 개수를 밀어준다(NotificationService 참고).
	 * 로그인 안 됐으면 리다이렉트가 의미 없는 응답 형식이라 401로 응답한다.
	 */
	@GetMapping("/stream")
	public SseEmitter stream(HttpSession session) {
		Long userId = (Long) session.getAttribute("userId");
		if (userId == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
		}
		return notificationService.subscribe(userId);
	}

	/** 알림 클릭 시 진입점 — 읽음 처리 후 linkUrl로 보낸다(없으면 알림함에 그대로 머문다). */
	@GetMapping("/{id}")
	public String open(@PathVariable Long id, HttpSession session) {
		Long userId = (Long) session.getAttribute("userId");
		if (userId == null) {
			return "redirect:/auth/loginForm";
		}
		String linkUrl = notificationService.markRead(userId, id);
		return linkUrl != null && !linkUrl.isBlank() ? "redirect:" + linkUrl : "redirect:/user/alerts";
	}

	@PostMapping("/read-all")
	public String readAll(HttpSession session) {
		Long userId = (Long) session.getAttribute("userId");
		if (userId == null) {
			return "redirect:/auth/loginForm";
		}
		notificationService.markAllRead(userId);
		return "redirect:/user/alerts";
	}

	@GetMapping("/settings")
	public String settings(HttpSession session, Model model) {
		Long userId = (Long) session.getAttribute("userId");
		if (userId == null) {
			return "redirect:/auth/loginForm";
		}
		NotificationSettingEntity settings = notificationService.getOrCreateSettings(userId);
		model.addAttribute("pushEnabled", settings.isPushEnabled());
		model.addAttribute("likeAlertEnabled", settings.isLikeAlertEnabled());
		return "alertView/settings";
	}

	@PostMapping("/settings")
	public String updateSettings(@RequestParam(required = false) Boolean pushEnabled,
								  @RequestParam(required = false) Boolean likeAlertEnabled,
								  HttpSession session) {
		Long userId = (Long) session.getAttribute("userId");
		if (userId == null) {
			return "redirect:/auth/loginForm";
		}
		// 체크박스는 체크 안 하면 폼에 값 자체가 안 실려온다 — null이면 false로 간주.
		notificationService.updateSettings(userId, Boolean.TRUE.equals(pushEnabled), Boolean.TRUE.equals(likeAlertEnabled));
		return "redirect:/user/alerts/settings?saved";
	}
}

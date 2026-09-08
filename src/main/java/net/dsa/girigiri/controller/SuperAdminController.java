package net.dsa.girigiri.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.SuperAdminDashboardStatsDto;
import net.dsa.girigiri.service.NotificationService;
import net.dsa.girigiri.service.SuperAdminDashboardService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * 슈퍼어드민(플랫폼 운영자) 대시보드/공통코드/알림 라우팅.
 * common/layout-admin 을 쓰는 wide 레이아웃 전용 — 나머지 컨트롤러(common/layout, 420px)와는 별도 트랙.
 *
 * 2026-09-03, 레이어 규칙 2단계 — 회원/매장/공지사항/신고·문의는 도메인별 컨트롤러
 * (SuperAdminMemberController/SuperAdminStoreController/SuperAdminNoticeController/
 * SuperAdminSupportController)로 분리됐고, 여기는 그 어디에도 깔끔히 안 묶이는 나머지
 * (대시보드/공통코드/알림)만 남는다.
 */
@Controller
@RequestMapping("/superadmin")
@RequiredArgsConstructor
public class SuperAdminController {

	private final SuperAdminDashboardService dashboardService;
	private final NotificationService notificationService;

	@GetMapping("/dashboard")
	public String dashboard(Model model) {
		SuperAdminDashboardStatsDto stats = dashboardService.getDashboardStats();

		model.addAttribute("pendingInquiryCount", stats.pendingInquiryCount());
		model.addAttribute("pendingComplaintCount", stats.pendingComplaintCount());
		model.addAttribute("weeklySignupBars", stats.weeklySignupBars());
		model.addAttribute("calendarDays", stats.calendarDays());
		model.addAttribute("calendarMonthLabel", stats.calendarMonthLabel());

		return "superAdminView/dashboard";
	}

	@GetMapping("/codes")
	public String codes() {
		return "superAdminView/codes";
	}

	/**
	 * 알림 패널에서 알림 하나를 클릭했을 때. NotificationController#open(/user/alerts/{id})와
	 * 동일한 패턴 — 읽음 처리 후 linkUrl로 보낸다(없으면 대시보드로).
	 */
	@GetMapping("/notifications/{id}")
	public String openNotification(@PathVariable Long id) {
		Long adminId = dashboardService.findAdminIdOrNull();
		if (adminId == null) {
			return "redirect:/superadmin/dashboard";
		}
		String linkUrl = notificationService.markRead(adminId, id);
		return linkUrl != null && !linkUrl.isBlank() ? "redirect:" + linkUrl : "redirect:/superadmin/dashboard";
	}

	@PostMapping("/notifications/read-all")
	public String readAllNotifications(@RequestHeader(value = "Referer", required = false) String referer,
										HttpServletRequest request) {
		Long adminId = dashboardService.findAdminIdOrNull();
		if (adminId != null) {
			notificationService.markAllRead(adminId);
		}
		return "redirect:" + resolveReturnTo(referer, request);
	}

	// 추가됨 (2026-09-08, 코드 감사) — 알림 패널이 슈퍼어드민 공용 레이아웃(layout-admin)에 있어서
	// 어느 화면에서나 이 폼이 제출될 수 있다 보니 "방금 있던 화면으로" 되돌리는 값을 Referer 헤더
	// 그대로 썼다. ReviewController#resolveRedirect가 임의 문자열을 그대로 redirect에 안 쓰고
	// 화이트리스트로 거르듯, 여기도 같은 호스트(OriginCheckFilter와 동일한 판정) + "/superadmin/"
	// 경로일 때만 그 값을 쓰고, 그 외(다른 호스트, 이상한 형식, 없음)는 대시보드로 보낸다.
	private String resolveReturnTo(String referer, HttpServletRequest request) {
		if (referer == null || referer.isBlank()) {
			return "/superadmin/dashboard";
		}
		try {
			URI uri = new URI(referer);
			boolean sameHost = uri.getHost() != null && uri.getHost().equalsIgnoreCase(request.getServerName());
			boolean superAdminPath = uri.getPath() != null && uri.getPath().startsWith("/superadmin/");
			if (sameHost && superAdminPath) {
				return referer;
			}
		} catch (URISyntaxException e) {
			// 형식이 이상하면 판단 불가 — 안전하게 대시보드로.
		}
		return "/superadmin/dashboard";
	}
}

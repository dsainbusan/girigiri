package net.dsa.girigiri.controller;

import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.entity.NoticeEntity;
import net.dsa.girigiri.service.LookupService;
import net.dsa.girigiri.service.SuperAdminNoticeService;
import net.dsa.girigiri.util.PaginationUtil;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 슈퍼어드민(플랫폼 운영자) "공지사항 관리" 화면 라우팅.
 * 2026-09-03, SuperAdminController에서 도메인 분리 — 레이어 규칙 2단계.
 */
@Controller
@RequestMapping("/superadmin")
@RequiredArgsConstructor
public class SuperAdminNoticeController {

	// 글이 쌓일수록 목록이 길어지는 화면의 페이지당 행 수.
	private static final int PAGE_SIZE = 10;

	private final SuperAdminNoticeService noticeService;
	private final LookupService lookupService;

	@GetMapping("/notices")
	public String notices(@RequestParam(defaultValue = "0") int page, Model model) {
		List<NoticeEntity> allNotices = noticeService.findAllSortedByNewest();
		model.addAttribute("notices", PaginationUtil.paginate(allNotices, page, PAGE_SIZE));
		model.addAttribute("totalPages", PaginationUtil.totalPages(allNotices.size(), PAGE_SIZE));
		model.addAttribute("page", page);
		return "superAdminView/notices";
	}

	@GetMapping("/notices/new")
	public String noticeNewForm() {
		return "superAdminView/noticeNew";
	}

	@PostMapping("/notices/new")
	public String noticeCreate(@RequestParam String title, @RequestParam String content,
	                            @RequestParam(required = false) String publishStartAt,
	                            @RequestParam(required = false) String publishEndAt) {
		SuperAdminNoticeService.SaveResult result = noticeService.create(title, content, publishStartAt, publishEndAt);
		return switch (result) {
			case INVALID -> "redirect:/superadmin/notices/new?error";
			case INVALID_PERIOD -> "redirect:/superadmin/notices/new?error=period";
			case SUCCESS -> "redirect:/superadmin/notices";
		};
	}

	@GetMapping("/notices/{id}")
	public String noticeDetail(@PathVariable Long id, Model model) {
		model.addAttribute("notice", lookupService.getNotice(id));
		return "superAdminView/noticeDetail";
	}

	@GetMapping("/notices/{id}/edit")
	public String noticeEditForm(@PathVariable Long id, Model model) {
		model.addAttribute("notice", lookupService.getNotice(id));
		return "superAdminView/noticeEdit";
	}

	@PostMapping("/notices/{id}/edit")
	public String noticeEditSubmit(@PathVariable Long id,
	                                @RequestParam String title,
	                                @RequestParam String content,
	                                @RequestParam(required = false) Boolean published,
	                                @RequestParam(required = false) String publishStartAt,
	                                @RequestParam(required = false) String publishEndAt) {
		SuperAdminNoticeService.SaveResult result =
				noticeService.update(id, title, content, published, publishStartAt, publishEndAt);
		return switch (result) {
			case INVALID -> "redirect:/superadmin/notices/" + id + "/edit?error";
			case INVALID_PERIOD -> "redirect:/superadmin/notices/" + id + "/edit?error=period";
			case SUCCESS -> "redirect:/superadmin/notices/" + id;
		};
	}

	@PostMapping("/notices/{id}/unpublish")
	public String noticeUnpublish(@PathVariable Long id) {
		noticeService.setPublished(id, false);
		return "redirect:/superadmin/notices/" + id;
	}

	@PostMapping("/notices/{id}/publish")
	public String noticePublish(@PathVariable Long id) {
		noticeService.setPublished(id, true);
		return "redirect:/superadmin/notices/" + id;
	}

	@PostMapping("/notices/{id}/delete")
	public String noticeDelete(@PathVariable Long id) {
		noticeService.delete(id);
		return "redirect:/superadmin/notices";
	}
}

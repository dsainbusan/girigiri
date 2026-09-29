package net.dsa.girigiri.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.entity.StoreAnnouncementEntity;
import net.dsa.girigiri.security.LoginRequired;
import net.dsa.girigiri.service.StoreAccessService;
import net.dsa.girigiri.service.StoreAnnouncementService;
import net.dsa.girigiri.util.PaginationUtil;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * 점주가 자기 매장 공지를 등록/수정/삭제하는 화면. 2026-09-29 신규 (WBS "매장 공지사항 게시판
 * 관리" — 원래 김태훈 담당, 인수 후 실제 CRUD로 완성).
 *
 * "/store/notices"는 이미 슈퍼어드민 공지를 점주가 읽는 기존 기능이 쓰고 있어서 겹치지 않게
 * "/store/announcements"를 쓴다.
 */
@Controller
@RequestMapping("/store/announcements")
@RequiredArgsConstructor
@LoginRequired
public class StoreAnnouncementController {

	private static final int PAGE_SIZE = 5;
	private static final int PAGE_WINDOW = 5;

	private final StoreAccessService storeAccessService;
	private final StoreAnnouncementService storeAnnouncementService;

	private Long resolveCurrentStoreId(HttpSession session) {
		Long userId = (Long) session.getAttribute("userId");
		return storeAccessService.getMyStore(userId).getId();
	}

	@GetMapping
	public String list(@RequestParam(defaultValue = "0") int page, HttpSession session, Model model) {
		Long storeId = resolveCurrentStoreId(session);
		List<StoreAnnouncementEntity> all = storeAnnouncementService.getForStore(storeId);

		int totalPages = PaginationUtil.totalPages(all.size(), PAGE_SIZE);
		int safePage = Math.max(0, Math.min(page, totalPages - 1));
		int windowStart = Math.max(0, Math.min(safePage - 2, totalPages - PAGE_WINDOW));

		model.addAttribute("announcements", PaginationUtil.paginate(all, safePage, PAGE_SIZE));
		model.addAttribute("page", safePage);
		model.addAttribute("totalPages", totalPages);
		model.addAttribute("pageWindowStart", windowStart);
		model.addAttribute("pageWindowEnd", Math.min(totalPages - 1, windowStart + PAGE_WINDOW - 1));
		return "storeView/announcements";
	}

	@GetMapping("/new")
	public String newForm(Model model) {
		model.addAttribute("mode", "new");
		return "storeView/announcementForm";
	}

	@PostMapping("/new")
	public String create(@RequestParam String title, @RequestParam String content,
	                     HttpSession session) {
		Long storeId = resolveCurrentStoreId(session);
		StoreAnnouncementService.SaveResult result = storeAnnouncementService.create(storeId, title, content);
		if (result == StoreAnnouncementService.SaveResult.INVALID) {
			return "redirect:/store/announcements/new?error";
		}
		return "redirect:/store/announcements";
	}

	@GetMapping("/{id}/edit")
	public String editForm(@PathVariable Long id, HttpSession session, Model model) {
		Long storeId = resolveCurrentStoreId(session);
		StoreAnnouncementEntity announcement = storeAnnouncementService.findByIdForStore(id, storeId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "공지를 찾을 수 없어요."));
		model.addAttribute("mode", "edit");
		model.addAttribute("announcement", announcement);
		return "storeView/announcementForm";
	}

	@PostMapping("/{id}/edit")
	public String update(@PathVariable Long id, @RequestParam String title, @RequestParam String content,
	                     HttpSession session) {
		Long storeId = resolveCurrentStoreId(session);
		StoreAnnouncementEntity announcement = storeAnnouncementService.findByIdForStore(id, storeId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "공지를 찾을 수 없어요."));
		StoreAnnouncementService.SaveResult result = storeAnnouncementService.update(announcement, title, content);
		if (result == StoreAnnouncementService.SaveResult.INVALID) {
			return "redirect:/store/announcements/" + id + "/edit?error";
		}
		return "redirect:/store/announcements";
	}

	@PostMapping("/{id}/expose")
	public String expose(@PathVariable Long id, @RequestParam(defaultValue = "0") int page, HttpSession session) {
		Long storeId = resolveCurrentStoreId(session);
		storeAnnouncementService.setExposed(id, storeId);
		return "redirect:/store/announcements" + (page > 0 ? "?page=" + page : "");
	}

	@PostMapping("/{id}/delete")
	public String delete(@PathVariable Long id, @RequestParam(defaultValue = "0") int page, HttpSession session) {
		Long storeId = resolveCurrentStoreId(session);
		StoreAnnouncementEntity announcement = storeAnnouncementService.findByIdForStore(id, storeId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "공지를 찾을 수 없어요."));
		storeAnnouncementService.delete(announcement);
		return "redirect:/store/announcements" + (page > 0 ? "?page=" + page : "");
	}
}

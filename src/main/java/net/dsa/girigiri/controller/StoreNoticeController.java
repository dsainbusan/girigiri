package net.dsa.girigiri.controller;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.security.LoginRequired;
import net.dsa.girigiri.service.StoreNoticeService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

/**
 * 점주가 슈퍼어드민 공지사항을 읽기 전용으로 보는 화면 (WBS 3.0 "공지사항 게시판 관리",
 * 원래 김태훈 담당 → 문창호가 인수, 2026-09-16). 작성/수정/삭제는 여전히 슈퍼어드민 전용
 * (SuperAdminNoticeController, 송보미 담당) — 여기는 목록/상세 조회만 담당한다.
 */
@Controller
@RequestMapping("/store/notices")
@RequiredArgsConstructor
@LoginRequired
public class StoreNoticeController {

	private final StoreNoticeService storeNoticeService;

	@GetMapping
	public String list(Model model) {
		model.addAttribute("notices", storeNoticeService.findActiveSortedByNewest());
		return "storeView/notices";
	}

	@GetMapping("/{id}")
	public String detail(@PathVariable Long id, Model model) {
		try {
			model.addAttribute("notice", storeNoticeService.findActiveById(id));
		} catch (EntityNotFoundException e) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "공지사항을 찾을 수 없어요.");
		}
		return "storeView/noticeDetail";
	}
}

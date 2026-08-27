package net.dsa.girigiri.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.TemplateRowDto;
import net.dsa.girigiri.domain.entity.ListingTemplateEntity;
import net.dsa.girigiri.domain.entity.MenuItemEntity;
import net.dsa.girigiri.service.ListingTemplateService;
import net.dsa.girigiri.service.PosCatalogService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * "오늘의 구제 자동 등록" 템플릿 CRUD 화면 — 2026-08-26 신규 (문창호).
 * 라우팅은 /store/templates/**. StoreController(/store)·StoreProductController(/store/products)와 겹치지 않는다.
 */
@Controller
@RequestMapping("/store/templates")
@RequiredArgsConstructor
public class StoreTemplateController {

	private final ListingTemplateService templateService;
	private final PosCatalogService posCatalogService;

	@GetMapping
	public String list(HttpSession session, Model model) {
		Long ownerId = (Long) session.getAttribute("userId");
		if (ownerId == null) {
			return "redirect:/auth/loginForm";
		}
		try {
			List<TemplateRowDto> rows = templateService.listForOwner(ownerId).stream()
					.map(this::toRow)
					.toList();
			model.addAttribute("templates", rows);
		} catch (ResponseStatusException e) {
			return "redirect:/auth/owner-apply";
		}
		return "storeView/templates";
	}

	private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
	private static final String[] KOR_DAY = {"", "월", "화", "수", "목", "금", "토", "일"};

	private TemplateRowDto toRow(ListingTemplateEntity t) {
		return new TemplateRowDto(
				t.getId(), t.getName(), t.getImageUrl(),
				t.getOriginalPrice() == null ? 0 : t.getOriginalPrice(),
				t.getDefaultQuantity() == null ? 0 : t.getDefaultQuantity(),
				weekdaysLabel(t.getWeekdays()),
				t.getPromptTime() == null ? "" : t.getPromptTime().format(TIME_FMT),
				t.isActive());
	}

	private String weekdaysLabel(String csv) {
		if (csv == null || csv.isBlank()) {
			return "";
		}
		Set<Integer> days = new LinkedHashSet<>();
		for (String s : csv.split(",")) {
			try {
				days.add(Integer.parseInt(s.trim()));
			} catch (NumberFormatException ignored) {
			}
		}
		if (days.equals(Set.of(1, 2, 3, 4, 5, 6, 7))) {
			return "매일";
		}
		if (days.equals(Set.of(1, 2, 3, 4, 5))) {
			return "평일";
		}
		if (days.equals(Set.of(6, 7))) {
			return "주말";
		}
		StringBuilder sb = new StringBuilder();
		for (int d : days) {
			if (d >= 1 && d <= 7) {
				if (sb.length() > 0) {
					sb.append("·");
				}
				sb.append(KOR_DAY[d]);
			}
		}
		return sb.toString();
	}

	@GetMapping("/new")
	public String createForm(@RequestParam(required = false) Long menuItemId, HttpSession session, Model model) {
		Long ownerId = (Long) session.getAttribute("userId");
		if (ownerId == null) {
			return "redirect:/auth/loginForm";
		}

		ListingTemplateEntity prefill = null;
		if (menuItemId != null) {
			// POS 카탈로그에서 넘어온 경우 — 품목명·원가·사진을 임시 객체에 담아 폼에 프리필 (저장 안 함)
			MenuItemEntity menu = posCatalogService.getOwnedMenuItem(ownerId, menuItemId);
			prefill = ListingTemplateEntity.builder()
					.name(menu.getName())
					.originalPrice(menu.getOriginalPrice())
					.imageUrl(menu.getImageUrl())
					.build();
		}

		model.addAttribute("mode", "create");
		model.addAttribute("template", prefill);
		return "storeView/templateForm";
	}

	@PostMapping
	public String create(@RequestParam String name,
	                     @RequestParam(required = false) Integer originalPrice,
	                     @RequestParam(required = false) Integer defaultQuantity,
	                     @RequestParam(required = false) List<Integer> weekdays,
	                     @RequestParam(required = false) String promptTime,
	                     @RequestParam(required = false) String description,
	                     @RequestParam(required = false) MultipartFile image,
	                     HttpSession session) {
		Long ownerId = (Long) session.getAttribute("userId");
		if (ownerId == null) {
			return "redirect:/auth/loginForm";
		}
		try {
			templateService.create(ownerId, name, originalPrice, defaultQuantity, weekdays, promptTime, description, image);
		} catch (ResponseStatusException e) {
			return "redirect:/store/templates/new?error";
		}
		return "redirect:/store/templates";
	}

	@GetMapping("/{id}/edit")
	public String editForm(@PathVariable Long id, HttpSession session, Model model) {
		Long ownerId = (Long) session.getAttribute("userId");
		if (ownerId == null) {
			return "redirect:/auth/loginForm";
		}
		ListingTemplateEntity template = templateService.getOwned(ownerId, id);
		model.addAttribute("mode", "edit");
		model.addAttribute("template", template);
		return "storeView/templateForm";
	}

	@PostMapping("/{id}")
	public String update(@PathVariable Long id,
	                     @RequestParam String name,
	                     @RequestParam(required = false) Integer originalPrice,
	                     @RequestParam(required = false) Integer defaultQuantity,
	                     @RequestParam(required = false) List<Integer> weekdays,
	                     @RequestParam(required = false) String promptTime,
	                     @RequestParam(required = false) String description,
	                     @RequestParam(required = false) MultipartFile image,
	                     HttpSession session) {
		Long ownerId = (Long) session.getAttribute("userId");
		if (ownerId == null) {
			return "redirect:/auth/loginForm";
		}
		try {
			templateService.update(ownerId, id, name, originalPrice, defaultQuantity, weekdays, promptTime, description, image);
		} catch (ResponseStatusException e) {
			return "redirect:/store/templates/" + id + "/edit?error";
		}
		return "redirect:/store/templates";
	}

	@PostMapping("/{id}/toggle")
	public String toggle(@PathVariable Long id, HttpSession session) {
		Long ownerId = (Long) session.getAttribute("userId");
		if (ownerId == null) {
			return "redirect:/auth/loginForm";
		}
		templateService.toggleActive(ownerId, id);
		return "redirect:/store/templates";
	}

	@PostMapping("/{id}/delete")
	public String delete(@PathVariable Long id, HttpSession session) {
		Long ownerId = (Long) session.getAttribute("userId");
		if (ownerId == null) {
			return "redirect:/auth/loginForm";
		}
		templateService.delete(ownerId, id);
		return "redirect:/store/templates";
	}
}

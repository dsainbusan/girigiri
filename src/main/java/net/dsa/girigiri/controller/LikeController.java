package net.dsa.girigiri.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.LikedStoreDto;
import net.dsa.girigiri.service.LikeService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequestMapping("/user/likes")
@RequiredArgsConstructor
public class LikeController {

	private final LikeService likeService;

	@GetMapping
	public String list(HttpSession session, Model model) {
		Long userId = (Long) session.getAttribute("userId");
		if (userId == null) {
			return "redirect:/auth/loginForm";
		}

		List<LikedStoreDto> likedStores = likeService.getLikedStores(userId);
		long onSaleCount = likedStores.stream().filter(LikedStoreDto::onSale).count();

		model.addAttribute("likedStores", likedStores);
		model.addAttribute("totalCount", likedStores.size());
		model.addAttribute("onSaleCount", onSaleCount);
		return "likeView/list";
	}
}

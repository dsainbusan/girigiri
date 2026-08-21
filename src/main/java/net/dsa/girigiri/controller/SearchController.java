package net.dsa.girigiri.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.StoreCardDto;
import net.dsa.girigiri.service.LikeService;
import net.dsa.girigiri.service.SearchService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * TODO(강노은): "픽업 가능 시간" 필터(지금 바로/저녁 18~21시/마감임박 21시~)는 목업엔 있지만
 * 상품에 픽업 시간대 데이터가 없어서 아직 시각적 요소만 두고 실제 필터링은 연결하지 않았다.
 * Product/Store에 픽업 가능 시간대 필드가 생기면 SearchService에 조건을 추가할 것.
 */
@Controller
@RequestMapping("/user/search")
@RequiredArgsConstructor
public class SearchController {

	private final SearchService searchService;
	private final LikeService likeService;

	@GetMapping
	public String search(@RequestParam(required = false) String q,
						  @RequestParam(required = false, defaultValue = "discount") String sort,
						  @RequestParam(required = false) String price,
						  HttpSession session,
						  Model model) {
		Long userId = (Long) session.getAttribute("userId");
		List<StoreCardDto> results = searchService.search(q, sort, price, likeService.getLikedStoreIds(userId));

		model.addAttribute("keyword", q == null ? "" : q);
		model.addAttribute("sort", sort);
		model.addAttribute("price", price);
		model.addAttribute("results", results);
		model.addAttribute("resultCount", results.size());
		return "searchView/results";
	}
}

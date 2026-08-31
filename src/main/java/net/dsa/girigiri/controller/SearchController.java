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
						  // 추가됨 (강노은) — 왜: "픽업 가능 시간" 필터. StoreEntity.lastPickupTime/prepTimeMinutes를
						  // 재사용해 판단한다(SearchService.matchesPickupBucket 참고). "now"|"evening"|"late"|null.
						  @RequestParam(required = false) String pickup,
						  // 추가됨 (강노은) — 왜: 거리순 정렬용. 서버는 사용자 위치를 모르니 브라우저 Geolocation API로
						  // 받은 좌표를 쿼리 파라미터로 넘겨받는다. 둘 다 없으면 거리 계산 자체를 건너뛴다.
						  @RequestParam(required = false) Double lat,
						  @RequestParam(required = false) Double lng,
						  HttpSession session,
						  Model model) {
		Long userId = (Long) session.getAttribute("userId");
		List<StoreCardDto> results = searchService.search(q, sort, price, pickup, likeService.getLikedStoreIds(userId), lat, lng);

		model.addAttribute("keyword", q == null ? "" : q);
		model.addAttribute("sort", sort);
		model.addAttribute("price", price);
		model.addAttribute("pickup", pickup);
		model.addAttribute("lat", lat);
		model.addAttribute("lng", lng);
		model.addAttribute("results", results);
		model.addAttribute("resultCount", results.size());
		return "searchView/results";
	}
}

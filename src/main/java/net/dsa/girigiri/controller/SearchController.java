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
						  // 변경됨 (강노은, 2026-09-21) — 왜: "가격대" 필터를 프리셋 버킷(under5000 등) 대신
						  // 슬라이더/직접입력으로 바꾸면서 min/max 숫자 그대로 받는다. 둘 다 null이면 전체.
						  @RequestParam(required = false) Integer priceMin,
						  @RequestParam(required = false) Integer priceMax,
						  // 변경됨 (강노은, 2026-09-21) — 왜: "픽업 가능 시간"도 프리셋 버킷 대신 시간 단위
						  // 슬라이더(from~to)로 바꿨다. 기존 "지금 바로" 옵션은 실시간 판단이라 슬라이더로
						  // 표현이 안 돼서 별도 체크박스(pickupNow)로 남겨 슬라이더와 함께(AND) 적용한다.
						  @RequestParam(required = false) Boolean pickupNow,
						  @RequestParam(required = false) Integer pickupFrom,
						  @RequestParam(required = false) Integer pickupTo,
						  // 추가됨 (강노은) — 왜: 거리순 정렬용. 서버는 사용자 위치를 모르니 브라우저 Geolocation API로
						  // 받은 좌표를 쿼리 파라미터로 넘겨받는다. 둘 다 없으면 거리 계산 자체를 건너뛴다.
						  @RequestParam(required = false) Double lat,
						  @RequestParam(required = false) Double lng,
						  HttpSession session,
						  Model model) {
		Long userId = (Long) session.getAttribute("userId");
		List<StoreCardDto> results = searchService.search(q, sort, priceMin, priceMax, pickupNow, pickupFrom, pickupTo,
				likeService.getLikedStoreIds(userId), lat, lng);

		model.addAttribute("keyword", q == null ? "" : q);
		model.addAttribute("sort", sort);
		model.addAttribute("priceMin", priceMin);
		model.addAttribute("priceMax", priceMax);
		// pickupNow=false는 화면상 "체크 안 함"과 같은 뜻이라 null로 넘겨 URL 쿼리스트링에서 아예 빼버린다
		// (다른 필터들처럼 "값 없음 = 전체" 관례를 유지 — th:href에서 null 파라미터는 자동으로 생략된다).
		model.addAttribute("pickupNow", Boolean.TRUE.equals(pickupNow) ? Boolean.TRUE : null);
		model.addAttribute("pickupFrom", pickupFrom);
		model.addAttribute("pickupTo", pickupTo);
		model.addAttribute("lat", lat);
		model.addAttribute("lng", lng);
		model.addAttribute("results", results);
		model.addAttribute("resultCount", results.size());
		return "searchView/results";
	}
}

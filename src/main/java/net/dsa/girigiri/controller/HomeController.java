package net.dsa.girigiri.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.StoreCardDto;
import net.dsa.girigiri.domain.dto.StoreMapDto;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.repository.StoreRepository;
import net.dsa.girigiri.service.HomeService;
import net.dsa.girigiri.service.LikeService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/**
 * TODO(강노은): 지금은 매장 전체를 좌표만 보고 뿌린다(지도).
 * 카드 리스트(storeCards)는 active 상품 있는 매장만 나온다 — 카테고리 필터는 다음 작업.
 */
@Controller
@RequiredArgsConstructor
public class HomeController {

	private final StoreRepository storeRepository;
	private final HomeService homeService;
	private final LikeService likeService;

	@Value("${kakao.map.js-key}")
	private String kakaoMapJsKey;

	@GetMapping("/")
	public String home(HttpSession session, Model model) {
		Long userId = (Long) session.getAttribute("userId");
		List<StoreMapDto> stores = storeRepository.findAll().stream()
				.filter(store -> store.getLatitude() != null && store.getLongitude() != null)
				.map(this::toMapDto)
				.toList();
		List<StoreCardDto> storeCards = homeService.getActiveStoreCards(likeService.getLikedStoreIds(userId));

		model.addAttribute("kakaoMapJsKey", kakaoMapJsKey);
		model.addAttribute("stores", stores);
		model.addAttribute("storeCards", storeCards);
		model.addAttribute("todayRescueCount", homeService.getTodayRescueCount());

		// TODO(강노은): GPS/역지오코딩 연동 전까지 고정값. 알림 기능 붙으면 unreadCount도 실데이터로 교체.
		model.addAttribute("location", "내 동네");
		model.addAttribute("unreadCount", 0);
		return "home";
	}

	private StoreMapDto toMapDto(StoreEntity store) {
		return StoreMapDto.builder()
				.id(store.getId())
				.storeName(store.getStoreName())
				.category(store.getCategory())
				.latitude(store.getLatitude())
				.longitude(store.getLongitude())
				.build();
	}
}

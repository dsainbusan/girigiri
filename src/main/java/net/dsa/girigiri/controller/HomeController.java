package net.dsa.girigiri.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.StoreCardDto;
import net.dsa.girigiri.domain.dto.StoreMapDto;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.repository.StoreRepository;
import net.dsa.girigiri.service.HomeService;
import net.dsa.girigiri.service.LikeService;
import net.dsa.girigiri.service.NotificationService;
import net.dsa.girigiri.service.RecommendationService;
import net.dsa.girigiri.util.KakaoGeocodingClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 강노은: 지도(stores)는 좌표 있는 매장 전체를 그대로 뿌린다 — 카테고리 필터가 안 걸린다.
 * 카드 리스트(storeCards)는 active 상품 있는 매장만 나오고, 카테고리 필터는 home.html의
 * chip-row가 클라이언트 사이드로 처리한다(카드만 대상, 지도 마커는 그대로 유지).
 */
@Controller
@RequiredArgsConstructor
public class HomeController {

	private final StoreRepository storeRepository;
	private final HomeService homeService;
	private final LikeService likeService;
	private final NotificationService notificationService;
	private final RecommendationService recommendationService;
	private final KakaoGeocodingClient kakaoGeocodingClient;

	@Value("${kakao.map.js-key}")
	private String kakaoMapJsKey;

	// 추가됨 (강노은) — 왜: "현재 위치 기준 거리순 카드 목록" 요구사항 — 서버는 사용자 위치를 모르니
	// home.html의 JS가 브라우저 Geolocation으로 좌표를 받아 이 파라미터를 붙여 자동으로 한 번 다시
	// 불러온다(검색 페이지의 거리순 정렬과 같은 방식). 좌표가 없으면(권한 거부/미지원) 기존처럼
	// 마감임박순으로 폴백 — HomeService.getActiveStoreCards 참고.
	@GetMapping("/")
	public String home(@RequestParam(required = false) Double lat,
						@RequestParam(required = false) Double lng,
						HttpSession session, Model model) {
		Long userId = (Long) session.getAttribute("userId");
		var likedStoreIds = likeService.getLikedStoreIds(userId);
		List<StoreMapDto> stores = storeRepository.findAll().stream()
				.filter(store -> store.getLatitude() != null && store.getLongitude() != null)
				.map(this::toMapDto)
				.toList();
		List<StoreCardDto> storeCards = homeService.getActiveStoreCards(likedStoreIds, lat, lng);

		model.addAttribute("kakaoMapJsKey", kakaoMapJsKey);
		model.addAttribute("stores", stores);
		model.addAttribute("storeCards", storeCards);
		model.addAttribute("todayRescueCount", homeService.getTodayRescueCount());
		// 강노은: 개인화 추천 섹션 (WBS 4.0 탐색·검색, 맨 마지막으로 미뤄뒀던 항목) — RecommendationService 참고.
		// 메인 목록(storeCards)에 이미 나온 매장은 추천에서 제외하고, 위치 정보도 그대로 넘겨서
		// 추천 카드도 메인 카드처럼 실제 거리/마감임박이 표시되게 한다.
		Set<Long> shownStoreIds = storeCards.stream().map(StoreCardDto::getStoreId).collect(Collectors.toSet());
		model.addAttribute("recommendation",
				recommendationService.getRecommendations(userId, likedStoreIds, shownStoreIds, lat, lng));

		// 강노은: 좌표가 있으면 실제 동네 이름으로(KakaoGeocodingClient), 없거나 실패하면 "내 동네" 폴백.
		model.addAttribute("location", resolveLocationLabel(lat, lng));
		model.addAttribute("unreadCount", notificationService.getUnreadCount(userId));
		model.addAttribute("loggedIn", userId != null); // SSE 알림 구독은 로그인했을 때만 열게(home.html)
		return "home";
	}

	/** 좌표 없거나(Geolocation 미허용) 역지오코딩 실패 시 기존처럼 "내 동네" 고정 문구로 폴백한다. */
	private String resolveLocationLabel(Double lat, Double lng) {
		if (lat == null || lng == null) {
			return "내 동네";
		}
		return kakaoGeocodingClient.reverseGeocode(lat, lng).orElse("내 동네");
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

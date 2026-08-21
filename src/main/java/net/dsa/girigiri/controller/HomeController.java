package net.dsa.girigiri.controller;

import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.StoreMapDto;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.repository.StoreRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/**
 * TODO(강노은): 지금은 매장 전체를 좌표만 보고 뿌린다.
 * "마감세일 중" 필터(Product 재고/status 연동)는 재고 등록 기능이 들어온 뒤에 추가.
 */
@Controller
@RequiredArgsConstructor
public class HomeController {

	private final StoreRepository storeRepository;

	@Value("${kakao.map.js-key}")
	private String kakaoMapJsKey;

	@GetMapping("/")
	public String home(Model model) {
		List<StoreMapDto> stores = storeRepository.findAll().stream()
				.filter(store -> store.getLatitude() != null && store.getLongitude() != null)
				.map(this::toMapDto)
				.toList();

		model.addAttribute("kakaoMapJsKey", kakaoMapJsKey);
		model.addAttribute("stores", stores);
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

package net.dsa.girigiri.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * TODO(강노은): 지도 마커/카드 리스트는 StoreRepository 연동 후 실데이터로 교체.
 * 현재는 카카오맵 SDK 연동 확인용 데모 좌표(서울시청)다.
 */
@Controller
public class HomeController {

	@Value("${kakao.map.js-key}")
	private String kakaoMapJsKey;

	@GetMapping("/")
	public String home(Model model) {
		model.addAttribute("kakaoMapJsKey", kakaoMapJsKey);
		return "home";
	}
}

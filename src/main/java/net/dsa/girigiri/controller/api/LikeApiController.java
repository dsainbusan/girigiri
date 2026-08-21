package net.dsa.girigiri.controller.api;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.service.LikeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 찜하기 토글 API. 홈/검색/가게상세 화면의 하트 버튼이 fetch로 호출한다.
 * 로그인 세션이 없으면 401을 내려주고, 프론트에서 로그인 화면으로 안내한다.
 */
@RestController
@RequestMapping("/api/likes")
@RequiredArgsConstructor
public class LikeApiController {

	private final LikeService likeService;

	@PostMapping("/{storeId}/toggle")
	public ResponseEntity<Map<String, Object>> toggle(@PathVariable Long storeId, HttpSession session) {
		Long userId = (Long) session.getAttribute("userId");
		if (userId == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "login_required"));
		}
		boolean liked = likeService.toggle(userId, storeId);
		return ResponseEntity.ok(Map.of("liked", liked));
	}
}

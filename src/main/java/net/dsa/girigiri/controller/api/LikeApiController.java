package net.dsa.girigiri.controller.api;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dsa.girigiri.security.LoginRequired;
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
@Slf4j
@RestController
@RequestMapping("/api/likes")
@RequiredArgsConstructor
public class LikeApiController {

	private final LikeService likeService;

	// 변경됨 (2026-09-08, 코드 감사) — 로그인 체크를 LoginRequiredInterceptor로 위임(수십 곳 반복되던
	// 보일러플레이트 정리). @RestController라 인터셉터가 자동으로 401 + {"error":"login_required"}를
	// 내려줘서 app.js의 찜하기 fetch 핸들러(res.status===401 분기) 동작은 그대로다.
	@LoginRequired
	@PostMapping("/{storeId}/toggle")
	public ResponseEntity<Map<String, Object>> toggle(@PathVariable Long storeId, HttpSession session) {
		Long userId = (Long) session.getAttribute("userId");
		// 추가됨 (2026-09-08, 코드 감사) — 여기서 예상 못한 예외(DB 오류 등)가 나면 GlobalExceptionHandler가
		// HTML 뷰 이름을 돌려줘서 fetch의 JSON 파싱이 깨졌다(ChatController#sendMessage와 같은 문제).
		try {
			boolean liked = likeService.toggle(userId, storeId);
			return ResponseEntity.ok(Map.of("liked", liked));
		} catch (Exception e) {
			log.error("> [LikeApiController] 찜 토글 처리 중 오류 - storeId={}", storeId, e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "toggle_failed"));
		}
	}
}

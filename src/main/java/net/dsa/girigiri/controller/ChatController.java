package net.dsa.girigiri.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.ChatRequestDto;
import net.dsa.girigiri.domain.dto.ChatResponseDto;
import net.dsa.girigiri.service.ChatService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 고객 지원 챗봇 API 컨트롤러.
 * 담당: 송채현 (WBS 6.5 고객 지원 챗봇)
 *
 * 마이페이지(templates/mypageView/mypage.html) 안에 있는 채팅 위젯에서만 호출된다 — 챗봇은
 * 로그인한 사용자에게만 노출된다(REQ-F-120). WebSecurityConfig의 PUBLIC_URLS에 /mypage/**가
 * 없어서 로그인 안 한 요청은 Spring Security가 먼저 걸러내지만(로그인 화면으로 리다이렉트),
 * 세션 값을 직접 한 번 더 확인해서 비로그인 요청엔 401을 명시적으로 돌려준다(REQ-NF-112).
 */
@RestController
@RequestMapping("/mypage/chat")
@RequiredArgsConstructor
public class ChatController {

	private final ChatService chatService;

	@PostMapping("/message")
	public ResponseEntity<ChatResponseDto> sendMessage(@RequestBody ChatRequestDto request, HttpSession session) {
		Long userId = (Long) session.getAttribute("userId");
		if (userId == null) {
			return ResponseEntity.status(401).body(ChatResponseDto.failed("로그인이 필요해요."));
		}

		String role = (String) session.getAttribute("role");
		ChatResponseDto response = chatService.sendMessage(role, request);
		return ResponseEntity.ok(response);
	}
}

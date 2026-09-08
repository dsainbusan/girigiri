package net.dsa.girigiri.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.ChatRequestDto;
import net.dsa.girigiri.domain.dto.ChatResponseDto;
import net.dsa.girigiri.security.LoginRequired;
import net.dsa.girigiri.service.ChatService;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@RestController
@RequestMapping("/mypage/chat")
@RequiredArgsConstructor
public class ChatController {

	private final ChatService chatService;

	// 변경됨 (2026-09-08, 코드 감사) — 로그인 체크를 LoginRequiredInterceptor로 위임(수십 곳 반복되던
	// 보일러플레이트 정리). 비로그인 401은 이제 인터셉터가 내려주는데, mypage.html의 챗봇 위젯은 이미
	// res.status===401만 보고 자체 "로그인이 필요해요" 문구를 쓰지 응답 바디는 안 읽어서 그대로 동작한다.
	@LoginRequired
	@PostMapping("/message")
	public ResponseEntity<ChatResponseDto> sendMessage(@RequestBody ChatRequestDto request, HttpSession session) {
		Long userId = (Long) session.getAttribute("userId");
		// 변경됨 (2026-09-01) — 왜: role은 불변 권한이라 "사장님이 유저 모드로 전환해서 보는 중"인
		// 경우를 구분 못 한다. AuthController.toggleMode()로 viewMode가 이미 세션에 들어오고 있어서
		// (문창호님 파트 완료), 화면 분기는 팀 컨벤션대로 viewMode 기준으로 바꿨다.
		String viewMode = (String) session.getAttribute("viewMode");
		// 추가됨 (2026-09-08, 코드 감사) — 왜: chatService.sendMessage 안에서 Gemini 호출 등이
		// 예상 못한 예외를 던지면(네트워크 오류, 응답 파싱 실패 등) 그대로 500 에러 화면으로
		// 튀어서 챗봇 위젯이 깨진 것처럼 보였다. 사용자에게는 재시도 안내만 보여주고, 실제 원인은
		// 로그로 남긴다.
		try {
			ChatResponseDto response = chatService.sendMessage(userId, viewMode, request);
			return ResponseEntity.ok(response);
		} catch (Exception e) {
			log.error("> [ChatController] 챗봇 응답 처리 중 예상 못한 오류 - userId={}", userId, e);
			return ResponseEntity.ok(ChatResponseDto.failed("죄송해요, 답변 중 오류가 발생했어요. 잠시 후 다시 시도해주세요."));
		}
	}
}

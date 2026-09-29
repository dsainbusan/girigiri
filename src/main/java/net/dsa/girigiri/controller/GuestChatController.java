package net.dsa.girigiri.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dsa.girigiri.domain.dto.ChatRequestDto;
import net.dsa.girigiri.domain.dto.ChatResponseDto;
import net.dsa.girigiri.service.ChatService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 비회원용 고객 지원 챗봇 API 컨트롤러 (2026-09-29 추가, 담당: 송채현).
 *
 * 왜: 마케팅 홈(marketingView/home.html)과 FAQ(common/faq.html)는 로그인 전 방문자가 보는 화면인데,
 * 기존 챗봇(ChatController, /mypage/chat/**)은 로그인한 사용자에게만 노출된다(REQ-F-120)는 전제가
 * 코드 곳곳에 박혀 있어서(마이페이지 전용 위젯, 세션 userId 필수) 그대로 재사용할 수 없었다.
 * ChatController/ChatService.sendMessage()는 이 클래스가 전혀 참조하지 않으므로 기존 회원용 흐름은
 * 그대로다 — 완전히 분리된 새 진입점만 하나 만들었다.
 *
 * @LoginRequired를 붙이지 않는다 — 이 API는 로그인 없이 호출 가능해야 한다. 대신
 * WebSecurityConfig의 PUBLIC_URLS에 "/support/chat/**"를 추가해서 Spring Security가 비로그인
 * 요청을 먼저 막지 않도록 했다.
 */
@Slf4j
@RestController
@RequestMapping("/support/chat")
@RequiredArgsConstructor
public class GuestChatController {

	private final ChatService chatService;

	@PostMapping("/message")
	public ResponseEntity<ChatResponseDto> sendMessage(@RequestBody ChatRequestDto request,
			HttpServletRequest httpRequest) {
		String guestKey = resolveGuestKey(httpRequest);
		try {
			ChatResponseDto response = chatService.sendGuestMessage(guestKey, request);
			return ResponseEntity.ok(response);
		} catch (Exception e) {
			log.error("> [GuestChatController] 비회원 챗봇 응답 처리 중 예상 못한 오류 - guestKey={}", guestKey, e);
			return ResponseEntity.ok(ChatResponseDto.failed("죄송해요, 답변 중 오류가 발생했어요. 잠시 후 다시 시도해주세요."));
		}
	}

	/**
	 * 요청 제한(ChatService.isGuestRateLimited)에 쓸 키. 세션이 이미 있으면(직전 메시지로 이미
	 * 만들어진 세션 등) 세션 ID를 쓰고, 없으면(대부분의 첫 요청 — 이 컨트롤러는 세션을 새로
	 * 만들지 않는다) IP로 묶는다. 이 프로젝트는 리버스 프록시 뒤에 있지 않아서 X-Forwarded-For 같은
	 * 헤더는 보지 않는다(AuthController의 다른 IP 기반 제한들과 동일한 방식 — request.getRemoteAddr()).
	 */
	private String resolveGuestKey(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		return session != null ? "sess:" + session.getId() : "ip:" + request.getRemoteAddr();
	}
}

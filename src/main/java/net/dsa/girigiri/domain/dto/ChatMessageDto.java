package net.dsa.girigiri.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 챗봇 대화 한 턴(사용자 발화 또는 챗봇 응답)을 표현하는 DTO.
 *
 * 대화 내역은 서버가 세션/DB에 저장하지 않는다 — 채팅 UI(프론트)가 지금까지 주고받은 메시지를
 * 배열로 들고 있다가, 다음 메시지를 보낼 때마다 이 형태로 통째로 다시 함께 보내준다. "새 대화
 * 시작" 버튼(REQ-F-125)을 누르면 프론트가 들고 있던 이 배열만 비우면 되므로 서버 쪽 초기화
 * 처리가 따로 필요 없다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageDto {

	private String role;      // "user" | "assistant"
	private String content;   // 그 턴의 발화/응답 내용
}

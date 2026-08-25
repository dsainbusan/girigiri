package net.dsa.girigiri.domain.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 채팅 UI(마이페이지 챗봇 위젯)가 메시지를 보낼 때 쓰는 요청 DTO.
 * history는 서버 저장분이 아니라 프론트가 들고 있는 지금까지의 대화 내역 전체다.
 */
@Getter
@Setter
@NoArgsConstructor
public class ChatRequestDto {

	private String message;
	private List<ChatMessageDto> history;
}

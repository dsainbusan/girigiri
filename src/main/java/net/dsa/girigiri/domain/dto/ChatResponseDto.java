package net.dsa.girigiri.domain.dto;

import lombok.Getter;

/**
 * 챗봇 API 응답 DTO. success=false면 reply는 없고 errorMessage에 채팅창에 그대로 보여줄
 * 사람이 읽을 수 있는 안내 문구가 담긴다(REQ-F-124 오류 응답 및 예외 처리).
 */
@Getter
public class ChatResponseDto {

	private final boolean success;
	private final String reply;
	private final String errorMessage;

	private ChatResponseDto(boolean success, String reply, String errorMessage) {
		this.success = success;
		this.reply = reply;
		this.errorMessage = errorMessage;
	}

	public static ChatResponseDto success(String reply) {
		return new ChatResponseDto(true, reply, null);
	}

	public static ChatResponseDto failed(String errorMessage) {
		return new ChatResponseDto(false, null, errorMessage);
	}
}

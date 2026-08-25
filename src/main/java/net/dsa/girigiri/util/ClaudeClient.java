package net.dsa.girigiri.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.dsa.girigiri.domain.dto.ChatMessageDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * 고객 지원 챗봇이 쓰는 Claude API(Anthropic Messages API) 연동 유틸.
 * 담당: 송채현 (WBS 6.5 고객 지원 챗봇)
 *
 * PortOneClient와 같은 이유로 별도 SDK 의존성을 추가하지 않고 자바 표준 HttpClient로 REST API를
 * 직접 호출한다. 대화 내역(history)은 서버가 세션/DB에 저장하지 않고, 매 요청마다 프론트(채팅
 * UI)가 지금까지 주고받은 메시지를 그대로 다시 보내주는 방식이다 — 요구사항정의서 REQ-F-125
 * "챗봇 대화 초기화"가 "새 대화 시작" 버튼을 누르면 프론트가 들고 있던 history를 비우기만 하면
 * 되는 것도 이 구조 덕분이다 (서버에 별도로 "초기화해줘" 요청을 보낼 필요가 없다).
 *
 * 필요한 설정값(application.properties -> .env)은 README.md "로컬 실행" 섹션에 문서화돼 있다:
 *   CLAUDE_API_KEY (필수), CLAUDE_MODEL / CLAUDE_MAX_TOKENS (선택, 기본값 있음)
 *
 * 주의: 작성 시점(2026-08-25)엔 팀에 Claude API 키가 아직 없어서 이 값이 비어있다. isConfigured()가
 * false인 동안엔 sendMessage()가 바로 실패 결과를 돌려준다(채팅 UI에는 "챗봇이 아직 준비중이에요"
 * 안내가 뜬다) — PortOneClient와 동일한 패턴. 키를 발급받아 .env에 채우기만 하면 코드 수정 없이
 * 바로 동작한다. CLAUDE_MODEL 기본값(claude-3-5-sonnet-20241022)이 발급받은 키의 플랜에서 막혀
 * 있으면 .env의 CLAUDE_MODEL만 다른 모델명으로 바꿔주면 된다.
 */
@Component
public class ClaudeClient {

	private static final String API_BASE = "https://api.anthropic.com/v1/messages";
	private static final String ANTHROPIC_VERSION = "2023-06-01";

	private final HttpClient httpClient = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.build();
	private final ObjectMapper objectMapper = new ObjectMapper();

	private final String apiKey;
	private final String model;
	private final int maxTokens;

	public ClaudeClient(
			@Value("${claude.api-key:}") String apiKey,
			@Value("${claude.model:claude-3-5-sonnet-20241022}") String model,
			@Value("${claude.max-tokens:1024}") int maxTokens) {
		this.apiKey = apiKey;
		this.model = model;
		this.maxTokens = maxTokens;
	}

	/** .env에 CLAUDE_API_KEY가 채워져 있는지. */
	public boolean isConfigured() {
		return apiKey != null && !apiKey.isBlank();
	}

	/**
	 * 시스템 프롬프트 + 지금까지의 대화 내역(history) + 이번에 새로 보낸 메시지를 Claude API에 보내고
	 * 응답 텍스트를 받아온다. history는 프론트가 매 요청마다 통째로 다시 보내주는 값이라(서버 세션/DB
	 * 저장 없음), 여기서는 그대로 messages 배열 맨 뒤에 이번 메시지만 덧붙여 보낸다.
	 */
	public ChatResult sendMessage(String systemPrompt, List<ChatMessageDto> history, String userMessage) {
		if (!isConfigured()) {
			return ChatResult.failed("챗봇이 아직 준비중이에요. 잠시 후 다시 시도해주세요.");
		}

		try {
			ArrayNode messages = objectMapper.createArrayNode();
			if (history != null) {
				for (ChatMessageDto turn : history) {
					if (turn == null || turn.getRole() == null || turn.getContent() == null) {
						continue;
					}
					ObjectNode node = objectMapper.createObjectNode();
					node.put("role", turn.getRole());
					node.put("content", turn.getContent());
					messages.add(node);
				}
			}
			ObjectNode userNode = objectMapper.createObjectNode();
			userNode.put("role", "user");
			userNode.put("content", userMessage);
			messages.add(userNode);

			ObjectNode body = objectMapper.createObjectNode();
			body.put("model", model);
			body.put("max_tokens", maxTokens);
			body.put("system", systemPrompt);
			body.set("messages", messages);

			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(API_BASE))
					.timeout(Duration.ofSeconds(20))
					.header("x-api-key", apiKey)
					.header("anthropic-version", ANTHROPIC_VERSION)
					.header("content-type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(
							objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
					.build();

			HttpResponse<String> response =
					httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

			if (response.statusCode() / 100 != 2) {
				return ChatResult.failed(
						"답변을 가져오지 못했어요 (status=" + response.statusCode() + "). 다시 시도해주세요.");
			}

			JsonNode responseBody = objectMapper.readTree(response.body());
			JsonNode contentArray = responseBody.path("content");
			StringBuilder text = new StringBuilder();
			if (contentArray.isArray()) {
				for (JsonNode block : contentArray) {
					if ("text".equals(block.path("type").asText())) {
						text.append(block.path("text").asText(""));
					}
				}
			}

			if (text.isEmpty()) {
				return ChatResult.failed("답변을 가져오지 못했어요. 다시 시도해주세요.");
			}

			return ChatResult.success(text.toString());
		} catch (IOException | InterruptedException e) {
			if (e instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			return ChatResult.failed("답변을 가져오지 못했어요. 다시 시도해주세요.");
		}
	}

	/** success=true여야 reply가 채워져 있다. false면 failReason에 사람이 읽을 수 있는 실패 사유가 담긴다. */
	public record ChatResult(boolean success, String reply, String failReason) {
		public static ChatResult success(String reply) {
			return new ChatResult(true, reply, null);
		}

		public static ChatResult failed(String reason) {
			return new ChatResult(false, null, reason);
		}
	}
}

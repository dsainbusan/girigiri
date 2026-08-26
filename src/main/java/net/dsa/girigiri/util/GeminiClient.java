package net.dsa.girigiri.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import net.dsa.girigiri.domain.dto.ChatMessageDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * 고객 지원 챗봇이 쓰는 Gemini API(Google Generative Language API) 연동 유틸.
 * 담당: 송채현 (WBS 6.5 고객 지원 챗봇)
 *
 * 변경됨 (2026-08-26) — 원래는 ClaudeClient.java로 Claude API(Anthropic)에 연동했었는데, Claude
 * API는 신규 계정에 자동으로 주는 무료 크레딧이 없어서(콘솔 확인 결과 $0.00, 카드 등록 + 최소
 * 결제가 있어야 키가 동작함) 개발/테스트 단계에서 비용 없이 쓸 수 있는 Gemini API로 바꿨다.
 * Gemini는 결제수단 등록 없이 API 키 발급 및 사용이 가능한 무료 티어를 제공한다.
 *
 * ⚠️ CLAUDE.md 기획서(WBS 6.5)에는 "Spring Boot 백엔드 (Claude API 연동)"이라고 적혀 있다 —
 * 비용 문제로 우선 Gemini로 구현했으니, 조장님(송보미)/팀에 공유하고 기획서를 업데이트하거나
 * 팀 방침에 맞게 다시 조정할 것.
 *
 * PortOneClient와 같은 이유로 별도 SDK 의존성을 추가하지 않고 자바 표준 HttpClient로 REST API를
 * 직접 호출한다. 대화 내역(history)은 서버가 세션/DB에 저장하지 않고, 매 요청마다 프론트(채팅
 * UI)가 지금까지 주고받은 메시지를 그대로 다시 보내주는 방식이다 — 요구사항정의서 REQ-F-125
 * "챗봇 대화 초기화"가 "새 대화 시작" 버튼을 누르면 프론트가 들고 있던 history를 비우기만 하면
 * 되는 것도 이 구조 덕분이다 (서버에 별도로 "초기화해줘" 요청을 보낼 필요가 없다).
 *
 * 필요한 설정값(application.properties -> .env)은 README.md "로컬 실행" 섹션에 문서화할 것:
 *   GEMINI_API_KEY (필수), GEMINI_MODEL / GEMINI_MAX_TOKENS (선택, 기본값 있음)
 *
 * 주의: isConfigured()가 false인 동안엔 sendMessage()가 바로 실패 결과를 돌려준다(채팅 UI에는
 * "챗봇이 아직 준비중이에요" 안내가 뜬다) — PortOneClient와 동일한 패턴. 키를 발급받아 .env에
 * 채우기만 하면 코드 수정 없이 바로 동작한다.
 *
 * Gemini API는 대화 role을 "user"/"model" 두 가지로만 구분한다 (Claude/OpenAI 쪽의 "assistant"와
 * 다른 이름) — 그래서 ChatMessageDto.role이 "assistant"로 들어오면 여기서 "model"로 바꿔 보낸다.
 * 시스템 프롬프트는 messages 배열에 안 섞고 Gemini의 별도 systemInstruction 필드로 보낸다.
 *
 * 모델명 기본값은 특정 버전(예: gemini-2.5-flash)을 고정하지 않고 gemini-flash-latest 별칭을
 * 쓴다 — 2026-08-26에 실제로 gemini-2.5-flash로 호출했다가 404(model not found)를 겪었다. 구글이
 * 모델을 계속 새 버전으로 교체/폐기하는데, 버전을 직접 박아두면 그 버전이 내려갈 때마다 다시
 * 깨지기 때문에, 항상 그 시점의 최신 안정 버전을 가리키는 별칭을 쓰는 게 더 안전하다.
 *
 * 디버그 로그 (2026-08-26 추가) — 화면엔 사람이 읽을 실패 사유만 짧게 보여주고, 구글이 실제로
 * 뭐라고 응답했는지(에러 본문/원인)는 서버 콘솔에 log.warn으로 남긴다. application.properties의
 * logging.level.net.dsa.girigiri=debug 설정 덕분에 warn 로그도 콘솔에 그대로 찍힌다 — 챗봇이
 * 실패할 때마다 이 로그를 보면 정확한 원인을 알 수 있다.
 */
@Slf4j
@Component
public class GeminiClient {

	private static final String API_URL_TEMPLATE =
			"https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";

	static {
		// 추가됨 (2026-08-26) — 왜: HttpTimeoutException으로 계속 실패하는 문제를 겪었는데, 혹시
		// 개발 환경(학교/기관 네트워크)에 프록시가 설정돼 있을 수도 있어서 시도해본 것. 자바는 이
		// 시스템 프로퍼티를 켜주지 않으면 윈도우의 프록시 설정을 무시하고 직접 나가려다 막힐 수
		// 있다. (프록시가 없는 일반 네트워크에서는 부작용 없이 그냥 직접 연결하니 남겨둔다.)
		System.setProperty("java.net.useSystemProxies", "true");
	}

	// 추가됨 (2026-08-26) — 왜: 프록시 설정을 켜도 여전히 HttpTimeoutException이 나서 다시 조사함.
	// curl로 같은 구글 API 주소에 GET 요청을 보내면 즉시 성공하는데, 이 자바 코드의 POST 요청만
	// 계속 응답 없이 멈추는 걸 보고 원인을 좁혔다 — 자바의 HttpClient는 기본적으로 HTTP/2 방식을
	// 먼저 시도하는데, 일부 네트워크의 방화벽/보안 프로그램이 HTTP/2를 제대로 처리하지 못해서
	// 응답도 에러도 없이 그냥 무한정 멈춰버리는 경우가 있다(반면 curl은 보통 HTTP/1.1을 쓴다).
	// version(HTTP_1_1)로 자바도 curl과 같은 방식을 쓰도록 강제해서 이 문제를 우회한다.
	private final HttpClient httpClient = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.proxy(ProxySelector.getDefault())
			.version(HttpClient.Version.HTTP_1_1)
			.build();
	private final ObjectMapper objectMapper = new ObjectMapper();

	private final String apiKey;
	private final String model;
	private final int maxOutputTokens;

	public GeminiClient(
			@Value("${gemini.api-key:}") String apiKey,
			@Value("${gemini.model:gemini-flash-latest}") String model,
			@Value("${gemini.max-tokens:1024}") int maxOutputTokens) {
		this.apiKey = apiKey;
		this.model = model;
		this.maxOutputTokens = maxOutputTokens;
	}

	/** .env에 GEMINI_API_KEY가 채워져 있는지. */
	public boolean isConfigured() {
		return apiKey != null && !apiKey.isBlank();
	}

	/**
	 * 시스템 프롬프트 + 지금까지의 대화 내역(history) + 이번에 새로 보낸 메시지를 Gemini API에 보내고
	 * 응답 텍스트를 받아온다. history는 프론트가 매 요청마다 통째로 다시 보내주는 값이라(서버 세션/DB
	 * 저장 없음), 여기서는 그대로 contents 배열 맨 뒤에 이번 메시지만 덧붙여 보낸다.
	 */
	public ChatResult sendMessage(String systemPrompt, List<ChatMessageDto> history, String userMessage) {
		if (!isConfigured()) {
			return ChatResult.failed("챗봇이 아직 준비중이에요. 잠시 후 다시 시도해주세요.");
		}

		try {
			ArrayNode contents = objectMapper.createArrayNode();
			if (history != null) {
				for (ChatMessageDto turn : history) {
					if (turn == null || turn.getRole() == null || turn.getContent() == null) {
						continue;
					}
					contents.add(toContentNode(
							"assistant".equals(turn.getRole()) ? "model" : "user", turn.getContent()));
				}
			}
			contents.add(toContentNode("user", userMessage));

			ObjectNode body = objectMapper.createObjectNode();
			body.set("contents", contents);

			ObjectNode systemInstruction = objectMapper.createObjectNode();
			ArrayNode systemParts = objectMapper.createArrayNode();
			ObjectNode systemPart = objectMapper.createObjectNode();
			systemPart.put("text", systemPrompt);
			systemParts.add(systemPart);
			systemInstruction.set("parts", systemParts);
			body.set("systemInstruction", systemInstruction);

			ObjectNode generationConfig = objectMapper.createObjectNode();
			generationConfig.put("maxOutputTokens", maxOutputTokens);
			// thinkingBudget=0 — 2.5/3.x flash 계열은 기본적으로 "생각(thinking)" 과정에도
			// maxOutputTokens를 같이 소모한다. 이 챗봇은 짧은 FAQ 답변만 하면 되고 복잡한 추론이
			// 필요 없는데, thinking을 끄지 않으면 그 "생각" 토큰이 maxOutputTokens를 다 써버려서
			// 정작 사용자에게 보여줄 답변 텍스트가 0글자로 나오는 문제가 있었다(응답은 200
			// 정상인데 내용이 비어서 실패 처리됨). thinkingBudget:0으로 꺼서 모든 토큰이 실제
			// 답변에만 쓰이게 한다.
			ObjectNode thinkingConfig = objectMapper.createObjectNode();
			thinkingConfig.put("thinkingBudget", 0);
			generationConfig.set("thinkingConfig", thinkingConfig);
			body.set("generationConfig", generationConfig);

			String url = String.format(API_URL_TEMPLATE, model, apiKey);

			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(url))
					.timeout(Duration.ofSeconds(20))
					.header("content-type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(
							objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
					.build();

			HttpResponse<String> response =
					httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

			if (response.statusCode() / 100 != 2) {
				log.warn("> [GeminiClient] 응답 실패 - status={}, body={}", response.statusCode(), response.body());
				return ChatResult.failed(
						"답변을 가져오지 못했어요 (status=" + response.statusCode() + "). 다시 시도해주세요.");
			}

			JsonNode responseBody = objectMapper.readTree(response.body());
			JsonNode candidates = responseBody.path("candidates");
			StringBuilder text = new StringBuilder();
			if (candidates.isArray() && !candidates.isEmpty()) {
				JsonNode parts = candidates.get(0).path("content").path("parts");
				if (parts.isArray()) {
					for (JsonNode part : parts) {
						text.append(part.path("text").asText(""));
					}
				}
			}

			if (text.isEmpty()) {
				log.warn("> [GeminiClient] 답변 텍스트가 비어있음 - 원본 응답={}", response.body());
				return ChatResult.failed("답변을 가져오지 못했어요. 다시 시도해주세요.");
			}

			return ChatResult.success(text.toString());
		} catch (IOException | InterruptedException e) {
			if (e instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			log.warn("> [GeminiClient] 요청 중 예외 발생", e);
			return ChatResult.failed("답변을 가져오지 못했어요. 다시 시도해주세요.");
		}
	}

	private ObjectNode toContentNode(String role, String text) {
		ObjectNode node = objectMapper.createObjectNode();
		node.put("role", role);
		ArrayNode parts = objectMapper.createArrayNode();
		ObjectNode part = objectMapper.createObjectNode();
		part.put("text", text);
		parts.add(part);
		node.set("parts", parts);
		return node;
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

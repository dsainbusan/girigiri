package net.dsa.girigiri.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

/**
 * 강노은: 가게 리뷰 AI 요약 전용 Gemini(Google Generative Language API) 연동 유틸.
 *
 * GeminiClient(송채현 담당, 고객 지원 챗봇 — WBS 6.5)와 하는 일이 겹치지만, 그 파일은 챗봇
 * 대화 흐름(role별 시스템 프롬프트, 대화 history, 예약조회 function calling)에 맞춰 짜여 있어서
 * "리뷰 뭉치 하나 던지고 요약 문장 하나 받기"에는 안 맞고, 다른 담당자 파일을 고치는 것도
 * 부담스러워서 완전히 별도 클래스로 뗐다 — GeminiClient.java는 한 줄도 안 건드린다.
 * gemini.api-key / gemini.model 설정값은 같은 .env 값을 그대로 재사용한다(설정만 공유, 코드는 별개).
 *
 * isConfigured()가 false(.env에 GEMINI_API_KEY 없음)이거나 API 호출이 실패하면 summarize()가
 * Optional.empty()를 돌려준다 — 호출부(ReviewService)는 이 경우 "요약 섹션 자체를 숨긴다"로
 * 처리하면 된다. 챗봇과 달리 부가 기능이라 실패해도 화면이 깨지면 안 된다.
 */
@Slf4j
@Component
public class ReviewSummaryClient {

	private static final String API_URL_TEMPLATE =
			"https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";

	// GeminiClient와 같은 이유(이 프로젝트에서 겪었던 윈도우 고장난 프록시 설정 우회)로
	// NO_PROXY + HTTP/1.1을 그대로 맞춰둔다 — GeminiClient.java 상단 주석 참고.
	private final HttpClient httpClient = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.proxy(HttpClient.Builder.NO_PROXY)
			.version(HttpClient.Version.HTTP_1_1)
			.build();
	private final ObjectMapper objectMapper = new ObjectMapper();

	private final String apiKey;
	private final String model;

	// 변경됨 (강노은, 2026-09-14, QA 발견) — 왜: 300으로 잡았을 때 실제 응답
	// (finishReason=MAX_TOKENS, usageMetadata.thoughtsTokenCount=287/300)을 까보니, 지금 모델이
	// "생각(thinking)" 토큰을 눈에 안 보이게 먼저 쓰고 남는 토큰으로 실제 답을 쓰는 방식이라
	// 300자 예산의 대부분(287)을 생각에 쓰고 진짜 요약 문장은 9토큰만에 잘려서
	// "빵이 촉촉하고 신선하며 마" 같은 조각 문장이 그대로 화면에 저장된 적이 있었다(직접 재현 확인).
	// thinkingConfig.thinkingBudget=0으로 생각 자체를 꺼보려 했지만 이 모델은 여전히 400
	// INVALID_ARGUMENT로 거절한다(2026-09-01 주석과 같은 결과, 재확인함) — 그래서 끄는 대신
	// "생각 + 실제 답변"이 둘 다 들어갈 만큼 예산을 넉넉히 늘리는 쪽으로 대응한다.
	// 800으로 한 번 올렸을 때도 실제 매장(리뷰 9개짜리 프롬프트)에서 한 번 더 MAX_TOKENS로
	// 잘린 걸 재현했다 — 생각 토큰 소비량이 요청마다 들쭉날쭉해서(같은 프롬프트인데도 0~287+로
	// 편차가 큼) 안전 마진이 더 필요하다고 보고 2048로 다시 올렸고, 같은 실제 프롬프트로 3회
	// 연속 finishReason=STOP·정상 한국어 요약을 확인했다. 그래도 100% 보장은 아니라서 아래
	// summarize()에 MAX_TOKENS 재시도까지 같이 넣어둔다.
	private static final int MAX_OUTPUT_TOKENS = 2048;
	private static final int MAX_ATTEMPTS = 2;
	private static final long RETRY_DELAY_MS = 1500;

	private static final String SYSTEM_PROMPT = """
			너는 동네 가게 리뷰를 요약하는 도우미야. 아래 [리뷰 목록]을 읽고, 손님들이 공통적으로
			언급하는 특징(맛/구성/양/친절함 등)을 2~3문장, 120자 이내 한국어 존댓말로 요약해.

			규칙:
			- 특정 손님의 이름이나 리뷰 원문을 그대로 인용하지 마.
			- 마크다운(별표, #, - 등) 쓰지 말고 순수 문장으로만 답해.
			- 부정적인 의견이 있으면 과장하지 말고 담백하게 같이 반영해.
			- 리뷰 내용이 너무 짧거나 의미 없으면 "아직 뚜렷한 특징을 요약하기 어려워요."라고만 답해.
			""";

	// 변경됨 (강노은, 2026-09-01) — 왜: gemini.model이 .env에 따로 없으면 GeminiClient와 같은 기본값
	// gemini-flash-latest를 썼었는데, 실제로 붙여서 테스트해보니(curl로 직접 확인) 그 모델이 지금
	// 503 "high demand"로 계속 실패했다(연속 7회). gemini-flash-lite-latest로 바꾸니 정상 응답을
	// 받아서 이 클라이언트만 기본값을 바꾼다 — GeminiClient.java(송채현 담당)는 안 건드림, .env에
	// GEMINI_MODEL을 명시적으로 지정하면 두 클라이언트 다 그 값을 따른다(프로퍼티 키는 동일).
	public ReviewSummaryClient(
			@Value("${gemini.api-key:}") String apiKey,
			@Value("${gemini.model:gemini-flash-lite-latest}") String model) {
		this.apiKey = apiKey;
		this.model = model;
	}

	/** .env에 GEMINI_API_KEY가 채워져 있는지. */
	public boolean isConfigured() {
		return apiKey != null && !apiKey.isBlank();
	}

	/** 리뷰 본문들을 한데 모은 텍스트를 넘기면 요약 문장을 돌려준다. 실패하면 empty. */
	public Optional<String> summarize(String reviewsText) {
		if (!isConfigured() || reviewsText == null || reviewsText.isBlank()) {
			return Optional.empty();
		}

		String requestJson;
		try {
			requestJson = objectMapper.writeValueAsString(buildRequestBody(reviewsText));
		} catch (IOException e) {
			log.warn("> [ReviewSummaryClient] 요청 바디 생성 실패", e);
			return Optional.empty();
		}

		String url = String.format(API_URL_TEMPLATE, model, apiKey);
		for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
			try {
				HttpRequest request = HttpRequest.newBuilder()
						.uri(URI.create(url))
						.timeout(Duration.ofSeconds(20))
						.header("content-type", "application/json")
						.POST(HttpRequest.BodyPublishers.ofString(requestJson, StandardCharsets.UTF_8))
						.build();

				HttpResponse<String> response =
						httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

				if ((response.statusCode() == 503 || response.statusCode() == 429) && attempt < MAX_ATTEMPTS) {
					log.warn("> [ReviewSummaryClient] 일시적 실패(재시도) - attempt={}/{}, status={}",
							attempt, MAX_ATTEMPTS, response.statusCode());
					sleepBeforeRetry();
					continue;
				}
				if (response.statusCode() / 100 != 2) {
					log.warn("> [ReviewSummaryClient] 응답 실패 - status={}, body={}",
							response.statusCode(), response.body());
					return Optional.empty();
				}

				JsonNode candidate = objectMapper.readTree(response.body()).path("candidates").path(0);
				// 추가됨 (강노은, 2026-09-14, QA 발견) — 위 MAX_OUTPUT_TOKENS 주석 참고. 예산을
				// 넉넉히 늘려도 생각 토큰 소비가 들쭉날쭉해서 드물게 또 잘릴 수 있다 — 503/429와
				// 같은 재시도 경로를 태워서 한 번 더 시도하고, 마지막 시도까지 잘리면 그때
				// 조각난 문장을 그대로 쓰지 않고 실패로 취급한다(호출부 ReviewService가 예전 캐시
				// 폴백/요약 섹션 숨김으로 처리).
				if ("MAX_TOKENS".equals(candidate.path("finishReason").asText())) {
					if (attempt < MAX_ATTEMPTS) {
						log.warn("> [ReviewSummaryClient] 응답이 토큰 예산 안에 못 끝나서 잘림(재시도) - attempt={}/{}",
								attempt, MAX_ATTEMPTS);
						sleepBeforeRetry();
						continue;
					}
					log.warn("> [ReviewSummaryClient] 응답이 토큰 예산 안에 못 끝나서 잘림(finishReason=MAX_TOKENS) - 재시도 소진, 실패로 처리");
					return Optional.empty();
				}

				String text = candidate
						.path("content").path("parts").path(0)
						.path("text").asText("");
				return text.isBlank() ? Optional.empty() : Optional.of(text.trim());
			} catch (IOException | InterruptedException e) {
				if (e instanceof InterruptedException) {
					Thread.currentThread().interrupt();
					return Optional.empty();
				}
				log.warn("> [ReviewSummaryClient] 요청 중 예외 (attempt={}/{})", attempt, MAX_ATTEMPTS, e);
				if (attempt < MAX_ATTEMPTS) {
					sleepBeforeRetry();
				}
			}
		}
		return Optional.empty();
	}

	private ObjectNode buildRequestBody(String reviewsText) {
		ObjectNode body = objectMapper.createObjectNode();

		ArrayNode contents = objectMapper.createArrayNode();
		ObjectNode userTurn = objectMapper.createObjectNode();
		userTurn.put("role", "user");
		ArrayNode parts = objectMapper.createArrayNode();
		ObjectNode part = objectMapper.createObjectNode();
		part.put("text", "[리뷰 목록]\n" + reviewsText);
		parts.add(part);
		userTurn.set("parts", parts);
		contents.add(userTurn);
		body.set("contents", contents);

		ObjectNode systemInstruction = objectMapper.createObjectNode();
		ArrayNode systemParts = objectMapper.createArrayNode();
		ObjectNode systemPart = objectMapper.createObjectNode();
		systemPart.put("text", SYSTEM_PROMPT);
		systemParts.add(systemPart);
		systemInstruction.set("parts", systemParts);
		body.set("systemInstruction", systemInstruction);

		// 변경됨 (강노은, 2026-09-01) — 왜: GeminiClient를 참고해서 원래 thinkingConfig(thinkingBudget=0)를
		// 넣었었는데, 실제로 curl로 gemini-flash-lite-latest에 던져보니 이 필드가 있으면 400
		// INVALID_ARGUMENT로 거절당했다(이 필드만 뺐더니 바로 정상 응답 받음 — 직접 재현 확인).
		// lite 모델이라 애초에 thinking 토큰을 안 쓰는 것으로 보여서(응답에 thoughtsTokenCount
		// 없음) 이 필드 자체가 필요 없다 — 넣지 않는다.
		ObjectNode generationConfig = objectMapper.createObjectNode();
		generationConfig.put("maxOutputTokens", MAX_OUTPUT_TOKENS);
		body.set("generationConfig", generationConfig);

		return body;
	}

	private void sleepBeforeRetry() {
		try {
			Thread.sleep(RETRY_DELAY_MS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}

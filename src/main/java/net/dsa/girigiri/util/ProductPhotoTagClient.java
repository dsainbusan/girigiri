package net.dsa.girigiri.util;

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
import java.util.Base64;
import java.util.Optional;

/**
 * 강노은: 상품 사진 자동 태깅 전용 Gemini(Google Generative Language API) 연동 유틸 — 이미지 1장을
 * 보내서 "카테고리/품목명 추측" 원문 텍스트를 받아오기만 한다. 응답 텍스트를 실제로 파싱해서
 * PhotoTagSuggestionDto로 바꾸는 건 비즈니스 로직이라 여기가 아니라 ProductPhotoTagService가 한다
 * (ReviewSummaryClient/ReviewService와 같은 분리 원칙).
 *
 * GeminiClient(송채현, 챗봇)·ReviewSummaryClient(강노은, 리뷰 요약)와 하는 일이 겹치지만, 이번엔
 * 텍스트 하나가 아니라 이미지(inlineData)까지 실어야 해서 요청 바디 구조가 또 다르다 — 마찬가지로
 * 완전히 별도 클래스로 뗀다(다른 담당자 파일은 물론 ReviewSummaryClient.java도 한 줄도 안 건드림).
 * gemini.api-key / gemini.model 설정값은 같은 .env 값을 그대로 재사용한다(설정만 공유, 코드는 별개).
 *
 * isConfigured()가 false(.env에 GEMINI_API_KEY 없음)이거나 API 호출이 실패하면 suggest()가
 * Optional.empty()를 돌려준다 — 부가 기능이라 실패해도 상품 등록 폼 자체는 그대로 제출 가능해야 한다.
 */
@Slf4j
@Component
public class ProductPhotoTagClient {

	private static final String API_URL_TEMPLATE =
			"https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";

	// ReviewSummaryClient/GeminiClient와 같은 이유(이 프로젝트에서 겪었던 윈도우 고장난 프록시 설정
	// 우회)로 NO_PROXY + HTTP/1.1을 그대로 맞춰둔다.
	private final HttpClient httpClient = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.proxy(HttpClient.Builder.NO_PROXY)
			.version(HttpClient.Version.HTTP_1_1)
			.build();
	private final ObjectMapper objectMapper = new ObjectMapper();

	private final String apiKey;
	private final String model;

	// 두 줄("카테고리: .../품목명: ...")만 받으면 되니 리뷰 요약(300)보다도 더 작게 잡는다.
	private static final int MAX_OUTPUT_TOKENS = 60;
	private static final int MAX_ATTEMPTS = 2;
	private static final long RETRY_DELAY_MS = 1500;

	private static final String SYSTEM_PROMPT = """
			너는 동네 마감세일 앱에서 점주가 올린 상품 사진을 보고 카테고리와 품목명을 추측해주는 도우미야.
			사진 속 음식/상품을 보고 아래 형식 그대로 딱 두 줄만 답해 (다른 말 붙이지 마, 마크다운 금지):
			카테고리: (베이커리 / 반찬 / 도시락 / 카페 중 하나만. 애매하면 기타)
			품목명: (한국어로 짧게, 예: 크루아상 / 계란장조림 / 참치김밥. 음식이 아니거나 전혀 모르겠으면 빈칸으로 둬)
			""";

	// ReviewSummaryClient와 같은 이유(gemini-flash-latest가 503으로 계속 실패해서 lite로 바꿔 확인함) —
	// .env에 GEMINI_MODEL을 명시하면 그 값을 그대로 따른다.
	public ProductPhotoTagClient(
			@Value("${gemini.api-key:}") String apiKey,
			@Value("${gemini.model:gemini-flash-lite-latest}") String model) {
		this.apiKey = apiKey;
		this.model = model;
	}

	/** .env에 GEMINI_API_KEY가 채워져 있는지. */
	public boolean isConfigured() {
		return apiKey != null && !apiKey.isBlank();
	}

	/** imageBytes를 보고 "카테고리: .../품목명: ..." 형식의 원문 응답을 돌려준다. 실패하면 empty. */
	public Optional<String> suggest(byte[] imageBytes, String mimeType) {
		if (!isConfigured() || imageBytes == null || imageBytes.length == 0) {
			return Optional.empty();
		}

		String requestJson;
		try {
			requestJson = objectMapper.writeValueAsString(buildRequestBody(imageBytes, mimeType));
		} catch (IOException e) {
			log.warn("> [ProductPhotoTagClient] 요청 바디 생성 실패", e);
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
					log.warn("> [ProductPhotoTagClient] 일시적 실패(재시도) - attempt={}/{}, status={}",
							attempt, MAX_ATTEMPTS, response.statusCode());
					sleepBeforeRetry();
					continue;
				}
				if (response.statusCode() / 100 != 2) {
					log.warn("> [ProductPhotoTagClient] 응답 실패 - status={}, body={}",
							response.statusCode(), response.body());
					return Optional.empty();
				}

				String text = objectMapper.readTree(response.body())
						.path("candidates").path(0)
						.path("content").path("parts").path(0)
						.path("text").asText("");
				return text.isBlank() ? Optional.empty() : Optional.of(text.trim());
			} catch (IOException | InterruptedException e) {
				if (e instanceof InterruptedException) {
					Thread.currentThread().interrupt();
					return Optional.empty();
				}
				log.warn("> [ProductPhotoTagClient] 요청 중 예외 (attempt={}/{})", attempt, MAX_ATTEMPTS, e);
				if (attempt < MAX_ATTEMPTS) {
					sleepBeforeRetry();
				}
			}
		}
		return Optional.empty();
	}

	private ObjectNode buildRequestBody(byte[] imageBytes, String mimeType) {
		ObjectNode body = objectMapper.createObjectNode();

		ArrayNode contents = objectMapper.createArrayNode();
		ObjectNode userTurn = objectMapper.createObjectNode();
		userTurn.put("role", "user");

		ArrayNode parts = objectMapper.createArrayNode();

		ObjectNode imagePart = objectMapper.createObjectNode();
		ObjectNode inlineData = objectMapper.createObjectNode();
		inlineData.put("mimeType", mimeType);
		inlineData.put("data", Base64.getEncoder().encodeToString(imageBytes));
		imagePart.set("inlineData", inlineData);
		parts.add(imagePart);

		ObjectNode textPart = objectMapper.createObjectNode();
		textPart.put("text", "이 사진 속 상품의 카테고리와 품목명을 추측해줘.");
		parts.add(textPart);

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

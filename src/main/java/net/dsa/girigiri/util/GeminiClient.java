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
 *   GEMINI_API_KEY_MEMBER (필수, 회원용) / GEMINI_API_KEY_GUEST (필수, 비회원용) / GEMINI_MODEL /
 *   GEMINI_MAX_TOKENS (선택, 기본값 있음)
 *
 * 주의: isConfigured(KeyProfile)가 false인 동안엔 sendMessage()가 바로 실패 결과를 돌려준다(채팅
 * UI에는 "챗봇이 아직 준비중이에요" 안내가 뜬다) — PortOneClient와 동일한 패턴. 키를 발급받아
 * .env에 채우기만 하면 코드 수정 없이 바로 동작한다.
 *
 * 변경됨 (2026-09-29, 담당: 송채현) — 왜: 무료 티어 하루/분당 할당량이 회원 챗봇(마이페이지)과
 * 비회원 챗봇(마케팅 홈/FAQ)이 같은 키를 나눠 쓰면 한쪽이 몰릴 때 다른 쪽까지 같이 막혔다.
 * "용도별 키 분리"로 바꿔서, 회원용/비회원용 키를 아예 따로 설정하고(KeyProfile.MEMBER/GUEST)
 * 회원 쪽만 429(할당량 초과) 시 비회원 키로 1회 대체를 허용한다(반대는 안 함 — 비회원 트래픽이
 * 회원용 할당량까지 쓰게 두지 않기 위함). 예전 GEMINI_API_KEY_2(범용 예비 키) 개념은 이 구조로
 * 대체되어 제거했다. 기존 GEMINI_API_KEY 하나만 채워둔 팀원 .env는 안 깨지도록 그 값이 회원용
 * 키의 기본값으로 그대로 읽힌다(application.properties의 gemini.api-key-member 참고).
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

	// 추가됨 (2026-08-26) — 왜: 계속되는 HttpTimeoutException을 추적하다가, 이 컴퓨터의 윈도우
	// 프록시 설정이 "프록시 서버 사용"은 켜져 있는데 정작 프록시 주소/포트는 비어있는 고장난
	// 상태라는 걸 발견했다("자동으로 설정 검색"도 켜져 있었음). curl은 이런 시스템 프록시 설정을
	// 안 따라가서 항상 직접 연결에 성공했는데, 자바는 (예전엔 useSystemProxies=true로 이 설정을
	// 따라가게 해뒀었다) 이 고장난 프록시 설정 때문에 어디로 연결해야 할지 못 정하고 응답 없이
	// 멈춰있었던 것으로 보인다. proxy(HttpClient.Builder.NO_PROXY)로 윈도우 프록시 설정을 아예
	// 무시하고 curl처럼 항상 직접 연결하도록 강제한다.
	// (HTTP/2 대신 HTTP/1.1을 강제하는 것도 앞서 시도했던 처방인데, 프록시 원인이 확인된 지금도
	// 안전하게 같이 유지한다 — curl과 최대한 동일한 조건으로 맞춰두는 것도 나쁘지 않다.)
	private final HttpClient httpClient = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.proxy(HttpClient.Builder.NO_PROXY)
			.version(HttpClient.Version.HTTP_1_1)
			.build();
	private final ObjectMapper objectMapper = new ObjectMapper();

	/**
	 * 2026-09-29 추가, 담당: 송채현 — 회원 챗봇(마이페이지)과 비회원 챗봇(마케팅 홈/FAQ) 중 어느
	 * 쪽이 이 호출의 주체인지. 키 선택·대체 로직(callWithKeyFallback)이 이 값 하나로만 갈라지게
	 * 모아뒀다 — 호출하는 쪽(ChatService)은 "내가 회원용인지 비회원용인지"만 알려주면 된다.
	 */
	public enum KeyProfile { MEMBER, GUEST }

	// 변경됨 (2026-09-29, 담당: 송채현) — 왜: 하나의 키를 회원/비회원이 나눠 쓰면 한쪽이 몰릴 때
	// 다른 쪽까지 같이 막혔다. 용도별로 키를 완전히 분리했다 — memberApiKey는 마이페이지 챗봇,
	// guestApiKey는 마케팅 홈/FAQ 챗봇 전용. member가 429일 때만 guest 키로 1회 대체를 시도하고
	// (callWithKeyFallback), 반대(guest가 member 키를 빌려쓰는 것)는 하지 않는다 — 비회원 트래픽이
	// 회원용 할당량까지 갉아먹지 않게 하기 위함.
	private final String memberApiKey;
	private final String guestApiKey;
	private final String model;
	private final int maxOutputTokens;

	public GeminiClient(
			@Value("${gemini.api-key-member:}") String memberApiKey,
			@Value("${gemini.api-key-guest:}") String guestApiKey,
			@Value("${gemini.model:gemini-flash-latest}") String model,
			@Value("${gemini.max-tokens:1024}") int maxOutputTokens) {
		this.memberApiKey = memberApiKey;
		this.guestApiKey = guestApiKey;
		this.model = model;
		this.maxOutputTokens = maxOutputTokens;
	}

	/**
	 * 해당 프로필로 챗봇을 쓸 수 있는지. MEMBER는 memberApiKey만 본다. GUEST는 guestApiKey가
	 * 없어도 memberApiKey가 있으면 true다 — 2026-09-29 추가: GEMINI_API_KEY_GUEST를 아직 안
	 * 채워둔 환경(팀원 개인 키가 없는 경우 등)에서 비회원 챗봇을 "준비중"으로 막아두지 않고
	 * member 키로 대신 돌아가게 하기 위함(callWithKeyFallback의 같은 분기 참고).
	 */
	public boolean isConfigured(KeyProfile keyProfile) {
		if (keyProfile == KeyProfile.GUEST) {
			return hasKey(guestApiKey) || hasKey(memberApiKey);
		}
		return hasKey(memberApiKey);
	}

	private static boolean hasKey(String key) {
		return key != null && !key.isBlank();
	}

	// 추가됨 (2026-08-31, 챗봇 기능 연동/function calling) — 왜: 예약 취소 가능 여부를 물어보면
	// 그동안은 시스템 프롬프트에 적힌 "30분 이내" 같은 일반 안내만 반복했다. 실제 그 사람의 그
	// 예약이 지금 취소 가능한지는 DB를 봐야 알 수 있어서, Gemini의 function calling으로 실제
	// 데이터를 조회해서 답하도록 확장한다. 이 tool은 조회만 하고 실제 취소는 처리하지 않는다
	// (실제 취소는 항상 마이페이지 버튼으로만 — 챗봇이 직접 시스템을 조작하지 않는다는 기존
	// 원칙 그대로 유지).
	private static final String RESERVATION_TOOL_NAME = "getMyActiveReservations";

	/**
	 * 예약 조회 tool의 실제 실행부. ChatService가 로그인 세션의 진짜 userId로 구현을 넘겨준다 —
	 * Gemini가 함수 호출에 userId를 실어 보내더라도 여기서는 절대 그 값을 쓰지 않는다(본인 예약만
	 * 조회되도록 보장하기 위해 항상 서버가 이미 알고 있는 세션 userId만 사용).
	 */
	public interface ReservationToolExecutor {
		/** 지금 시점 기준, 이 사용자의 진행중인 예약과 취소 가능 여부를 JSON 문자열로 돌려준다. */
		String getMyActiveReservationsJson();
	}

	// 재시도 관련 상수 (2026-08-26 추가) — 왜: HttpTimeoutException의 진짜 원인을 끝까지 추적해보니
	// 이 컴퓨터/네트워크 문제가 아니라, 구글 Gemini 무료 모델 서버가 사용자가 몰릴 때 일시적으로
	// "503 UNAVAILABLE - This model is currently experiencing high demand" 로 거절하거나, 아예
	// 응답을 안 주다가 우리 쪽 20초 타임아웃으로 이어지는 경우였다(원인은 하나인데 증상이 둘로
	// 나타난 것). 구글 에러 메시지 자체가 "일시적이니 나중에 다시 시도하라"고 안내하므로, 실패하면
	// 잠깐 기다렸다가 자동으로 한두 번 더 시도하는 재시도 로직을 추가한다.
	private static final int MAX_ATTEMPTS = 3;
	private static final long RETRY_DELAY_MS = 1500;

	/**
	 * 시스템 프롬프트 + 지금까지의 대화 내역(history) + 이번에 새로 보낸 메시지를 Gemini API에 보내고
	 * 응답 텍스트를 받아온다. history는 프론트가 매 요청마다 통째로 다시 보내주는 값이라(서버 세션/DB
	 * 저장 없음), 여기서는 그대로 contents 배열 맨 뒤에 이번 메시지만 덧붙여 보낸다.
	 *
	 * 일시적인 실패(네트워크 예외, 503 과부하, 429 요청 과다)는 내부에서 최대 MAX_ATTEMPTS번까지
	 * 자동 재시도한다 — 그 외 실패(잘못된 키, 잘못된 모델명 등)는 재시도해도 어차피 똑같이 실패하니
	 * 바로 실패를 돌려준다.
	 */
	public ChatResult sendMessage(KeyProfile keyProfile, String systemPrompt, List<ChatMessageDto> history,
			String userMessage) {
		return sendMessage(keyProfile, systemPrompt, history, userMessage, null);
	}

	/**
	 * 예약 조회 tool(RESERVATION_TOOL_NAME)을 같이 쓸 수 있는 버전. toolExecutor가 null이면 tool을
	 * 아예 요청에 안 실어서 기존과 100% 동일하게 동작한다(사장님 채팅 등 이 tool이 필요 없는
	 * 흐름은 영향이 없다). null이 아니면 Gemini에게 이 함수의 존재를 알려주고, 모델이 필요하다고
	 * 판단해서 함수 호출을 요청하면 여기서 toolExecutor를 직접 실행해 실제 예약 데이터를 가져온
	 * 뒤, 그 결과를 다시 Gemini에 보내 최종 답변 텍스트를 받아온다(최대 2번 왕복).
	 *
	 * 2026-09-29 — keyProfile 파라미터 추가(용도별 키 분리). 어느 키를 쓸지, 429일 때 대체를
	 * 허용할지는 전부 callWithKeyFallback 한 군데에서 keyProfile만 보고 결정한다.
	 */
	public ChatResult sendMessage(KeyProfile keyProfile, String systemPrompt, List<ChatMessageDto> history,
			String userMessage, ReservationToolExecutor toolExecutor) {
		if (!isConfigured(keyProfile)) {
			return ChatResult.failed("챗봇이 아직 준비중이에요. 잠시 후 다시 시도해주세요.");
		}

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

		boolean useTools = toolExecutor != null;
		GeminiCallResult result = callWithKeyFallback(keyProfile, buildRequestBody(systemPrompt, contents, useTools));

		if (!result.success()) {
			return ChatResult.failed(result.failReason(), result.quotaExceeded());
		}

		if (result.functionCallPart() != null) {
			return handleFunctionCall(keyProfile, systemPrompt, contents, useTools, result.functionCallPart(),
					toolExecutor);
		}

		if (result.text() == null || result.text().isEmpty()) {
			return ChatResult.failed("답변을 가져오지 못했어요. 다시 시도해주세요.");
		}
		return ChatResult.success(result.text());
	}

	/**
	 * 모델이 함수 호출을 요청했을 때: 실제로 그 함수를 실행하고, "모델이 이 함수를 호출했다" +
	 * "그 결과는 이거다"를 대화 내역(contents)에 이어붙여서 Gemini에 다시 보낸다. Gemini API는
	 * 이 과정을 하나의 대화 맥락 안에서 처리하도록 설계돼 있어서(펑션콜 자체도 "모델의 한 턴"으로
	 * 취급), 최종 응답도 systemInstruction/tools를 그대로 유지한 채 같은 대화의 연장으로 요청한다.
	 */
	private ChatResult handleFunctionCall(KeyProfile keyProfile, String systemPrompt, ArrayNode contents,
			boolean useTools, JsonNode functionCallPart, ReservationToolExecutor toolExecutor) {
		String functionName = functionCallPart.path("functionCall").path("name").asText("");

		String toolResultJson = RESERVATION_TOOL_NAME.equals(functionName) && toolExecutor != null
				? toolExecutor.getMyActiveReservationsJson()
				: "{\"error\":\"알 수 없는 함수예요.\"}";

		ObjectNode modelTurn = objectMapper.createObjectNode();
		modelTurn.put("role", "model");
		ArrayNode modelParts = objectMapper.createArrayNode();
		modelParts.add(functionCallPart);
		modelTurn.set("parts", modelParts);
		contents.add(modelTurn);

		ObjectNode functionResponseTurn = objectMapper.createObjectNode();
		functionResponseTurn.put("role", "user");
		ArrayNode frParts = objectMapper.createArrayNode();
		ObjectNode frPart = objectMapper.createObjectNode();
		ObjectNode functionResponse = objectMapper.createObjectNode();
		functionResponse.put("name", functionName);
		ObjectNode responseWrapper = objectMapper.createObjectNode();
		try {
			responseWrapper.set("result", objectMapper.readTree(toolResultJson));
		} catch (IOException e) {
			responseWrapper.put("result", toolResultJson);
		}
		functionResponse.set("response", responseWrapper);
		frPart.set("functionResponse", functionResponse);
		frParts.add(frPart);
		functionResponseTurn.set("parts", frParts);
		contents.add(functionResponseTurn);

		GeminiCallResult second = callWithKeyFallback(keyProfile, buildRequestBody(systemPrompt, contents, useTools));
		if (!second.success()) {
			return ChatResult.failed(second.failReason(), second.quotaExceeded());
		}
		// 이론상 모델이 함수 결과를 받고 또 함수 호출을 요청할 수도 있는 스펙이지만, 지금 tool은
		// 1개뿐이고 그마저도 "결과를 보고 다시 조회할" 이유가 없는 단순 조회라 여기서는 두 번째
		// 응답이 곧바로 최종 텍스트라고 가정한다(두 번째도 functionCall이면 실패로 처리).
		if (second.functionCallPart() != null || second.text() == null || second.text().isEmpty()) {
			log.warn("> [GeminiClient] 함수 호출 후 두 번째 응답이 텍스트가 아님 - functionCall={}",
					second.functionCallPart());
			return ChatResult.failed("답변을 가져오지 못했어요. 다시 시도해주세요.");
		}
		return ChatResult.success(second.text());
	}

	private ObjectNode buildRequestBody(String systemPrompt, ArrayNode contents, boolean includeTools) {
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

		if (includeTools) {
			body.set("tools", buildReservationTool());
		}

		return body;
	}

	/** 예약 조회 tool 하나짜리 Gemini function-declarations 스펙. */
	private ArrayNode buildReservationTool() {
		ObjectNode function = objectMapper.createObjectNode();
		function.put("name", RESERVATION_TOOL_NAME);
		function.put("description",
				"로그인한 손님 본인의 진행중인 예약과, 각 예약을 지금 취소할 수 있는 상태인지를 실제 " +
				"데이터로 조회한다. 예약 취소 가능 여부나 취소까지 남은 시간을 사용자가 물어보면 " +
				"추측하지 말고 반드시 이 함수를 먼저 호출해서 확인한 뒤 답하라. 이 함수는 조회만 " +
				"하며 실제로 예약을 취소하지는 않는다 — 결과가 취소 가능이면 마이페이지에서 취소하는 " +
				"방법을 안내하고, 취소 불가능이면 그 사유(reasonIfNotEligible)를 그대로 풀어서 " +
				"설명하라. reservations가 빈 배열이면 진행중인 예약이 없다는 뜻이다.");
		ObjectNode parameters = objectMapper.createObjectNode();
		parameters.put("type", "OBJECT");
		parameters.set("properties", objectMapper.createObjectNode());
		function.set("parameters", parameters);

		ArrayNode functionDeclarations = objectMapper.createArrayNode();
		functionDeclarations.add(function);

		ObjectNode toolEntry = objectMapper.createObjectNode();
		toolEntry.set("functionDeclarations", functionDeclarations);

		ArrayNode tools = objectMapper.createArrayNode();
		tools.add(toolEntry);
		return tools;
	}

	/**
	 * 2026-09-29 추가, 담당: 송채현 — "용도별 키 분리" 구조의 핵심. 두 가지 대체 상황을 구분해서
	 * 처리한다:
	 * ① MEMBER가 429(할당량 초과)면 딱 1회만 guest 키로 대체해본다 — 429는 잠깐 기다린다고
	 *    풀리는 게 아니라 재시도가 무의미하고(아래 callWithRetry의 429 주석 참고), 다른 키(다른
	 *    할당량)로 바꾸는 것만 효과가 있다. 429가 아닌 다른 실패(네트워크 오류, 잘못된 모델명 등)는
	 *    키를 바꿔도 똑같이 실패할 가능성이 높아 대체하지 않는다.
	 * ② GUEST는 원래 대체가 없어야 하지만(스펙: "비회원 채팅은 guest 키만 사용, 대체 없음" —
	 *    회원용 할당량을 비회원 트래픽이 갉아먹지 않게 하려는 의도), GEMINI_API_KEY_GUEST 자체가
	 *    아예 설정 안 된 환경(예: 개인 키가 아직 없는 팀원)에서는 예외적으로 member 키를 빌려
	 *    쓴다 — 이건 "할당량을 나눠 쓰는" 게 아니라 "guest 전용 키가 없을 때의 임시 대체"라
	 *    ①과는 성격이 달라서 경고 로그도 따로 남긴다.
	 */
	private GeminiCallResult callWithKeyFallback(KeyProfile keyProfile, ObjectNode body) {
		if (keyProfile == KeyProfile.GUEST) {
			if (!hasKey(guestApiKey)) {
				log.warn("> [GeminiClient][guest] 전용 키 미설정 - member 키로 대체");
				return callWithRetry(body, memberApiKey, "guest(대체:member 키, 전용 키 미설정)");
			}
			return callWithRetry(body, guestApiKey, "guest");
		}

		GeminiCallResult result = callWithRetry(body, memberApiKey, "member");
		if (result.quotaExceeded() && hasKey(guestApiKey)) {
			log.warn("> [GeminiClient][member] 회원용 키 할당량 초과 - 비회원용 키로 1회만 대체 시도");
			result = callWithRetry(body, guestApiKey, "member(대체:guest 키)");
		}
		return result;
	}

	/** sendMessage()의 재시도 루프를 그대로 옮긴 것 — 함수 호출 왕복까지 지원하려고 재사용 가능하게 분리했다.
	 * logTag(2026-09-29 추가)는 로그에만 쓰인다 — "지금 할당량을 쓰고 있는 게 회원 기능인지 비회원
	 * 기능인지"를 남기기 위함([member]/[guest]/[member(대체:guest 키)] 형태). */
	private GeminiCallResult callWithRetry(ObjectNode body, String apiKeyToUse, String logTag) {
		String url = String.format(API_URL_TEMPLATE, model, apiKeyToUse);
		String requestJson;
		try {
			requestJson = objectMapper.writeValueAsString(body);
		} catch (IOException e) {
			log.warn("> [GeminiClient][{}] 요청 바디 생성 실패", logTag, e);
			return GeminiCallResult.failed("답변을 가져오지 못했어요. 다시 시도해주세요.");
		}

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

				if (response.statusCode() == 503) {
					log.warn("> [GeminiClient][{}] 일시적 실패(재시도 대상) - attempt={}/{}, status={}, body={}",
							logTag, attempt, MAX_ATTEMPTS, response.statusCode(), response.body());
					if (attempt < MAX_ATTEMPTS) {
						sleepBeforeRetry(attempt);
						continue;
					}
					return GeminiCallResult.failed(
							"지금 챗봇 사용자가 많아서 답변이 지연되고 있어요. 잠시 후 다시 시도해주세요.");
				}

				// 추가됨 (2026-09-01) — 왜: 429는 503과 원인이 다르다. 503은 구글 서버가 일시적으로
				// 과부하인 경우라 잠깐 기다리면 풀리지만, 429는 "이 API 키의 무료 할당량을 이미 다
				// 썼다"는 뜻이라 1.5~4.5초 기다린다고 절대 안 풀린다. 실제로 gemini-flash-latest가
				// 가리키는 모델이 무료 할당량 20건짜리(gemini-3.7-flash)로 바뀌면서, 이 둘을 같이
				// 취급해 429에도 3번씩 재시도하는 바람에 얼마 안 되는 할당량을 스스로 3배 빠르게
				// 써버리고 있었다(재현 확인: 메시지 몇 개만 연달아 보내도 바로 429 반복). 그래서
				// 429는 재시도 없이 바로 실패 처리해서 할당량을 더 이상 낭비하지 않는다.
				if (response.statusCode() == 429) {
					log.warn("> [GeminiClient][{}] 할당량 초과(재시도 안 함) - status=429, body={}", logTag, response.body());
					// 변경됨 (2026-09-29) — failed() 대신 quotaExceeded()로 바꿔서 callWithKeyFallback이
					// "이 실패는 다른 키로 넘어가볼 만하다"를 구분할 수 있게 했다(그 외 실패는 키를
					// 바꿔도 똑같이 실패할 가능성이 높아 바로 반환).
					return GeminiCallResult.quotaExceeded(
							"지금 챗봇 사용자가 많아서 답변이 지연되고 있어요. 잠시 후 다시 시도해주세요.");
				}

				if (response.statusCode() / 100 != 2) {
					log.warn("> [GeminiClient][{}] 응답 실패 - status={}, body={}", logTag, response.statusCode(), response.body());
					return GeminiCallResult.failed(
							"답변을 가져오지 못했어요 (status=" + response.statusCode() + "). 다시 시도해주세요.");
				}

				JsonNode responseBody = objectMapper.readTree(response.body());
				JsonNode candidates = responseBody.path("candidates");
				if (!candidates.isArray() || candidates.isEmpty()) {
					log.warn("> [GeminiClient][{}] 답변 후보가 없음 - 원본 응답={}", logTag, response.body());
					return GeminiCallResult.failed("답변을 가져오지 못했어요. 다시 시도해주세요.");
				}

				JsonNode parts = candidates.get(0).path("content").path("parts");
				StringBuilder text = new StringBuilder();
				JsonNode functionCallPart = null;
				if (parts.isArray()) {
					for (JsonNode part : parts) {
						if (part.has("functionCall")) {
							functionCallPart = part;
						} else {
							text.append(part.path("text").asText(""));
						}
					}
				}

				if (functionCallPart != null) {
					return GeminiCallResult.functionCall(functionCallPart);
				}

				if (text.isEmpty()) {
					log.warn("> [GeminiClient][{}] 답변 텍스트가 비어있음 - 원본 응답={}", logTag, response.body());
					return GeminiCallResult.failed("답변을 가져오지 못했어요. 다시 시도해주세요.");
				}

				return GeminiCallResult.text(text.toString());
			} catch (IOException | InterruptedException e) {
				if (e instanceof InterruptedException) {
					Thread.currentThread().interrupt();
					return GeminiCallResult.failed("답변을 가져오지 못했어요. 다시 시도해주세요.");
				}
				log.warn("> [GeminiClient][{}] 요청 중 예외 발생(재시도 대상) - attempt={}/{}", logTag, attempt, MAX_ATTEMPTS, e);
				if (attempt < MAX_ATTEMPTS) {
					sleepBeforeRetry(attempt);
					continue;
				}
				return GeminiCallResult.failed("답변을 가져오지 못했어요. 다시 시도해주세요.");
			}
		}

		// 이론상 도달하지 않는다 (루프 안에서 항상 return됨) — 컴파일러를 위한 안전망.
		return GeminiCallResult.failed("답변을 가져오지 못했어요. 다시 시도해주세요.");
	}

	/**
	 * callWithRetry()의 내부 결과 — 텍스트 답변인지, 함수 호출 요청인지, 실패인지 셋 중 하나.
	 * quotaExceeded(2026-09-29 추가)는 실패 중에서도 429(할당량 초과)였는지만 따로 표시한다 —
	 * callWithKeyFallback이 "다른 키로 넘어가볼 만한 실패"를 구분하는 용도.
	 */
	private record GeminiCallResult(boolean success, String failReason, String text, JsonNode functionCallPart,
			boolean quotaExceeded) {
		static GeminiCallResult failed(String reason) {
			return new GeminiCallResult(false, reason, null, null, false);
		}

		static GeminiCallResult quotaExceeded(String reason) {
			return new GeminiCallResult(false, reason, null, null, true);
		}

		static GeminiCallResult text(String text) {
			return new GeminiCallResult(true, null, text, null, false);
		}

		static GeminiCallResult functionCall(JsonNode part) {
			return new GeminiCallResult(true, null, null, part, false);
		}
	}

	/**
	 * 재시도 사이에 잠깐 대기한다. attempt가 늘어날수록 조금씩 더 오래 기다린다(1.5초, 3초, ...).
	 * 대기 중 인터럽트(InterruptedException)는 여기서 직접 처리한다 — catch 블록 안에서도 이
	 * 메서드를 호출하는데, catch 블록 자체는 try로 보호되는 범위가 아니라서 이 메서드가
	 * InterruptedException을 밖으로 던지면 그 호출부에서 다시 처리해줘야 하는 번거로움이 있다.
	 */
	private void sleepBeforeRetry(int attempt) {
		try {
			Thread.sleep(RETRY_DELAY_MS * attempt);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
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

	/**
	 * success=true여야 reply가 채워져 있다. false면 failReason에 사람이 읽을 수 있는 실패 사유가
	 * 담긴다. quotaExceeded(2026-09-29 추가)는 그 실패가 429(할당량 초과)였는지만 따로 표시한다 —
	 * ChatService가 비회원용으로 다른 문구("지금 상담이 많아 답변이 어려워요...")를 쓰고 싶을 때
	 * failReason 문자열 내용을 직접 비교하지 않고 이 플래그로 판단하게 하기 위함.
	 */
	public record ChatResult(boolean success, String reply, String failReason, boolean quotaExceeded) {
		public static ChatResult success(String reply) {
			return new ChatResult(true, reply, null, false);
		}

		public static ChatResult failed(String reason) {
			return new ChatResult(false, null, reason, false);
		}

		public static ChatResult failed(String reason, boolean quotaExceeded) {
			return new ChatResult(false, null, reason, quotaExceeded);
		}
	}
}

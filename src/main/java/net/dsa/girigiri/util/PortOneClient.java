package net.dsa.girigiri.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * PortOne(포트원) V2 결제 검증 유틸.
 *
 * 결제창(프론트 브라우저 SDK)이 "결제 성공했다"고 응답을 돌려줘도, 그 응답을 서버가 그대로 믿으면
 * 안 된다 — 브라우저 쪽 응답은 개발자 도구 등으로 조작 가능해서, 실제로 결제가 됐는지는 반드시
 * 서버가 PortOne 서버에 직접 물어봐서(GET /payments/{paymentId}) 확인해야 한다. 이 클래스는 그
 * "서버 대 서버 확인"만 담당한다 (결제창을 띄우는 쪽은 프론트 JS의 PortOne 브라우저 SDK가 담당 —
 * reservationView/checkout.html 참고).
 *
 * 별도 SDK 없이 자바 표준 HttpClient로 PortOne REST API를 직접 호출한다
 * (SupabaseStorageClient와 동일한 이유로 build.gradle에 새 의존성을 안 늘리려고 일부러 이렇게 했다).
 *
 * 필요한 설정값(application.properties -> .env)은 README.md "로컬 실행" 섹션에 문서화돼 있다:
 *   PORTONE_STORE_ID, PORTONE_CHANNEL_KEY, PORTONE_API_SECRET
 *
 * 주의: 작성 시점(2026-08-24)엔 팀에 PortOne 테스트 계정이 아직 없어서 이 세 값이 전부 빈 상태다.
 *      isConfigured()가 false인 동안엔 verifyPayment()를 호출하면 바로 IllegalStateException이
 *      난다(체크아웃 화면도 이 값을 보고 "결제 준비중" 안내로 대체함 — ReservationController 참고).
 *      계정을 만들고 콘솔에서 발급받은 값을 .env에 채우기만 하면, 코드를 전혀 안 건드리고 바로
 *      동작한다. PortOne 응답 스키마(REST API v2)는 공식 문서 기준으로 맞춰뒀지만, 실제 계정으로
 *      끝까지 테스트해본 적은 없다는 점을 감안해달라 — 이 부분은 계정이 생기면 꼭 실제 결제 1건으로
 *      끝까지(결제창 → 검증 → confirmed 전환) 확인해봐야 한다.
 */
@Component
public class PortOneClient {

	private static final String API_BASE = "https://api.portone.io";

	private final HttpClient httpClient = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.build();
	private final ObjectMapper objectMapper = new ObjectMapper();

	private final String storeId;
	private final String channelKey;
	private final String apiSecret;

	public PortOneClient(
			@Value("${portone.store-id:}") String storeId,
			@Value("${portone.channel-key:}") String channelKey,
			@Value("${portone.api-secret:}") String apiSecret) {
		this.storeId = storeId;
		this.channelKey = channelKey;
		this.apiSecret = apiSecret;
	}

	/** .env에 PORTONE_STORE_ID / PORTONE_CHANNEL_KEY / PORTONE_API_SECRET이 전부 채워져 있는지. */
	public boolean isConfigured() {
		return !storeId.isBlank() && !channelKey.isBlank() && !apiSecret.isBlank();
	}

	/** 결제창(프론트 JS SDK)에 넘겨줄 storeId. isConfigured()가 false면 빈 문자열. */
	public String getStoreId() {
		return storeId;
	}

	/** 결제창(프론트 JS SDK)에 넘겨줄 channelKey. isConfigured()가 false면 빈 문자열. */
	public String getChannelKey() {
		return channelKey;
	}

	/**
	 * PortOne 서버에 이 결제(paymentId)가 실제로 결제 완료(PAID) 됐고, 금액이 expectedAmount와
	 * 정확히 일치하는지 확인한다.
	 *
	 * 금액이 다르면(예: 프론트에서 결제창을 띄울 때 금액이 조작된 경우) status가 PAID여도
	 * paid=false로 돌려준다 — "결제는 어쨌든 됐지만 우리가 요청한 금액이 아니다"도 승인 거부
	 * 대상이라서, 이 경우에도 절대 예약을 confirmed로 넘기면 안 되기 때문이다.
	 */
	public PortOneVerifyResult verifyPayment(String paymentId, int expectedAmount) {
		if (!isConfigured()) {
			throw new IllegalStateException(
					"PortOne 설정이 비어있어요. .env에 PORTONE_STORE_ID / PORTONE_CHANNEL_KEY / " +
					"PORTONE_API_SECRET을 채워주세요. (필요한 키 목록은 README.md \"로컬 실행\" 섹션 참고)");
		}

		try {
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(API_BASE + "/payments/" + paymentId))
					.timeout(Duration.ofSeconds(15))
					.header("Authorization", "PortOne " + apiSecret)
					.GET()
					.build();

			HttpResponse<String> response =
					httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

			if (response.statusCode() == 404) {
				return PortOneVerifyResult.failed("PortOne에서 해당 결제 건을 찾을 수 없어요. paymentId=" + paymentId);
			}
			if (response.statusCode() / 100 != 2) {
				return PortOneVerifyResult.failed(
						"PortOne 결제 조회에 실패했어요 (status=" + response.statusCode() + "): " + response.body());
			}

			JsonNode body = objectMapper.readTree(response.body());
			String status = body.path("status").asText("");
			int actualAmount = body.path("amount").path("total").asInt(-1);
			String transactionId = body.path("transactionId").asText(null);

			if (!"PAID".equals(status)) {
				return PortOneVerifyResult.failed("결제가 완료되지 않았어요 (status=" + status + ")");
			}
			if (actualAmount != expectedAmount) {
				return PortOneVerifyResult.failed(
						"결제 금액이 일치하지 않아요 (요청 금액=" + expectedAmount + "원, 실제 결제 금액=" + actualAmount + "원)");
			}

			return PortOneVerifyResult.success(transactionId, actualAmount);
		} catch (IOException | InterruptedException e) {
			if (e instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			return PortOneVerifyResult.failed("PortOne 결제 조회 중 오류가 발생했어요: " + e.getMessage());
		}
	}

	/** paid=true여야 진짜 결제 성공. false면 failReason에 사람이 읽을 수 있는 실패 사유가 담긴다. */
	public record PortOneVerifyResult(boolean paid, String transactionId, Integer amount, String failReason) {
		public static PortOneVerifyResult success(String transactionId, int amount) {
			return new PortOneVerifyResult(true, transactionId, amount, null);
		}

		public static PortOneVerifyResult failed(String reason) {
			return new PortOneVerifyResult(false, null, null, reason);
		}
	}

	/**
	 * 이미 결제 완료(paid)된 건을 PortOne에 실제로 취소(환불) 요청한다.
	 * 예약 취소(ReservationService.cancelReservation/cancelByStore) 시, 결제가 이미 paid였던 경우에만
	 * 호출된다 — 아직 결제 전(ready)인 pending 예약을 취소할 땐 애초에 환불할 돈이 없으니 호출할 필요가 없다.
	 *
	 * 나이스정보통신 테스트 모드는 결제 후 그날 밤(23:00~23:50) 사이에 자동으로 취소되는 특성이 있어서
	 * (help.portone.io "나이스페이먼츠 채널설정 방법" 참고), 이미 그렇게 자동 취소된 건을 우리가 또
	 * 취소해달라고 요청하면 PortOne이 "이미 취소된 결제"라는 에러를 돌려줄 수 있다 — 이 경우는
	 * "환불이 안 됐다"가 아니라 오히려 원하던 결과(취소됨)가 이미 이뤄진 상태이므로 성공으로 취급한다.
	 */
	public PortOneCancelResult cancelPayment(String paymentId, String reason) {
		if (!isConfigured()) {
			return PortOneCancelResult.failed("PortOne 설정이 비어있어서 환불 요청을 보낼 수 없어요.");
		}

		try {
			String requestBody = objectMapper.writeValueAsString(
					Map.of("reason", (reason == null || reason.isBlank()) ? "예약 취소" : reason));

			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(API_BASE + "/payments/" + paymentId + "/cancel"))
					.timeout(Duration.ofSeconds(15))
					.header("Authorization", "PortOne " + apiSecret)
					.header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
					.build();

			HttpResponse<String> response =
					httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

			if (response.statusCode() / 100 == 2) {
				return PortOneCancelResult.success();
			}

			String errorType = "";
			try {
				errorType = objectMapper.readTree(response.body()).path("type").asText("");
			} catch (IOException ignored) {
				// 에러 응답 바디가 JSON이 아니어도(거의 없겠지만) 아래에서 원문 그대로 실패 사유에 담아 처리한다.
			}

			// 이미 취소/환불된 결제를 또 취소하려 한 경우 -> 목표(환불됨)는 이미 달성된 상태라 성공으로 취급.
			if (errorType.contains("ALREADY_PAID_CANCELLED") || errorType.contains("ALREADY_CANCELLED")) {
				return PortOneCancelResult.success();
			}

			return PortOneCancelResult.failed(
					"PortOne 환불 요청이 실패했어요 (status=" + response.statusCode() + "): " + response.body());
		} catch (IOException | InterruptedException e) {
			if (e instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			return PortOneCancelResult.failed("PortOne 환불 요청 중 오류가 발생했어요: " + e.getMessage());
		}
	}

	/** cancelled=true여야 진짜 환불 성공(또는 이미 환불된 상태 확인). false면 failReason에 실패 사유가 담긴다. */
	public record PortOneCancelResult(boolean cancelled, String failReason) {
		public static PortOneCancelResult success() {
			return new PortOneCancelResult(true, null);
		}

		public static PortOneCancelResult failed(String reason) {
			return new PortOneCancelResult(false, reason);
		}
	}
}

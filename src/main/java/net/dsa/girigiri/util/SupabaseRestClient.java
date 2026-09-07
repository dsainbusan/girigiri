package net.dsa.girigiri.util;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.lang.reflect.Array;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * Supabase의 데이터 REST API(PostgREST)를 자바 표준 HttpClient로 직접 호출하는 유틸 (매출 리포트, 문창호).
 *
 * 파일 저장용 {@link SupabaseStorageClient}와 같은 패턴이다 — 별도 SDK 없이,
 * build.gradle에 의존성을 안 늘리려고 HttpClient로 직접 짰다. 설정값도 같은 것을 공유한다:
 *   SUPABASE_URL, SUPABASE_SERVICE_KEY  (application.properties -> .env, README "로컬 실행" 참고)
 *
 * PostgREST 규칙 요약:
 *   - 조회:  GET  {url}/rest/v1/{table}?컬럼=연산자.값&order=컬럼.asc   (예: store_id=eq.2&sold_at=gte.2026-09-01)
 *   - 삽입:  POST {url}/rest/v1/{table}   body = JSON 객체 또는 배열
 *   - 삭제:  DELETE {url}/rest/v1/{table}?조건
 *   - 인증:  apikey 헤더 + Authorization: Bearer  (둘 다 service_role 키)
 *
 * JSON은 camelCase(자바) <-> snake_case(DB)로 자동 변환한다. id/created_at 처럼 DB가 채우는 값을
 * insert 때 생략하려면 DTO 쪽에 @JsonInclude(NON_NULL)을 붙인다 (SalesRow 참고).
 */
@Component
public class SupabaseRestClient {

	private final HttpClient httpClient = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.build();

	private final ObjectMapper objectMapper = JsonMapper.builder()
			.addModule(new JavaTimeModule())
			.propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
			.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
			.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
			.build();

	private final String baseUrl;      // 예: https://xxxxxxxx.supabase.co
	private final String serviceKey;   // service_role 키

	public SupabaseRestClient(
			@Value("${supabase.url:}") String baseUrl,
			@Value("${supabase.service-key:}") String serviceKey) {
		this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
		this.serviceKey = serviceKey == null ? "" : serviceKey;
	}

	/** .env에 SUPABASE_URL / SUPABASE_SERVICE_KEY가 채워져 있는지. 아직 없으면 false. */
	public boolean isConfigured() {
		return !baseUrl.isBlank() && !serviceKey.isBlank();
	}

	/**
	 * GET /rest/v1/{table}?{query} 결과를 elementType 리스트로 역직렬화한다.
	 * query 예: "store_id=eq.2&sold_at=gte.2026-09-01&order=sold_at.asc" (앞에 ? 붙이지 않는다)
	 */
	@SuppressWarnings("unchecked")
	public <T> List<T> select(String table, String query, Class<T> elementType) {
		requireConfigured();
		String url = baseUrl + "/rest/v1/" + table + (query == null || query.isBlank() ? "" : "?" + query);
		HttpRequest request = baseRequest(url)
				.header("Accept", "application/json")
				.GET()
				.build();
		String body = send(request, "조회");
		Class<T[]> arrayType = (Class<T[]>) Array.newInstance(elementType, 0).getClass();
		try {
			return Arrays.asList(objectMapper.readValue(body, arrayType));
		} catch (IOException e) {
			throw new IllegalStateException("Supabase 응답 파싱 실패 (table=" + table + "): " + body, e);
		}
	}

	/** POST /rest/v1/{table} — 행 여러 개를 한 번에 삽입. 반환값은 안 받는다(Prefer: return=minimal). */
	public void insertAll(String table, List<?> rows) {
		requireConfigured();
		String json;
		try {
			json = objectMapper.writeValueAsString(rows);
		} catch (IOException e) {
			throw new IllegalStateException("Supabase 요청 직렬화 실패 (table=" + table + ")", e);
		}
		HttpRequest request = baseRequest(baseUrl + "/rest/v1/" + table)
				.header("Content-Type", "application/json")
				.header("Prefer", "return=minimal")
				.POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
				.build();
		send(request, "삽입");
	}

	/** 행 1개 삽입. */
	public void insert(String table, Object row) {
		insertAll(table, List.of(row));
	}

	/** DELETE /rest/v1/{table}?{query} — 조건에 맞는 행 삭제. 데모 데이터 초기화용. */
	public void delete(String table, String query) {
		requireConfigured();
		if (query == null || query.isBlank()) {
			throw new IllegalArgumentException("Supabase delete에는 조건(query)이 필요합니다. 전체 삭제 방지.");
		}
		HttpRequest request = baseRequest(baseUrl + "/rest/v1/" + table + "?" + query)
				.DELETE()
				.build();
		send(request, "삭제");
	}

	// --- 내부 헬퍼 ---

	private HttpRequest.Builder baseRequest(String url) {
		return HttpRequest.newBuilder()
				.uri(URI.create(url))
				.timeout(Duration.ofSeconds(15))
				.header("apikey", serviceKey)
				.header("Authorization", "Bearer " + serviceKey);
	}

	private String send(HttpRequest request, String action) {
		try {
			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (response.statusCode() / 100 != 2) {
				throw new IllegalStateException(
						"Supabase " + action + " 실패 (status=" + response.statusCode() + "): " + response.body());
			}
			return response.body();
		} catch (IOException | InterruptedException e) {
			if (e instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			throw new IllegalStateException("Supabase " + action + " 중 오류가 발생했어요.", e);
		}
	}

	private void requireConfigured() {
		if (!isConfigured()) {
			throw new IllegalStateException(
					"Supabase 설정이 비어있어요. .env에 SUPABASE_URL / SUPABASE_SERVICE_KEY를 채워주세요. " +
					"(필요한 키 목록은 README.md \"로컬 실행\" 섹션 참고)");
		}
	}
}

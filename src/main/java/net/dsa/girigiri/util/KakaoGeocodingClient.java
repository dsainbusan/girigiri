package net.dsa.girigiri.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * 강노은: 좌표 → 동네 이름 역지오코딩. 카카오 로컬 API(coord2regioncode)를 REST API 키로 호출한다.
 * home.html 상단바의 "내 동네" 고정 문자열을 실제 위치 기반 동네 이름으로 바꾸는 데 쓴다
 * (HomeController#home 참고). 카카오맵 JS SDK(kakao.map.js-key, 마커 렌더링용)와는 별개 키다 —
 * 직접 curl로 확인한 결과, 카카오 로그인용 KAKAO_CLIENT_ID로는 이 API가 403(카카오맵 제품
 * 비활성화)이 났고, 콘솔에서 발급한 KAKAO_REST_API_KEY로는 정상 동작했다(2026-09-01 확인).
 *
 * .env에 KAKAO_REST_API_KEY가 없거나 호출이 실패하면 Optional.empty()를 돌려준다 — 호출부는 이때
 * "내 동네" 등 기존 고정 문구로 폴백하면 된다(부가 기능이라 실패해도 화면이 깨지면 안 됨).
 */
@Slf4j
@Component
public class KakaoGeocodingClient {

	private static final String API_URL =
			"https://dapi.kakao.com/v2/local/geo/coord2regioncode.json?x=%s&y=%s";

	private final HttpClient httpClient = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(5))
			.build();
	private final ObjectMapper objectMapper = new ObjectMapper();

	private final String restApiKey;

	public KakaoGeocodingClient(@Value("${kakao.rest-api-key:}") String restApiKey) {
		this.restApiKey = restApiKey;
	}

	/** .env에 KAKAO_REST_API_KEY가 채워져 있는지. */
	public boolean isConfigured() {
		return restApiKey != null && !restApiKey.isBlank();
	}

	/** 위도/경도를 "구 + 동" 형태의 동네 이름으로 변환한다(예: "중구 명동"). 실패하면 empty. */
	public Optional<String> reverseGeocode(double lat, double lng) {
		if (!isConfigured()) {
			return Optional.empty();
		}

		try {
			// 카카오 로컬 API는 x=경도(lng), y=위도(lat) 순서다 — 반대로 넣으면 엉뚱한 지역이 나온다.
			String url = String.format(API_URL, lng, lat);
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(url))
					.timeout(Duration.ofSeconds(5))
					.header("Authorization", "KakaoAK " + restApiKey)
					.GET()
					.build();

			HttpResponse<String> response =
					httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

			if (response.statusCode() != 200) {
				log.warn("> [KakaoGeocodingClient] 응답 실패 - status={}, body={}",
						response.statusCode(), response.body());
				return Optional.empty();
			}

			JsonNode documents = objectMapper.readTree(response.body()).path("documents");
			return Optional.ofNullable(pickRegionLabel(documents));
		} catch (IOException | InterruptedException e) {
			if (e instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			log.warn("> [KakaoGeocodingClient] 요청 중 예외", e);
			return Optional.empty();
		}
	}

	/**
	 * region_type "H"(행정동)를 우선 쓴다 — "B"(법정동)는 행정 구역 등록용이라 사람이 실제로 부르는
	 * 동네 이름과 다를 때가 있다(예: 여러 법정동을 묶은 행정동). 응답엔 보통 B/H 둘 다 오는데, H가
	 * 없으면(드문 케이스) 첫 번째 결과로 폴백한다.
	 */
	private String pickRegionLabel(JsonNode documents) {
		JsonNode target = null;
		for (JsonNode doc : documents) {
			if ("H".equals(doc.path("region_type").asText())) {
				target = doc;
				break;
			}
		}
		if (target == null && documents.isArray() && !documents.isEmpty()) {
			target = documents.get(0);
		}
		if (target == null) {
			return null;
		}

		String gu = target.path("region_2depth_name").asText("");
		String dong = target.path("region_3depth_name").asText("");
		String label = (gu + " " + dong).trim();
		return label.isBlank() ? null : label;
	}
}

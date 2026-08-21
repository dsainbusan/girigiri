package net.dsa.girigiri.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Supabase Storage(클라우드 파일 저장소)에 파일을 올리는 유틸.
 *
 * 예전엔 영수증 PDF를 그냥 프로젝트 폴더 밑 receipts/에 저장했는데, 그러면 로컬 컴퓨터가 꺼지거나
 * 서버가 재배포되면 파일이 사라진다. Supabase Storage에 올려두면 URL 하나로 어디서든 접근 가능하고,
 * 파일 자체는 클라우드에 안전하게 보관된다.
 *
 * 별도 SDK 없이 Supabase Storage의 REST API를 자바 표준 HttpClient로 직접 호출한다
 * (build.gradle에 새 의존성을 안 늘리려고 일부러 이렇게 했다).
 *
 * 필요한 설정값(application.properties -> .env)은 README.md "로컬 실행" 섹션에 문서화돼 있다:
 *   SUPABASE_URL, SUPABASE_SERVICE_KEY, SUPABASE_STORAGE_BUCKET
 *
 * 주의: 이 버킷은 Supabase 대시보드에서 "Public bucket"으로 만들어야 한다. private 버킷이면
 *      아래 publicUrl()로 만든 링크로 못 열고, 매번 서명된(만료되는) URL을 새로 발급받아야 해서
 *      지금 구조(그냥 URL을 DB에 저장해두고 계속 재사용)랑 안 맞는다.
 */
@Component
public class SupabaseStorageClient {

	private final HttpClient httpClient = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.build();

	private final String baseUrl;      // 예: https://xxxxxxxx.supabase.co
	private final String serviceKey;   // Supabase 프로젝트의 service_role 키 (또는 anon 키)
	private final String bucket;       // 예: receipts

	public SupabaseStorageClient(
			@Value("${supabase.url:}") String baseUrl,
			@Value("${supabase.service-key:}") String serviceKey,
			@Value("${supabase.storage.bucket:receipts}") String bucket) {
		// 맨 끝 슬래시가 있으면 URL 조립할 때 //가 겹치니 미리 떼둔다.
		this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
		this.serviceKey = serviceKey;
		this.bucket = bucket;
	}

	/** .env에 SUPABASE_URL / SUPABASE_SERVICE_KEY가 채워져 있는지. 아직 없으면 false. */
	public boolean isConfigured() {
		return !baseUrl.isBlank() && !serviceKey.isBlank();
	}

	/**
	 * PDF 바이트를 Supabase Storage의 {bucket}/{path}에 업로드하고, 공개 URL을 돌려준다.
	 * 이미 같은 path에 파일이 있으면 덮어쓴다(upsert) — 취소/노쇼처럼 같은 예약의 영수증을
	 * 다시 만들 때 새 파일로 매번 갈아끼울 수 있어야 하기 때문.
	 */
	public String uploadPdf(String path, byte[] content) {
		if (baseUrl.isBlank() || serviceKey.isBlank()) {
			throw new IllegalStateException(
					"Supabase 설정이 비어있어요. .env에 SUPABASE_URL / SUPABASE_SERVICE_KEY를 채워주세요. " +
					"(필요한 키 목록은 README.md \"로컬 실행\" 섹션 참고)");
		}

		String uploadUrl = baseUrl + "/storage/v1/object/" + bucket + "/" + path;

		try {
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(uploadUrl))
					.timeout(Duration.ofSeconds(20))
					.header("Authorization", "Bearer " + serviceKey)
					.header("apikey", serviceKey)
					.header("Content-Type", "application/pdf")
					.header("x-upsert", "true")   // 같은 path면 새로 덮어쓰기 (없으면 이미 있을 때 409 에러남)
					.POST(HttpRequest.BodyPublishers.ofByteArray(content))
					.build();

			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

			if (response.statusCode() / 100 != 2) {
				throw new IllegalStateException(
						"Supabase 업로드 실패 (status=" + response.statusCode() + "): " + response.body());
			}
		} catch (IOException | InterruptedException e) {
			if (e instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			throw new IllegalStateException("Supabase 업로드 중 오류가 발생했어요. path=" + path, e);
		}

		return publicUrl(path);
	}

	/** 버킷이 public이라는 전제로, 업로드 없이도 미리 계산 가능한 공개 URL. */
	public String publicUrl(String path) {
		return baseUrl + "/storage/v1/object/public/" + bucket + "/" + path;
	}
}

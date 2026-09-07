package net.dsa.girigiri;

import net.dsa.girigiri.domain.dto.SalesRow;
import net.dsa.girigiri.util.SupabaseRestClient;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * SupabaseRestClient의 스프링/네트워크 없이 확인 가능한 부분만 테스트한다
 * (SupabaseStorageClientTest와 같은 방침 — 실제 Supabase 왕복은 계정이 있어야 하니 여기선 안 다룬다).
 */
class SupabaseRestClientTest {

	@Test
	void 설정값이_없으면_isConfigured가_false다() {
		SupabaseRestClient client = new SupabaseRestClient("", "");
		assertFalse(client.isConfigured());
	}

	@Test
	void 설정값이_없는_상태로_조회하면_SUPABASE_URL_안내와_함께_에러를_던진다() {
		SupabaseRestClient client = new SupabaseRestClient("", "");

		IllegalStateException e = assertThrows(IllegalStateException.class,
				() -> client.select("sales", "store_id=eq.1", SalesRow.class));

		assertTrue(e.getMessage().contains("SUPABASE_URL"));
	}

	@Test
	void 설정값이_있으면_isConfigured가_true다() {
		SupabaseRestClient client = new SupabaseRestClient("https://example.supabase.co", "dummy-key");
		assertTrue(client.isConfigured());
	}

	@Test
	void delete는_조건_없이_호출하면_전체삭제_방지를_위해_거부한다() {
		SupabaseRestClient client = new SupabaseRestClient("https://example.supabase.co", "dummy-key");

		assertThrows(IllegalArgumentException.class, () -> client.delete("sales", ""));
		assertThrows(IllegalArgumentException.class, () -> client.delete("sales", null));
	}

	@Test
	void SalesRow_of는_금액을_단가곱수량으로_채운다() {
		SalesRow row = SalesRow.of(2L, LocalDate.of(2026, 9, 1), "크림빵", "베이커리", 3, 2500);

		assertEquals(7500, row.amount());
		assertEquals(List.of(2L, 3, 2500), List.of(row.storeId(), row.qty(), row.unitPrice()));
	}

	/**
	 * 실제 Supabase 왕복 스모크 테스트. 프로젝트 루트 .env에 SUPABASE_URL / SUPABASE_SERVICE_KEY가
	 * 채워져 있을 때만 실행되고, 없으면 조용히 건너뛴다(assumeTrue). 센티넬 store_id로 넣고 바로 지운다.
	 */
	@Test
	void 실제_Supabase에_insert하고_select로_읽고_delete까지_된다() {
		Properties env = loadDotEnv();
		String url = env.getProperty("SUPABASE_URL", "");
		String key = env.getProperty("SUPABASE_SERVICE_KEY", "");
		assumeTrue(!url.isBlank() && !key.isBlank(), ".env에 Supabase 설정이 없어 스모크 테스트를 건너뜁니다.");

		SupabaseRestClient client = new SupabaseRestClient(url, key);
		long sentinelStoreId = 990001L;
		String filter = "store_id=eq." + sentinelStoreId;

		client.delete("sales", filter);   // 앞선 실패로 남은 게 있으면 정리
		try {
			client.insertAll("sales", List.of(
					SalesRow.of(sentinelStoreId, LocalDate.of(2026, 9, 1), "smoke test bun", "bakery", 2, 3000),
					SalesRow.of(sentinelStoreId, LocalDate.of(2026, 9, 2), "smoke test milk", "beverage", 1, 1500)
			));

			List<SalesRow> rows = client.select("sales", filter + "&order=sold_at.asc", SalesRow.class);

			assertEquals(2, rows.size());
			assertEquals(sentinelStoreId, rows.get(0).storeId());
			assertEquals(LocalDate.of(2026, 9, 1), rows.get(0).soldAt());   // snake_case(sold_at) → LocalDate 역직렬화 확인
			assertEquals(6000, rows.get(0).amount());
			assertTrue(rows.get(0).id() != null && rows.get(0).createdAt() != null);  // DB가 채운 값
		} finally {
			client.delete("sales", filter);
		}
	}

	private Properties loadDotEnv() {
		Properties p = new Properties();
		Path envFile = Path.of(".env");
		if (Files.exists(envFile)) {
			try {
				p.load(Files.newBufferedReader(envFile));
			} catch (IOException ignored) {
				// 못 읽으면 빈 Properties → 스모크 테스트는 assumeTrue에서 건너뜀
			}
		}
		return p;
	}
}

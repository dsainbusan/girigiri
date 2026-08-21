package net.dsa.girigiri;

import net.dsa.girigiri.util.SupabaseStorageClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SupabaseStorageClient 자체는 DB/스프링 없이도 확인할 수 있는 부분만 테스트한다
 * (실제 Supabase 서버에 업로드하는 부분은 진짜 계정이 있어야 확인 가능하니 여기선 안 다룬다).
 *
 * - 설정값(.env의 SUPABASE_URL/SUPABASE_SERVICE_KEY)이 비어있을 때 안전하게 동작하는지
 * - 설정값이 있을 때 URL 조립이 정확한지
 */
class SupabaseStorageClientTest {

	@Test
	void 설정값이_없으면_isConfigured가_false다() {
		SupabaseStorageClient client = new SupabaseStorageClient("", "", "receipts");
		assertFalse(client.isConfigured());
	}

	@Test
	void 설정값이_없는_상태로_업로드하면_바로_에러를_던진다() {
		SupabaseStorageClient client = new SupabaseStorageClient("", "", "receipts");

		IllegalStateException e = assertThrows(IllegalStateException.class,
				() -> client.uploadPdf("receipt-1.pdf", new byte[]{1, 2, 3}));

		assertTrue(e.getMessage().contains("SUPABASE_URL"));
	}

	@Test
	void 설정값이_있으면_isConfigured가_true다() {
		SupabaseStorageClient client = new SupabaseStorageClient(
				"https://example.supabase.co", "dummy-service-key", "receipts");
		assertTrue(client.isConfigured());
	}

	@Test
	void 공개_URL이_예상한_형식으로_조립된다() {
		SupabaseStorageClient client = new SupabaseStorageClient(
				"https://example.supabase.co/", "dummy-service-key", "receipts");   // 끝에 슬래시 있어도 정상 처리되는지도 확인

		String url = client.publicUrl("receipt-1.pdf");

		assertEquals("https://example.supabase.co/storage/v1/object/public/receipts/receipt-1.pdf", url);
	}
}

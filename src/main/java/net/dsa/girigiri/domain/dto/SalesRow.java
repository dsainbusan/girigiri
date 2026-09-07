package net.dsa.girigiri.domain.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDate;

/**
 * Supabase `public.sales` 테이블의 한 행 (매출 리포트, 문창호).
 *
 * MySQL이 아니라 Supabase(Postgres)에 적재하는 매출 데이터라 JPA 엔티티가 아니다.
 * SupabaseRestClient가 이 record를 JSON으로 직렬화(camelCase → snake_case)해서 PostgREST로 주고받는다.
 *
 * insert 시에는 id / createdAt 을 null로 두면 @JsonInclude(NON_NULL) 덕분에 그 필드가 빠진 채로 전송된다
 * (id는 DB가 자동 발급, created_at은 DB 기본값 now()).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SalesRow(
		Long id,
		Long storeId,
		LocalDate soldAt,
		String productName,
		String category,
		int qty,
		int unitPrice,
		int amount,
		String createdAt
) {
	/** 시뮬레이터/집계에서 새 매출 행을 만들 때. amount = 단가 × 수량. */
	public static SalesRow of(Long storeId, LocalDate soldAt, String productName, String category, int qty, int unitPrice) {
		return new SalesRow(null, storeId, soldAt, productName, category, qty, unitPrice, unitPrice * qty, null);
	}
}

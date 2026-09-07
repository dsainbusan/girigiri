package net.dsa.girigiri.domain.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDate;

/**
 * Supabase `public.sales` 테이블의 한 행 (매출 리포트, 문창호).
 * 한 행 = (날짜, 상품)별 마감 진열 스냅샷 — POS 마감 리포트가 이렇게 SKU별로 나온다고 가정.
 *
 * MySQL이 아니라 Supabase(Postgres)에 적재하는 데이터라 JPA 엔티티가 아니다.
 * SupabaseRestClient가 이 record를 JSON으로 직렬화(camelCase → snake_case)해서 PostgREST로 주고받는다.
 * insert 시 id / createdAt 을 null로 두면 @JsonInclude(NON_NULL) 덕분에 그 필드가 빠진 채로 전송된다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SalesRow(
		Long id,
		Long storeId,
		LocalDate saleDate,
		String productName,
		String category,
		int registeredQty,   // 그날 내놓은 수량
		int soldQty,         // 그중 팔린 수량
		int originalPrice,   // 정상가
		int salePrice,       // 마감 할인가
		String createdAt
) {
	/** 회수 매출 = 판매 수량 × 마감 할인가. */
	public long revenue() {
		return (long) soldQty * salePrice;
	}

	/** 폐기 수량 = 등록 − 판매. */
	public int wastedQty() {
		return registeredQty - soldQty;
	}

	/** 할인 제공액 = 판매 수량 × (정상가 − 할인가). */
	public long discountGiven() {
		return (long) soldQty * (originalPrice - salePrice);
	}

	/** 테스트/시드에서 새 행을 만들 때 (id·createdAt은 DB가 채움). */
	public static SalesRow of(Long storeId, LocalDate saleDate, String productName, String category,
	                          int registeredQty, int soldQty, int originalPrice, int salePrice) {
		return new SalesRow(null, storeId, saleDate, productName, category,
				registeredQty, soldQty, originalPrice, salePrice, null);
	}
}

package net.dsa.girigiri.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dsa.girigiri.domain.dto.SalesRow;
import net.dsa.girigiri.domain.entity.ProductEntity;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.repository.ProductRepository;
import net.dsa.girigiri.repository.StoreRepository;
import net.dsa.girigiri.util.SupabaseRestClient;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * 매장의 "오늘 올린 상품" 등록/판매 현황을 Supabase sales 테이블에 반영한다 (문창호).
 *
 * MySQL이 진짜 원장이고, Supabase는 매출 리포트(SalesReportService)가 읽는 미러다.
 * - 손님이 픽업을 완료하면 ReservationService.confirmPickup에서 이 서비스를 호출해 즉시 반영
 * - SalesSyncScheduler가 5분마다 전체 매장을 훑어 보정 (픽업 훅이 실패했거나, 마감 후 폐기 확정분)
 *
 * (store_id, sale_date, product_name) UNIQUE 제약 위에서 upsert 하므로, 하루 중 여러 번 호출돼도
 * 같은 상품 행이 최신 수치로 갱신될 뿐 중복되지 않는다. Supabase 호출 실패는 삼켜서(로그만) 절대
 * 픽업/스케줄 흐름을 막지 않는다 — best-effort 미러.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SalesSyncService {

	/** 매출로 집계할 상품 상태. draft/skipped(발행 안 된 초안)는 제외. */
	private static final Set<String> COUNTED_STATUSES = Set.of("active", "sold", "expired");

	private static final String ON_CONFLICT = "store_id,sale_date,product_name";

	private final ProductRepository productRepository;
	private final StoreRepository storeRepository;
	private final SupabaseRestClient supabase;

	/** 승인된 모든 OWNER 매장의 오늘 매출을 동기화 (스케줄러용). */
	public void syncAllStoresToday() {
		if (!supabase.isConfigured()) {
			return;
		}
		for (StoreEntity store : storeRepository.findByApprovalStatusAndRole("APPROVED", "OWNER")) {
			syncStoreToday(store.getId());
		}
	}

	/** 한 매장의 오늘 매출을 동기화. Supabase 미설정/실패 시 조용히 넘어간다. */
	public void syncStoreToday(Long storeId) {
		if (storeId == null || !supabase.isConfigured()) {
			return;
		}
		try {
			StoreEntity store = storeRepository.findById(storeId).orElse(null);
			if (store == null) {
				return;
			}
			LocalDate today = LocalDate.now();
			List<SalesRow> rows = productRepository.findByStoreId(storeId).stream()
					.filter(p -> p.getRegisteredAt() != null
							&& p.getRegisteredAt().toLocalDate().isEqual(today)
							&& COUNTED_STATUSES.contains(p.getStatus()))
					.map(p -> toRow(store, p, today))
					// sales 테이블 CHECK 제약(registered>0, 0<=sale<=original) 어기는 행은 건너뛴다
					.filter(r -> r.registeredQty() > 0 && r.originalPrice() > 0
							&& r.salePrice() >= 0 && r.salePrice() <= r.originalPrice()
							&& r.soldQty() >= 0 && r.soldQty() <= r.registeredQty())
					.toList();
			if (rows.isEmpty()) {
				return;
			}
			supabase.upsert("sales", rows, ON_CONFLICT);
		} catch (RuntimeException e) {
			log.warn("Supabase 매출 동기화 실패 (storeId={}): {}", storeId, e.toString());
		}
	}

	private SalesRow toRow(StoreEntity store, ProductEntity p, LocalDate today) {
		int registered = nz(p.getQuantity());
		int remaining = nz(p.getRemainingQuantity());
		int sold = Math.max(0, Math.min(registered, registered - remaining));   // 대시보드와 같은 "등록 − 남은재고"
		return SalesRow.of(store.getId(), today, p.getName(), store.getCategory(),
				Math.max(1, registered), sold, nz(p.getOriginalPrice()), nz(p.getDiscountedPrice()));
	}

	private static int nz(Integer v) {
		return v == null ? 0 : v;
	}
}

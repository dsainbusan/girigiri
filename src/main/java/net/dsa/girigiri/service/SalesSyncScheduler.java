package net.dsa.girigiri.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매출 미러 동기화 스케줄러 (문창호). 5분마다 승인된 OWNER 매장의 오늘 매출을
 * Supabase sales 테이블로 밀어넣는다 (SalesSyncService).
 *
 * 픽업 완료 시점엔 ReservationService.confirmPickup이 즉시 동기화하므로 이 스케줄러는 보정용:
 * 그 호출이 실패했거나, 마감 후 안 팔리고 폐기 확정된 수량을 반영한다.
 * Supabase 미설정이면 SalesSyncService가 알아서 no-op.
 */
@Component
@RequiredArgsConstructor
public class SalesSyncScheduler {

	private final SalesSyncService salesSyncService;

	@Scheduled(fixedDelay = 300_000L, initialDelay = 60_000L)
	public void syncTodaySales() {
		salesSyncService.syncAllStoresToday();
	}
}

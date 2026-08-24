package net.dsa.girigiri.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 픽업 마감시간이 지났는데도 픽업 안 된 예약을 주기적으로 "노쇼"로 자동 처리하는 스케줄러.
 * 실제 처리 로직은 전부 ReservationService.processNoShows()에 있고, 여기서는 그냥
 * "언제, 얼마나 자주 돌릴지"만 결정한다 (관심사 분리 — 나중에 주기를 바꾸고 싶으면 여기만 고치면 됨).
 *
 * 지금은 5분마다 한 번씩 돈다. 배포 환경에 맞게 나중에 조정 가능.
 * (@EnableScheduling은 GirigiriApplication에 이미 추가해둠)
 *
 * 추가됨 (2026-08-24, PortOne 연동) — 왜: 결제창을 띄운 채 결제를 끝내지 않고 이탈한 pending 예약도
 * 노쇼와 마찬가지로 "그대로 두면 재고를 영구히 붙잡고 있는" 문제라서, 별도 스케줄러를 새로 만들기보다
 * 이미 도는 이 스케줄러에 같이 얹었다 (둘 다 "방치된 예약 정리"라는 같은 성격의 작업이라 굳이
 * 클래스를 나눌 필요는 없다고 판단).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NoShowScheduler {

	private static final long FIVE_MINUTES_MS = 5 * 60 * 1000L;

	private final ReservationService reservationService;

	@Scheduled(fixedRate = FIVE_MINUTES_MS)
	public void autoProcessNoShows() {
		int count = reservationService.processNoShows();
		if (count > 0) {
			log.info("> [NoShowScheduler] 노쇼 자동 처리된 예약 {}건", count);
		} else {
			log.debug("> [NoShowScheduler] 노쇼 처리 대상 없음");
		}
	}

	@Scheduled(fixedRate = FIVE_MINUTES_MS)
	public void autoExpireStalePendingReservations() {
		int count = reservationService.expireStalePendingReservations();
		if (count > 0) {
			log.info("> [NoShowScheduler] 결제 시간 초과로 자동 취소된 예약 {}건", count);
		} else {
			log.debug("> [NoShowScheduler] 결제 시간 초과 정리 대상 없음");
		}
	}
}

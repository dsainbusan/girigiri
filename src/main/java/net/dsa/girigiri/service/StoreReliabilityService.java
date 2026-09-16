package net.dsa.girigiri.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dsa.girigiri.domain.dto.StoreCancelStatsDto;
import net.dsa.girigiri.domain.entity.ReservationEntity;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.repository.ReservationRepository;
import net.dsa.girigiri.repository.StoreRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 매장 신뢰도(취소율) 자동 정지/영구정지. 담당: 송채현 (2026-09-16 신규, 채채 확인).
 *
 * 규칙 (채채와 상의해서 확정한 내용):
 *  - 최소 예약 10건 이상인 매장만 대상(신생매장 보호). 10건 미만은 이 서비스가 정지를 걸지 않는다.
 *    처음엔 이 경우 손님에게 "이 매장은 취소 확률이 있어요" 경고 모달을 띄우는 것까지 만들었었는데,
 *    채채 판단으로 뺐다 — 경고 문구가 곧 "이 매장 믿을 만하지 않다"는 소문이 될 수 있고, 그러면
 *    신생매장이 애초에 신뢰도를 회복할 기회 자체가 줄어든다. 그래서 신생매장은 그냥 조용히
 *    보호만 하고(정지 없음), 손님에게 따로 알리지 않는다.
 *  - 신뢰도(=100-최근 예약 기준 취소율)가 70% 미만이 되면 단계별로 정지: 1회차 7일 → 2회차 14일 →
 *    3회차 30일 → 4회차(=정지 3번을 다 쓴 뒤 또 위반)부터는 영구정지.
 *  - "다음 위반"은 정지가 풀린 뒤 신뢰도가 70% 이상으로 완전히 회복됐다가 다시 떨어지는 경우와,
 *    회복 없이 정지가 풀리자마자 또 매장 취소가 나는 경우를 구분하지 않는다 — 둘 다 똑같이 "다음
 *    위반"으로 취급해서 같은 사다리를 탄다. 이미 정지 중인 동안(같은 위반 사이클)에는 중복으로
 *    단계를 더 올리지 않는다.
 *  - 정지 해제는 별도 스케줄러 없이 동적으로 판정한다 — reliabilitySuspendedUntil이 지난 시각이면
 *    그 자체로 "정지 아님"이다. 정지 단계(reliabilitySuspensionCount)는 해제돼도 리셋되지 않는다.
 *  - 정지/영구정지 상태를 손님에게 보여줄 때도 같은 이유(신뢰도 소문 방지)로 "취소가 반복돼서"
 *    같은 구체적인 사유는 노출하지 않는다 — 그냥 "오늘은 이 매장이 쉬어요"처럼 평범한 휴무
 *    안내처럼 보이게 한다(blockedReservationMessage 참고).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StoreReliabilityService {

	// 신생매장 보호 — 이 건수 미만이면 정지 규칙 자체를 적용하지 않는다.
	public static final int MIN_RESERVATIONS_FOR_PENALTY = 10;

	// 신뢰도(=100-취소율) 이 값 미만이면 위반으로 본다.
	private static final double RELIABILITY_THRESHOLD = 70.0;

	// 신뢰도 계산에 쓰는 "최근 N건" 창 크기. 가입 이후 전체 누적으로 계산하면 한 번 나빠진 매장이
	// 회복이 사실상 불가능해서 최근 예약만 보도록 좁혔다 (ReservationRepository.findRecentByStoreIdAndStatusNot 참고).
	private static final int RELIABILITY_WINDOW_SIZE = 30;

	// 위반 1회차/2회차/3회차 정지 기간(일). 이 배열을 다 쓴 뒤(4회차)엔 영구정지.
	private static final int[] SUSPENSION_DAYS = {7, 14, 30};

	private final ReservationRepository reservationRepository;
	private final StoreRepository storeRepository;

	/**
	 * 매장 신뢰도(취소율) 통계 — 최근 {@value #RELIABILITY_WINDOW_SIZE}건(결제까지 갔던 예약만, pending 제외)
	 * 기준. ReservationService.getStoreCancelStats가 이 메서드로 위임한다 — 기존 화면(매장 상세/
	 * 마이페이지/슈퍼어드민)은 그대로 reservationService.getStoreCancelStats(id)를 호출하면 된다.
	 */
	public StoreCancelStatsDto getStoreCancelStats(Long storeId) {
		List<ReservationEntity> recent = reservationRepository.findRecentByStoreIdAndStatusNot(
				storeId, "pending", PageRequest.of(0, RELIABILITY_WINDOW_SIZE));
		long total = recent.size();
		long storeCancelled = recent.stream().filter(r -> "STORE".equals(r.getCancelledBy())).count();
		double rate = total == 0 ? 0.0 : (storeCancelled * 100.0 / total);
		return new StoreCancelStatsDto(total, storeCancelled, rate);
	}

	/**
	 * 매장이 예약을 취소한(cancelByStore) 직후 호출한다. 신뢰도를 다시 계산해서 70% 밑이면 다음 단계
	 * 정지를 적용한다. storeId가 잘못됐거나 이미 영구정지/정지 중인 매장은 아무것도 하지 않는다.
	 */
	@Transactional
	public void evaluateAfterStoreCancel(Long storeId) {
		StoreEntity store = storeRepository.findById(storeId).orElse(null);
		if (store == null || store.isReliabilityBanned() || isCurrentlySuspended(store)) {
			return;
		}

		StoreCancelStatsDto stats = getStoreCancelStats(storeId);
		if (stats.totalReservationCount() < MIN_RESERVATIONS_FOR_PENALTY) {
			return; // 신생매장 보호
		}

		double reliabilityScore = 100.0 - stats.cancelRatePercent();
		if (reliabilityScore >= RELIABILITY_THRESHOLD) {
			return;
		}

		applyNextSuspensionTier(store);
	}

	private void applyNextSuspensionTier(StoreEntity store) {
		int count = store.getReliabilitySuspensionCount();
		if (count >= SUSPENSION_DAYS.length) {
			store.setReliabilityBanned(true);
			store.setReliabilitySuspendedUntil(null);
			log.warn("> [StoreReliabilityService] 매장 신뢰도 영구정지 - storeId={}", store.getId());
		} else {
			int days = SUSPENSION_DAYS[count];
			store.setReliabilitySuspendedUntil(LocalDateTime.now().plusDays(days));
			store.setReliabilitySuspensionCount(count + 1);
			log.warn("> [StoreReliabilityService] 매장 신뢰도 자동 정지 - storeId={}, {}일, 누적 {}회차",
					store.getId(), days, count + 1);
		}
		storeRepository.save(store);
	}

	/** 신뢰도 사유로 지금 정지 기간 중인지(영구정지는 별개, isBlockedFromNewReservations 참고). */
	public boolean isCurrentlySuspended(StoreEntity store) {
		return store.getReliabilitySuspendedUntil() != null
				&& store.getReliabilitySuspendedUntil().isAfter(LocalDateTime.now());
	}

	/** 새 예약을 막아야 하는지 — 영구정지 또는 신뢰도 정지 기간 중. */
	public boolean isBlockedFromNewReservations(StoreEntity store) {
		return store.isReliabilityBanned() || isCurrentlySuspended(store);
	}

	/**
	 * 예약 차단 시 보여줄 안내 문구. 정지(일시)든 영구정지든 문구를 똑같이 평범한 "휴무" 안내처럼
	 * 보이게 한다 — "취소가 반복돼서" 같은 구체적인 사유를 노출하면 그 자체가 매장 신뢰도에 대한
	 * 소문이 될 수 있고, 그러면 정지가 풀린 뒤에도 신뢰도를 회복하기 더 어려워진다(채채 판단,
	 * 2026-09-16). 이유를 구분해서 보여줄 필요가 없어져서 이제 store 파라미터는 안 쓰지만, 앞으로
	 * (예: 정지 vs 영구정지에 따라 그래도 문구를 살짝 다르게 하고 싶어지는 경우) 다시 필요할 수 있어
	 * 시그니처는 그대로 남겨둔다.
	 */
	public String blockedReservationMessage(StoreEntity store) {
		return "오늘은 이 매장이 예약을 받고 있지 않아요. 다음에 다시 확인해주세요.";
	}
}

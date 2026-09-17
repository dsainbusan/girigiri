package net.dsa.girigiri.service;

import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.MarketingStatsDto;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.repository.ReservationRepository;
import net.dsa.girigiri.repository.StoreRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회사/서비스 소개용 마케팅 홈페이지("/", MarketingController) 전용 서비스 (2026-09-17 추가).
 * 지도 기반 앱 홈(HomeService)과는 화면·목적이 완전히 달라서 별도 서비스로 분리한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MarketingService {

	private final ReservationRepository reservationRepository;
	private final StoreRepository storeRepository;

	public MarketingStatsDto getImpactStats() {
		return MarketingStatsDto.builder()
				.rescuedCount(reservationRepository.countByStatus("picked"))
				.storeCount(storeRepository.countByApprovalStatus(StoreEntity.STATUS_APPROVED))
				.build();
	}
}

package net.dsa.girigiri.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.entity.ComplaintEntity;
import net.dsa.girigiri.domain.entity.ReservationEntity;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.domain.entity.UserEntity;
import net.dsa.girigiri.repository.ComplaintRepository;
import net.dsa.girigiri.repository.StoreRepository;
import net.dsa.girigiri.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 손님이 예약 건에 대해 신고를 접수하는 로직 — 2026-09-08 신설.
 *
 * 지금까지 신고(ComplaintEntity)는 슈퍼어드민이 처리하는 것만 있고(SuperAdminSupportService),
 * 소비자가 실제로 신고를 "제출"하는 화면·서비스가 없었다 — DB에 있던 신고 데이터는 전부
 * sql/sample-data.sql로 넣은 시드였다. 이 서비스가 그 제출 경로를 담당한다.
 *
 * ReservationController(예약 상세/내역, 손님 쪽)에서 호출한다 — SuperAdminSupportService에 넣지
 * 않은 이유는 그건 "슈퍼어드민이 신고를 처리하는" 도메인이고, 이건 "손님이 신고를 접수하는" 별개
 * 도메인이라 이름이 맞지 않는다(둘 다 ComplaintRepository를 쓰지만 하는 일이 다르다).
 */
@Service
@RequiredArgsConstructor
public class ComplaintService {

	private final ComplaintRepository complaintRepository;
	private final UserRepository userRepository;
	private final StoreRepository storeRepository;

	/**
	 * 예약 상세의 "신고하기" 버튼 제출 — 신고 대상(매장)과 신고와 관련된 예약을 자동으로 채운다.
	 * 예약이 본인 것인지는 컨트롤러(ReservationController)가 이미 확인했다고 가정한다 — 다른
	 * 컨트롤러의 취소/영수증 액션과 동일한 위치에서 소유 확인을 하는 기존 관례를 따른다.
	 */
	@Transactional
	public ComplaintEntity submitFromReservation(ReservationEntity reservation, String reason, String content) {
		UserEntity reporter = userRepository.findById(reservation.getUserId())
				.orElseThrow(() -> new EntityNotFoundException("회원을 찾을 수 없습니다. id=" + reservation.getUserId()));
		StoreEntity store = storeRepository.findById(reservation.getStoreId()).orElse(null);

		ComplaintEntity complaint = ComplaintEntity.builder()
				.reporterId(reporter.getId())
				.reporterName(reporter.getNickname())
				.targetStoreId(reservation.getStoreId())
				.targetName(store != null ? store.getStoreName() : "알 수 없는 매장")
				.targetReservationId(reservation.getId())
				.reason(reason == null ? "" : reason.trim())
				.content(content == null ? "" : content.trim())
				.build();

		return complaintRepository.save(complaint);
	}
}

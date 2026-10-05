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

import java.time.Duration;
import java.time.LocalDateTime;

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

	// 픽업 후 이 시간(시간 단위)이 지나면 신고 접수를 막는다 — "신고 가능 시간은 48시간" 확정(2026-10-06).
	private static final int REPORT_WINDOW_HOURS = 48;

	/**
	 * 주문(예약) 기반 신고 접수 자격을 판정한다 — 접수 가능하면 null, 아니면 이유 메시지.
	 * blockedCancelMessage(ReservationService)와 동일한 패턴: 화면에서 [신고하기] 버튼을 숨기는
	 * 것과(ReservationController#myReservations), 실제 제출을 막는 것(ReservationReportController)
	 * 둘 다 이 메서드 하나로 판정해서, 한쪽만 고치고 다른 쪽을 빠뜨리는 일을 막는다.
	 *
	 * 조건(스펙 A.3): 픽업완료 상태일 것 / 픽업 후 48시간 이내일 것 / 이 주문에 처리 중(PENDING)인
	 * 신고가 이미 없을 것. "본인 주문인지"는 호출부(컨트롤러)가 세션 userId와 reservation.userId를
	 * 비교해서 이미 걸러주는 소유권 검증 영역이라 여기서는 다루지 않는다(기존 관례).
	 */
	public String blockedReportMessage(ReservationEntity reservation) {
		if (!"picked".equals(reservation.getStatus())) {
			return "픽업 완료된 주문만 신고할 수 있어요.";
		}
		if (reservation.getPickedAt() == null
				|| Duration.between(reservation.getPickedAt(), LocalDateTime.now()).toHours() >= REPORT_WINDOW_HOURS) {
			return "픽업 후 " + REPORT_WINDOW_HOURS + "시간이 지나 신고할 수 없어요.";
		}
		if (complaintRepository.existsByTargetReservationIdAndStatus(reservation.getId(), ComplaintEntity.STATUS_PENDING)) {
			return "이미 신고가 접수됐어요. 운영자 처리를 기다려주세요.";
		}
		return null;
	}

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

package net.dsa.girigiri;

import net.dsa.girigiri.domain.entity.ComplaintEntity;
import net.dsa.girigiri.domain.entity.ReservationEntity;
import net.dsa.girigiri.repository.ComplaintRepository;
import net.dsa.girigiri.service.ComplaintService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * ComplaintService.blockedReportMessage(스펙 A.3 신고 접수 자격) 테스트. "신고 가능 시간은
 * 48시간" 확정(2026-10-06) 반영. 신고 테이블에만 행을 추가하므로(reservation은 가짜 id의 transient
 * 객체) @Transactional로 테스트 후 롤백한다.
 */
@SpringBootTest
@Transactional
class ComplaintReportEligibilityTest {

	@Autowired
	private ComplaintService complaintService;
	@Autowired
	private ComplaintRepository complaintRepository;

	private static final long FAKE_RESERVATION_ID = 987654321L;

	private ReservationEntity reservation(String status, LocalDateTime pickedAt) {
		return ReservationEntity.builder()
				.id(FAKE_RESERVATION_ID)
				.status(status)
				.pickedAt(pickedAt)
				.build();
	}

	@Test
	void 픽업완료_상태가_아니면_신고할_수_없다() {
		assertNotNull(complaintService.blockedReportMessage(reservation("ready", null)));
	}

	@Test
	void 픽업후_48시간이_지나면_신고할_수_없다() {
		ReservationEntity r = reservation("picked", LocalDateTime.now().minusHours(49));
		assertNotNull(complaintService.blockedReportMessage(r));
	}

	@Test
	void 픽업후_48시간_이내면_신고할_수_있다() {
		ReservationEntity r = reservation("picked", LocalDateTime.now().minusHours(47));
		assertNull(complaintService.blockedReportMessage(r));
	}

	@Test
	void 이미_처리중인_신고가_있으면_중복_신고할_수_없다() {
		ReservationEntity r = reservation("picked", LocalDateTime.now().minusHours(1));
		complaintRepository.save(ComplaintEntity.builder()
				.targetName("다이스키 베이커리")
				.targetReservationId(FAKE_RESERVATION_ID)
				.reason("상품 상태 불량")
				.content("먼저 접수된 신고")
				.reporterName("테스트유저")
				.build());

		assertNotNull(complaintService.blockedReportMessage(r));
	}

	@Test
	void 처리완료된_신고만_있으면_재신고할_수_있다() {
		ReservationEntity r = reservation("picked", LocalDateTime.now().minusHours(1));
		complaintRepository.save(ComplaintEntity.builder()
				.targetName("다이스키 베이커리")
				.targetReservationId(FAKE_RESERVATION_ID)
				.reason("상품 상태 불량")
				.content("이미 처리된 신고")
				.reporterName("테스트유저")
				.status(ComplaintEntity.STATUS_RESOLVED)
				.build());

		assertNull(complaintService.blockedReportMessage(r));
	}
}

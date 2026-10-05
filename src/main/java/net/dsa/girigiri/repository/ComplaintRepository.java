package net.dsa.girigiri.repository;

import net.dsa.girigiri.domain.entity.ComplaintEntity;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ComplaintRepository extends JpaRepository<ComplaintEntity, Long> {
	// 회원 상세 화면의 "문의·신고 내역"에 쓴다 — 이 회원이 신고자인 신고 목록.
	List<ComplaintEntity> findByReporterId(Long reporterId, Sort sort);

	// 추가됨 (2026-10-06) — "이 주문에 처리 중인 신고가 이미 있는가"(중복 신고 방지, ComplaintService
	// 참고) 판정용. 같은 주문으로 여러 번 신고를 접수하는 걸 막되, 한 번 처리완료(RESOLVED)된 뒤
	// 다시 문제가 생기면 재신고는 허용한다 — 그래서 status까지 같이 본다.
	boolean existsByTargetReservationIdAndStatus(Long targetReservationId, String status);
}

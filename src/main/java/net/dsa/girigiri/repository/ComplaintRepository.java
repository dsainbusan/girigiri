package net.dsa.girigiri.repository;

import net.dsa.girigiri.domain.entity.ComplaintEntity;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ComplaintRepository extends JpaRepository<ComplaintEntity, Long> {
	// 회원 상세 화면의 "문의·신고 내역"에 쓴다 — 이 회원이 신고자인 신고 목록.
	List<ComplaintEntity> findByReporterId(Long reporterId, Sort sort);
}

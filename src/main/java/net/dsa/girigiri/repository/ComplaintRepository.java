package net.dsa.girigiri.repository;

import net.dsa.girigiri.domain.entity.ComplaintEntity;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ComplaintRepository extends JpaRepository<ComplaintEntity, Long> {
	// 회원 상세 화면의 "문의·신고 내역"에 쓴다 — 이 회원이 신고자인 신고 목록.
	List<ComplaintEntity> findByReporterId(Long reporterId, Sort sort);

	// 추가됨 (2026-10-06) — 슈퍼어드민 공통 상단바(SuperAdminNotificationService)가 모든 화면에서
	// 매 요청마다 "처리 대기 N건(신고 N)"을 보여주려면 findAll() 전체 로드가 아니라 COUNT 쿼리여야 한다.
	long countByStatus(String status);
}

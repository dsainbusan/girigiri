package net.dsa.girigiri.repository;

import net.dsa.girigiri.domain.entity.InquiryEntity;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InquiryRepository extends JpaRepository<InquiryEntity, Long> {
	// 회원 상세 화면의 "문의·신고 내역"에 쓴다 — 이 회원이 작성자인 문의 목록.
	List<InquiryEntity> findByUserId(Long userId, Sort sort);

	// 추가됨 (2026-10-06) — 슈퍼어드민 공통 상단바(SuperAdminNotificationService)용. "답변 없는 문의
	// 수"는 SuperAdminDashboardService#buildPendingQueue가 하듯 findAll() + 메모리 필터링하면
	// 매 요청(모든 화면)마다 전체 문의/댓글을 읽어오게 돼 무겁다 — NOT EXISTS 서브쿼리로 DB에서
	// 바로 COUNT한다.
	@Query("select count(i) from InquiryEntity i where not exists "
			+ "(select 1 from InquiryCommentEntity c where c.inquiryId = i.id)")
	long countPending();
}

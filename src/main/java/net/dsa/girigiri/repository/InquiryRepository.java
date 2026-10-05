package net.dsa.girigiri.repository;

import net.dsa.girigiri.domain.entity.InquiryEntity;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InquiryRepository extends JpaRepository<InquiryEntity, Long> {
	// 회원 상세 화면의 "문의·신고 내역"에 쓴다 — 이 회원이 작성자인 문의 목록.
	List<InquiryEntity> findByUserId(Long userId, Sort sort);
}

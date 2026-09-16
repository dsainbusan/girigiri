package net.dsa.girigiri.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.entity.NoticeEntity;
import net.dsa.girigiri.repository.NoticeRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 점주 화면에서 슈퍼어드민 공지사항을 읽기 전용으로 보여주는 조회 서비스
 * (WBS 3.0 "공지사항 게시판 관리", 원래 김태훈 담당 → 문창호가 인수).
 *
 * 작성/수정/삭제는 그대로 슈퍼어드민 전용(NoticeEntity/NoticeRepository/SuperAdminNoticeService,
 * 송보미 담당)이라 건드리지 않는다 — 여기서는 지금 실제로 게시중(PUBLISHED)인 것만 걸러서 보여준다.
 */
@Service
@RequiredArgsConstructor
public class StoreNoticeService {

	private final NoticeRepository noticeRepository;

	@Transactional(readOnly = true)
	public List<NoticeEntity> findActiveSortedByNewest() {
		return noticeRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
				.filter(notice -> NoticeEntity.STATUS_PUBLISHED.equals(notice.getDisplayStatus()))
				.toList();
	}

	@Transactional(readOnly = true)
	public NoticeEntity findActiveById(Long id) {
		return noticeRepository.findById(id)
				.filter(notice -> NoticeEntity.STATUS_PUBLISHED.equals(notice.getDisplayStatus()))
				.orElseThrow(() -> new EntityNotFoundException("공지사항을 찾을 수 없습니다: " + id));
	}
}

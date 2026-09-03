package net.dsa.girigiri.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.entity.NoticeEntity;
import net.dsa.girigiri.repository.NoticeRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 슈퍼어드민 "공지사항 관리" 도메인 서비스 (2026-09-03, 레이어 규칙 2단계 — SuperAdminController 도메인 분리).
 *
 * SuperAdminController의 공지사항 관련 Repository 직접 호출·검증·상태 변경 로직을 옮겨온다.
 */
@Service
@RequiredArgsConstructor
public class SuperAdminNoticeService {

	public enum SaveResult { SUCCESS, INVALID, INVALID_PERIOD }

	private final NoticeRepository noticeRepository;
	private final LookupService lookupService;

	@Transactional(readOnly = true)
	public List<NoticeEntity> findAllSortedByNewest() {
		return noticeRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
	}

	/**
	 * "게시 기간을 정하고 싶다"는 요청 — 시작일/종료일(둘 다 선택, 비우면 무제한)을 같이 받는다.
	 * 종료일이 시작일보다 빠르면 저장 자체를 막는다(사용자 실수 방지).
	 */
	@Transactional
	public SaveResult create(String title, String content, String publishStartAt, String publishEndAt) {
		if (title == null || title.isBlank() || content == null || content.isBlank()) {
			return SaveResult.INVALID;
		}

		LocalDate startAt = parseNoticeDate(publishStartAt);
		LocalDate endAt = parseNoticeDate(publishEndAt);
		if (startAt != null && endAt != null && endAt.isBefore(startAt)) {
			return SaveResult.INVALID_PERIOD;
		}

		NoticeEntity notice = NoticeEntity.builder()
				.title(title.trim())
				.content(content.trim())
				.published(true)
				.publishStartAt(startAt)
				.publishEndAt(endAt)
				.build();
		noticeRepository.save(notice);

		return SaveResult.SUCCESS;
	}

	@Transactional
	public SaveResult update(Long id, String title, String content, Boolean published,
	                          String publishStartAt, String publishEndAt) {
		NoticeEntity notice = lookupService.getNotice(id);

		if (title == null || title.isBlank() || content == null || content.isBlank()) {
			return SaveResult.INVALID;
		}

		LocalDate startAt = parseNoticeDate(publishStartAt);
		LocalDate endAt = parseNoticeDate(publishEndAt);
		if (startAt != null && endAt != null && endAt.isBefore(startAt)) {
			return SaveResult.INVALID_PERIOD;
		}

		notice.setTitle(title.trim());
		notice.setContent(content.trim());
		notice.setPublished(Boolean.TRUE.equals(published));
		notice.setPublishStartAt(startAt);
		notice.setPublishEndAt(endAt);
		noticeRepository.save(notice);

		return SaveResult.SUCCESS;
	}

	/**
	 * "게시글 내리기"를 수정 화면까지 안 들어가고 상세에서 바로 할 수 있게 한다 — 즉시 토글.
	 */
	@Transactional
	public void setPublished(Long id, boolean published) {
		NoticeEntity notice = lookupService.getNotice(id);
		notice.setPublished(published);
		noticeRepository.save(notice);
	}

	@Transactional
	public void delete(Long id) {
		if (!noticeRepository.existsById(id)) {
			throw new EntityNotFoundException("공지사항을 찾을 수 없습니다: " + id);
		}
		noticeRepository.deleteById(id);
	}

	private LocalDate parseNoticeDate(String value) {
		return value == null || value.isBlank() ? null : LocalDate.parse(value);
	}
}

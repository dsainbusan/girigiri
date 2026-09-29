package net.dsa.girigiri.service;

import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.entity.StoreAnnouncementEntity;
import net.dsa.girigiri.repository.StoreAnnouncementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 점주가 자기 매장 공지(StoreAnnouncementEntity)를 쓰고 손님이 읽는 기능의 서비스 레이어.
 * 2026-09-29 신규 — SuperAdminNoticeService(슈퍼어드민 플랫폼 공지)의 SaveResult 반환 패턴을
 * 그대로 따른다.
 */
@Service
@RequiredArgsConstructor
public class StoreAnnouncementService {

	private final StoreAnnouncementRepository storeAnnouncementRepository;

	public enum SaveResult { SUCCESS, INVALID }

	@Transactional
	public List<StoreAnnouncementEntity> getForStore(Long storeId) {
		List<StoreAnnouncementEntity> list = storeAnnouncementRepository.findByStoreIdOrderByCreatedAtDesc(storeId);
		if (!list.isEmpty()) {
			boolean hasExposed = list.stream().anyMatch(StoreAnnouncementEntity::isExposed);
			if (!hasExposed) {
				list.get(0).setExposed(true);
				storeAnnouncementRepository.save(list.get(0));
			}
		}
		return list;
	}

	@Transactional(readOnly = true)
	public long countForStore(Long storeId) {
		return storeAnnouncementRepository.countByStoreId(storeId);
	}

	// 매장 상세 페이지(손님용)에 노출할 공지 1건을 반환한다.
	// 점주가 노출 중으로 지정한 것을 우선 반환하고, 없으면 가장 최신 공지 1건을 폴백한다.
	@Transactional(readOnly = true)
	public List<StoreAnnouncementEntity> getExposedForConsumer(Long storeId) {
		List<StoreAnnouncementEntity> all = storeAnnouncementRepository.findByStoreIdOrderByCreatedAtDesc(storeId);
		if (all.isEmpty()) {
			return List.of();
		}
		for (StoreAnnouncementEntity a : all) {
			if (a.isExposed()) {
				return List.of(a);
			}
		}
		return List.of(all.get(0));
	}

	@Transactional(readOnly = true)
	public List<StoreAnnouncementEntity> getRecentForConsumer(Long storeId, int limit) {
		return getExposedForConsumer(storeId);
	}

	@Transactional(readOnly = true)
	public Optional<StoreAnnouncementEntity> findByIdForStore(Long id, Long storeId) {
		return storeAnnouncementRepository.findByIdAndStoreId(id, storeId);
	}

	@Transactional
	public SaveResult create(Long storeId, String title, String content) {
		if (isBlank(title) || isBlank(content)) {
			return SaveResult.INVALID;
		}
		// 새로 등록한 공지를 노출 중으로 설정하고 기존 공지들은 노출 해제
		List<StoreAnnouncementEntity> all = storeAnnouncementRepository.findByStoreIdOrderByCreatedAtDesc(storeId);
		for (StoreAnnouncementEntity a : all) {
			a.setExposed(false);
		}
		storeAnnouncementRepository.save(StoreAnnouncementEntity.builder()
				.storeId(storeId)
				.title(title.trim())
				.content(content.trim())
				.exposed(true)
				.build());
		return SaveResult.SUCCESS;
	}

	// 점주가 특정 공지를 손님 화면에 노출하도록 대표 지정
	@Transactional
	public boolean setExposed(Long announcementId, Long storeId) {
		List<StoreAnnouncementEntity> all = storeAnnouncementRepository.findByStoreIdOrderByCreatedAtDesc(storeId);
		boolean found = false;
		for (StoreAnnouncementEntity a : all) {
			if (a.getId().equals(announcementId)) {
				a.setExposed(true);
				found = true;
			} else {
				a.setExposed(false);
			}
		}
		return found;
	}

	// id가 정말 이 매장(storeId) 소유인지 findByIdForStore로 먼저 확인한 뒤 호출부가 update/delete를
	// 부르는 구조 — 여기서는 넘어온 엔티티를 그대로 신뢰한다(컨트롤러가 소유권 확인 후 넘긴다).
	@Transactional
	public SaveResult update(StoreAnnouncementEntity announcement, String title, String content) {
		if (isBlank(title) || isBlank(content)) {
			return SaveResult.INVALID;
		}
		announcement.setTitle(title.trim());
		announcement.setContent(content.trim());
		storeAnnouncementRepository.save(announcement);
		return SaveResult.SUCCESS;
	}

	@Transactional
	public void delete(StoreAnnouncementEntity announcement) {
		boolean wasExposed = announcement.isExposed();
		Long storeId = announcement.getStoreId();
		storeAnnouncementRepository.delete(announcement);
		if (wasExposed) {
			List<StoreAnnouncementEntity> remaining = storeAnnouncementRepository.findByStoreIdOrderByCreatedAtDesc(storeId);
			if (!remaining.isEmpty()) {
				remaining.get(0).setExposed(true);
			}
		}
	}

	private boolean isBlank(String s) {
		return s == null || s.isBlank();
	}
}

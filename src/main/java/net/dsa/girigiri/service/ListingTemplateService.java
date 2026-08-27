package net.dsa.girigiri.service;

import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.entity.ListingTemplateEntity;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.repository.ListingTemplateRepository;
import net.dsa.girigiri.repository.StoreRepository;
import net.dsa.girigiri.util.FileStorageUtil;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * "오늘의 구제 자동 등록" 템플릿 CRUD — 2026-08-26 신규 (문창호).
 *
 * 템플릿 사진은 상품 사진과 다른 하위 폴더(upload/template/)에 저장한다 — 초안이 published 되면
 * 그 사진을 상품이 공유하게 되는데, 상품 쪽 삭제/교체 로직(FileStorageUtil.deleteIfOwned(x, "product"))이
 * 실수로 템플릿 원본을 지우지 않도록 경로 접두사로 분리해두는 것.
 */
@Service
@RequiredArgsConstructor
public class ListingTemplateService {

	public static final String IMAGE_SUBDIR = "template";

	private final ListingTemplateRepository templateRepository;
	private final StoreRepository storeRepository;
	private final FileStorageUtil fileStorageUtil;

	public List<ListingTemplateEntity> listForOwner(Long ownerId) {
		return templateRepository.findByStoreId(requireStore(ownerId).getId());
	}

	public ListingTemplateEntity getOwned(Long ownerId, Long templateId) {
		return requireOwned(requireStore(ownerId), templateId);
	}

	@Transactional
	public Long create(Long ownerId, String name, Integer originalPrice, Integer defaultQuantity,
	                   List<Integer> weekdays, String promptTime, String description, MultipartFile image) {
		StoreEntity store = requireStore(ownerId);
		validate(name, originalPrice, defaultQuantity, weekdays, promptTime);

		ListingTemplateEntity template = ListingTemplateEntity.builder()
				.storeId(store.getId())
				.name(name.trim())
				.originalPrice(originalPrice)
				.defaultQuantity(defaultQuantity)
				.weekdays(toWeekdayCsv(weekdays))
				.promptTime(LocalTime.parse(promptTime))
				.description(blankToNull(description))
				.imageUrl(fileStorageUtil.store(image, IMAGE_SUBDIR))
				.active(true)
				.build();
		templateRepository.save(template);
		return template.getId();
	}

	@Transactional
	public void update(Long ownerId, Long templateId, String name, Integer originalPrice, Integer defaultQuantity,
	                   List<Integer> weekdays, String promptTime, String description, MultipartFile image) {
		StoreEntity store = requireStore(ownerId);
		ListingTemplateEntity template = requireOwned(store, templateId);
		validate(name, originalPrice, defaultQuantity, weekdays, promptTime);

		template.setName(name.trim());
		template.setOriginalPrice(originalPrice);
		template.setDefaultQuantity(defaultQuantity);
		template.setWeekdays(toWeekdayCsv(weekdays));
		template.setPromptTime(LocalTime.parse(promptTime));
		template.setDescription(blankToNull(description));

		if (image != null && !image.isEmpty()) {
			String previous = template.getImageUrl();
			template.setImageUrl(fileStorageUtil.store(image, IMAGE_SUBDIR));
			fileStorageUtil.deleteIfOwned(previous, IMAGE_SUBDIR);
		}
		templateRepository.save(template);
	}

	@Transactional
	public void toggleActive(Long ownerId, Long templateId) {
		ListingTemplateEntity template = requireOwned(requireStore(ownerId), templateId);
		template.setActive(!template.isActive());
		templateRepository.save(template);
	}

	@Transactional
	public void delete(Long ownerId, Long templateId) {
		ListingTemplateEntity template = requireOwned(requireStore(ownerId), templateId);
		// 사진은 이미 published된 상품이 공유 중일 수 있어서 지우지 않는다 (경로 접두사가 다르므로
		// 상품 쪽 정리 로직도 이 파일을 건드리지 않는다 — 고아 파일로 남지만 랜덤명이라 충돌 없음).
		templateRepository.delete(template);
	}

	// ---------------------------------------------------------------------

	private StoreEntity requireStore(Long ownerId) {
		if (ownerId == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요해요.");
		}
		return storeRepository.findByOwnerId(ownerId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "등록된 매장이 없어요."));
	}

	private ListingTemplateEntity requireOwned(StoreEntity store, Long templateId) {
		ListingTemplateEntity template = templateRepository.findById(templateId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "템플릿을 찾을 수 없어요."));
		if (!store.getId().equals(template.getStoreId())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "다른 매장의 템플릿은 건드릴 수 없어요.");
		}
		return template;
	}

	private void validate(String name, Integer originalPrice, Integer defaultQuantity,
	                      List<Integer> weekdays, String promptTime) {
		if (name == null || name.isBlank()
				|| originalPrice == null || originalPrice <= 0
				|| defaultQuantity == null || defaultQuantity <= 0
				|| weekdays == null || weekdays.isEmpty()
				|| promptTime == null || promptTime.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "필수 항목을 모두 올바르게 입력해 주세요.");
		}
		try {
			LocalTime.parse(promptTime);
		} catch (Exception e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "시각 형식이 올바르지 않아요.");
		}
	}

	private String toWeekdayCsv(List<Integer> weekdays) {
		return weekdays.stream().distinct().sorted().map(String::valueOf).collect(Collectors.joining(","));
	}

	public static boolean weekdayMatches(String weekdayCsv, int isoDayOfWeek) {
		if (weekdayCsv == null || weekdayCsv.isBlank()) {
			return false;
		}
		return Stream.of(weekdayCsv.split(",")).map(String::trim).anyMatch(d -> d.equals(String.valueOf(isoDayOfWeek)));
	}

	private String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}
}

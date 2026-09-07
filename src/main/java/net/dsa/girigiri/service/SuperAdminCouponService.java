package net.dsa.girigiri.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.CouponCampaignRowDto;
import net.dsa.girigiri.domain.entity.CouponCampaignEntity;
import net.dsa.girigiri.repository.CouponCampaignRepository;
import net.dsa.girigiri.repository.CouponRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * 슈퍼어드민 "프로모션 쿠폰 캠페인 관리" 도메인 서비스 — 2026-09-07 신규 (송채현).
 * SuperAdminNoticeService(공지사항 관리)와 동일한 패턴을 따른다: enum 결과 + Repository 직접 호출.
 */
@Service
@RequiredArgsConstructor
public class SuperAdminCouponService {

	public enum SaveResult { SUCCESS, INVALID, DUPLICATE_CODE }

	private static final DateTimeFormatter DATE_LABEL = DateTimeFormatter.ofPattern("yyyy-MM-dd");

	private final CouponCampaignRepository campaignRepository;
	private final CouponRepository couponRepository;

	@Transactional(readOnly = true)
	public List<CouponCampaignRowDto> listAllSortedByNewest() {
		LocalDateTime now = LocalDateTime.now();
		return campaignRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
				.map(c -> toRow(c, now))
				.toList();
	}

	@Transactional
	public SaveResult create(String name, String code, Integer discountRate, String expiresAtDate) {
		String normalizedCode = normalizeCode(code);
		LocalDateTime expiresAt = parseExpiresAtEndOfDay(expiresAtDate);

		if (name == null || name.isBlank()
				|| normalizedCode.isBlank() || normalizedCode.length() > 30
				|| discountRate == null || discountRate < 1 || discountRate > 90
				|| expiresAt == null || !expiresAt.isAfter(LocalDateTime.now())) {
			return SaveResult.INVALID;
		}
		if (campaignRepository.existsByCode(normalizedCode)) {
			return SaveResult.DUPLICATE_CODE;
		}

		CouponCampaignEntity campaign = CouponCampaignEntity.builder()
				.name(name.trim())
				.code(normalizedCode)
				.discountRate(discountRate)
				.expiresAt(expiresAt)
				.active(true)
				.build();
		campaignRepository.save(campaign);
		return SaveResult.SUCCESS;
	}

	@Transactional
	public void toggleActive(Long id) {
		CouponCampaignEntity campaign = getOwned(id);
		campaign.setActive(!campaign.isActive());
		campaignRepository.save(campaign);
	}

	@Transactional
	public void delete(Long id) {
		if (!campaignRepository.existsById(id)) {
			throw new EntityNotFoundException("캠페인을 찾을 수 없습니다: " + id);
		}
		campaignRepository.deleteById(id);
	}

	/** CouponController(회원용 "코드로 쿠폰 받기")에서 코드로 캠페인을 찾을 때 쓴다. */
	@Transactional(readOnly = true)
	public CouponCampaignEntity findByCode(String code) {
		return campaignRepository.findByCode(normalizeCode(code))
				.orElseThrow(() -> new EntityNotFoundException("존재하지 않는 코드예요."));
	}

	// ---------------------------------------------------------------------

	private CouponCampaignEntity getOwned(Long id) {
		return campaignRepository.findById(id)
				.orElseThrow(() -> new EntityNotFoundException("캠페인을 찾을 수 없습니다: " + id));
	}

	private String normalizeCode(String code) {
		return code == null ? "" : code.trim().toUpperCase();
	}

	private LocalDateTime parseExpiresAtEndOfDay(String dateStr) {
		if (dateStr == null || dateStr.isBlank()) {
			return null;
		}
		try {
			return LocalDate.parse(dateStr.trim()).atTime(23, 59, 59);
		} catch (DateTimeParseException e) {
			return null;
		}
	}

	private CouponCampaignRowDto toRow(CouponCampaignEntity c, LocalDateTime now) {
		boolean expired = !c.getExpiresAt().isAfter(now);
		String statusLabel;
		if (expired) {
			statusLabel = "기한 만료";
		} else if (!c.isActive()) {
			statusLabel = "비활성";
		} else {
			statusLabel = "진행중";
		}
		return CouponCampaignRowDto.builder()
				.id(c.getId())
				.name(c.getName())
				.code(c.getCode())
				.discountRate(c.getDiscountRate())
				.expiresAtLabel(c.getExpiresAt().toLocalDate().format(DATE_LABEL))
				.active(c.isActive())
				.expired(expired)
				.claimedCount(couponRepository.countByCampaignId(c.getId()))
				.statusLabel(statusLabel)
				.build();
	}
}

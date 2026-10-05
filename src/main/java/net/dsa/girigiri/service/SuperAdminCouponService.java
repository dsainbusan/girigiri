package net.dsa.girigiri.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.CouponCampaignRowDto;
import net.dsa.girigiri.domain.entity.CouponCampaignEntity;
import net.dsa.girigiri.domain.entity.CouponStoreEntity;
import net.dsa.girigiri.domain.entity.LikeEntity;
import net.dsa.girigiri.domain.entity.NotificationEntity;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.domain.entity.UserEntity;
import net.dsa.girigiri.repository.CouponCampaignRepository;
import net.dsa.girigiri.repository.CouponRepository;
import net.dsa.girigiri.repository.CouponStoreRepository;
import net.dsa.girigiri.repository.LikeRepository;
import net.dsa.girigiri.repository.StoreRepository;
import net.dsa.girigiri.repository.UserRepository;
import net.dsa.girigiri.util.SidoParser;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 슈퍼어드민 "프로모션 쿠폰 캠페인 관리" 도메인 서비스 — 2026-09-07 신규 (송채현).
 * SuperAdminNoticeService(공지사항 관리)와 동일한 패턴을 따른다: enum 결과 + Repository 직접 호출.
 *
 * 2026-10-01 확장 — 지역별 현황 드릴다운의 "매장 지정 쿠폰 발행"(createStoreCampaign)을 추가했다.
 * 기존 코드형 캠페인(create/toggleActive/delete/findByCode)은 전혀 안 건드렸다.
 */
@Service
@RequiredArgsConstructor
public class SuperAdminCouponService {

	public enum SaveResult { SUCCESS, INVALID, DUPLICATE_CODE }

	public enum StoreCampaignResult { SUCCESS, INVALID }

	private static final DateTimeFormatter DATE_LABEL = DateTimeFormatter.ofPattern("yyyy-MM-dd");
	private static final int MAX_RATE = 90;

	private final CouponCampaignRepository campaignRepository;
	private final CouponRepository couponRepository;
	// 추가됨 (2026-10-01, 매장 지정 쿠폰) — createStoreCampaign 전용 의존성.
	private final CouponStoreRepository couponStoreRepository;
	private final StoreRepository storeRepository;
	private final LikeRepository likeRepository;
	private final UserRepository userRepository;
	private final NotificationService notificationService;

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
				.discountType(CouponCampaignEntity.DISCOUNT_TYPE_RATE)
				.discountRate(discountRate)
				.expiresAt(expiresAt)
				.active(true)
				.build();
		campaignRepository.save(campaign);
		return SaveResult.SUCCESS;
	}

	/**
	 * 매장 지정 쿠폰 발행 — 2026-10-01 신규(통계 대시보드 "지역별 현황" → 매장 목록 드릴다운).
	 * 코드 입력 없이 대상 매장의 손님용 상세 페이지 "쿠폰 받기" 버튼으로 받는 캠페인(scope=STORE)을
	 * 만들고, coupon_store에 대상 매장을 연결한다. 비용은 전부 플랫폼 부담(FUNDED_BY_PLATFORM)이라
	 * 정산 로직은 건드리지 않는다. REGION 발행은 이 메서드로 들어오지 않는다(컨트롤러가 STORE로 고정).
	 */
	@Transactional
	public StoreCampaignResult createStoreCampaign(List<Long> storeIds, String discountType, Integer discountRate,
	                                                Integer discountAmount, Integer maxDiscountAmount,
	                                                Integer minOrderAmount, int validDays,
	                                                String reason, Long adminId,
	                                                boolean notifyLiked, boolean notifySameRegion) {
		if (storeIds == null || storeIds.isEmpty() || validDays < 1) {
			return StoreCampaignResult.INVALID;
		}
		boolean isRate = CouponCampaignEntity.DISCOUNT_TYPE_RATE.equals(discountType);
		boolean isAmount = CouponCampaignEntity.DISCOUNT_TYPE_AMOUNT.equals(discountType);
		if (!isRate && !isAmount) {
			return StoreCampaignResult.INVALID;
		}
		if (isRate && (discountRate == null || discountRate < 1 || discountRate > MAX_RATE)) {
			return StoreCampaignResult.INVALID;
		}
		if (isAmount && (discountAmount == null || discountAmount < 1)) {
			return StoreCampaignResult.INVALID;
		}

		List<StoreEntity> stores = storeRepository.findAllById(storeIds);
		if (stores.isEmpty()) {
			return StoreCampaignResult.INVALID;
		}

		// 발행 수량 상한 = 대상 매장을 찜한 손님 수(중복 제거) — 운영자가 임의 숫자를 입력하지 않는다
		// (2026-10-01, 조장 확인). 클라이언트가 모달에서 미리 본 숫자를 그대로 신뢰하지 않고 여기서
		// 다시 계산한다 — 그 사이 찜이 바뀌었을 수도 있고, 애초에 서버 값만 믿어야 한다.
		int issueLimit = countDistinctLikedCustomers(storeIds);
		if (issueLimit == 0) {
			return StoreCampaignResult.INVALID;
		}

		CouponCampaignEntity campaign = CouponCampaignEntity.builder()
				.name("매장 지정 쿠폰 · " + LocalDate.now().format(DATE_LABEL) + " (" + stores.size() + "곳)")
				.scope(CouponCampaignEntity.SCOPE_STORE)
				.discountType(discountType)
				.discountRate(isRate ? discountRate : null)
				.discountAmount(isAmount ? discountAmount : null)
				.maxDiscountAmount(isRate ? maxDiscountAmount : null)
				.minOrderAmount(minOrderAmount)
				.issueLimit(issueLimit)
				.fundedBy(CouponCampaignEntity.FUNDED_BY_PLATFORM)
				.issuedByAdminId(adminId)
				.issueReason(reason != null && !reason.isBlank() ? reason.trim() : null)
				.expiresAt(LocalDateTime.now().plusDays(validDays))
				.active(true)
				.build();
		campaignRepository.save(campaign);

		for (StoreEntity store : stores) {
			couponStoreRepository.save(CouponStoreEntity.builder()
					.campaignId(campaign.getId())
					.storeId(store.getId())
					.build());
		}

		if (notifyLiked || notifySameRegion) {
			notifyCustomers(campaign, stores, notifyLiked, notifySameRegion);
		}
		return StoreCampaignResult.SUCCESS;
	}

	/**
	 * 대상 매장을 찜한 손님 수(중복 제거). 쿠폰 발행 모달의 "발행 수량 상한" 미리보기와 실제 발행
	 * 양쪽에서 쓴다(미리보기는 SuperAdminRegionController가 호출, 실제 발행 값은 여기서 다시 계산).
	 */
	@Transactional(readOnly = true)
	public int countDistinctLikedCustomers(List<Long> storeIds) {
		if (storeIds == null || storeIds.isEmpty()) {
			return 0;
		}
		return (int) storeIds.stream()
				.flatMap(storeId -> likeRepository.findByStoreId(storeId).stream())
				.map(LikeEntity::getUserId)
				.distinct()
				.count();
	}

	/**
	 * 발행 직후 알림 발송 — 2026-10-01 분리(피드백 반영). "대상 매장을 찜한 손님"은 매장마다 그
	 * 매장 이름을 넣어 따로 보내고, "같은 시도 손님"은 선택한 매장 전체를 한 번에 묶어서 딱 1통만
	 * 보낸다 — 원래는 매장별 루프 안에서 같이 처리해서, 같은 시도에 선택 매장이 여러 곳이면 한 손님이
	 * 똑같은 성격의 알림을 여러 통 받는 문제가 있었다(조장 확인 후 수정).
	 */
	private void notifyCustomers(CouponCampaignEntity campaign, List<StoreEntity> stores,
	                              boolean notifyLiked, boolean notifySameRegion) {
		String discountLabel = CouponCampaignEntity.DISCOUNT_TYPE_AMOUNT.equals(campaign.getDiscountType())
				? campaign.getDiscountAmount() + "원"
				: campaign.getDiscountRate() + "%";

		if (notifyLiked) {
			for (StoreEntity store : stores) {
				Set<Long> likedUserIds = likeRepository.findByStoreId(store.getId()).stream()
						.map(LikeEntity::getUserId).collect(Collectors.toSet());
				String message = "근처 " + store.getStoreName() + "에서 쓸 수 있는 " + discountLabel + " 쿠폰이 도착했어요";
				String linkUrl = "/user/stores/" + store.getId();
				for (Long userId : likedUserIds) {
					notificationService.createNotification(userId, NotificationEntity.TYPE_STORE_COUPON_AVAILABLE,
							message, linkUrl, "store_coupon:" + campaign.getId() + ":liked:" + userId);
				}
			}
		}

		if (notifySameRegion) {
			Set<String> targetSidos = stores.stream()
					.map(StoreEntity::getSido).filter(Objects::nonNull).collect(Collectors.toSet());
			if (targetSidos.isEmpty()) {
				return;
			}
			String regionLabel = targetSidos.size() == 1 ? targetSidos.iterator().next() : "우리 동네";
			String message = regionLabel + " 매장에서 쓸 수 있는 " + discountLabel + " 쿠폰이 도착했어요";
			for (UserEntity u : userRepository.findAll()) {
				if (targetSidos.contains(SidoParser.parse(u.getRegion()))) {
					notificationService.createNotification(u.getId(), NotificationEntity.TYPE_STORE_COUPON_AVAILABLE,
							message, "/", "store_coupon:" + campaign.getId() + ":region:" + u.getId());
				}
			}
		}
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

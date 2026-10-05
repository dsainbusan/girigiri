package net.dsa.girigiri.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.CouponCampaignRowDto;
import net.dsa.girigiri.domain.entity.CouponCampaignEntity;
import net.dsa.girigiri.domain.entity.CouponEntity;
import net.dsa.girigiri.domain.entity.CouponRegionEntity;
import net.dsa.girigiri.domain.entity.CouponStoreEntity;
import net.dsa.girigiri.domain.entity.LikeEntity;
import net.dsa.girigiri.domain.entity.NotificationEntity;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.domain.entity.UserEntity;
import net.dsa.girigiri.repository.CouponCampaignRepository;
import net.dsa.girigiri.repository.CouponRegionRepository;
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

	/** 지역 지정 쿠폰 발행 결과 — success=false면 입력값 오류/대상 회원 없음, issuedCount는 실제 발급된 쿠폰 수. */
	public record RegionCampaignResult(boolean success, int issuedCount) {
		static RegionCampaignResult invalid() {
			return new RegionCampaignResult(false, 0);
		}
	}

	private static final DateTimeFormatter DATE_LABEL = DateTimeFormatter.ofPattern("yyyy-MM-dd");
	private static final int MAX_RATE = 90;

	private final CouponCampaignRepository campaignRepository;
	private final CouponRepository couponRepository;
	// 추가됨 (2026-10-01, 매장 지정 쿠폰) — createStoreCampaign 전용 의존성.
	private final CouponStoreRepository couponStoreRepository;
	// 추가됨 (2026-10-06, 지역 지정 쿠폰) — createRegionCampaign 전용 의존성.
	private final CouponRegionRepository couponRegionRepository;
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
	 * 지역 지정 쿠폰 발행("뿌리기") — 2026-10-06 신규(/superadmin/coupons). 선택한 시도에 사는 활성 일반
	 * 회원(role=USER, status=ACTIVE, users.region을 SidoParser로 파싱한 값이 대상 시도에 포함)의 쿠폰함으로
	 * 쿠폰을 바로 발급한다. 매장 지정 쿠폰(createStoreCampaign)처럼 "쿠폰 받기" 버튼을 누르게 하지 않는
	 * 이유는 대상이 매장을 찜한 사람이 아니라 지역 주민 전체라 상세 페이지 진입점이 따로 없기 때문이다.
	 * 쓸 수 있는 곳은 대상 시도에 있는 매장뿐이다(CouponService#validateForRedeem).
	 * 비용은 전부 플랫폼 부담(FUNDED_BY_PLATFORM)이라 정산은 건드리지 않는다. 발행 수량은 발급 대상 회원
	 * 수와 같다(issueLimit) — 클라이언트가 모달에서 본 숫자는 믿지 않고 여기서 다시 계산한다.
	 */
	@Transactional
	public RegionCampaignResult createRegionCampaign(List<String> sidos, String discountType, Integer discountRate,
	                                                  Integer discountAmount, Integer maxDiscountAmount,
	                                                  Integer minOrderAmount, int validDays,
	                                                  String reason, Long adminId, boolean notify) {
		List<String> targetSidos = sidos == null ? List.of() : sidos.stream()
				.filter(SidoParser.SIDO_LIST::contains).distinct().toList();
		if (targetSidos.isEmpty() || validDays < 1) {
			return RegionCampaignResult.invalid();
		}
		boolean isRate = CouponCampaignEntity.DISCOUNT_TYPE_RATE.equals(discountType);
		boolean isAmount = CouponCampaignEntity.DISCOUNT_TYPE_AMOUNT.equals(discountType);
		if (!isRate && !isAmount) {
			return RegionCampaignResult.invalid();
		}
		if (isRate && (discountRate == null || discountRate < 1 || discountRate > MAX_RATE)) {
			return RegionCampaignResult.invalid();
		}
		if (isAmount && (discountAmount == null || discountAmount < 1)) {
			return RegionCampaignResult.invalid();
		}
		int min = minOrderAmount == null ? 0 : minOrderAmount;
		if (min < 0 || (maxDiscountAmount != null && maxDiscountAmount < 1)) {
			return RegionCampaignResult.invalid();
		}

		List<UserEntity> recipients = findRegionRecipients(targetSidos);
		if (recipients.isEmpty()) {
			return RegionCampaignResult.invalid();
		}

		LocalDateTime expiresAt = LocalDateTime.now().plusDays(validDays);
		CouponCampaignEntity campaign = CouponCampaignEntity.builder()
				.name("지역 지정 쿠폰 · " + LocalDate.now().format(DATE_LABEL) + " (" + String.join("·", targetSidos) + ")")
				.scope(CouponCampaignEntity.SCOPE_REGION)
				.discountType(discountType)
				.discountRate(isRate ? discountRate : null)
				.discountAmount(isAmount ? discountAmount : null)
				.maxDiscountAmount(isRate ? maxDiscountAmount : null)
				.minOrderAmount(min)
				.issueLimit(recipients.size())
				.fundedBy(CouponCampaignEntity.FUNDED_BY_PLATFORM)
				.issuedByAdminId(adminId)
				.issueReason(reason != null && !reason.isBlank() ? reason.trim() : null)
				.expiresAt(expiresAt)
				.active(true)
				.build();
		campaignRepository.save(campaign);

		for (String sido : targetSidos) {
			couponRegionRepository.save(CouponRegionEntity.builder().campaignId(campaign.getId()).sido(sido).build());
		}

		List<CouponEntity> coupons = recipients.stream()
				.map(u -> CouponEntity.builder()
						.source(CouponEntity.SOURCE_PROMOTION)
						.issuedToUserId(u.getId())
						.campaignId(campaign.getId())
						.scope(campaign.getScope())
						.discountType(campaign.getDiscountType())
						.discountRate(campaign.getDiscountRate())
						.discountAmount(campaign.getDiscountAmount())
						.maxDiscountAmount(campaign.getMaxDiscountAmount())
						.minOrderAmount(campaign.getMinOrderAmount())
						.expiresAt(expiresAt)
						.used(false)
						.build())
				.toList();
		couponRepository.saveAll(coupons);

		if (notify) {
			String discountLabel = isAmount ? discountAmount + "원" : discountRate + "%";
			String regionLabel = targetSidos.size() == 1 ? targetSidos.get(0) : "우리 동네";
			String message = regionLabel + " 매장에서 쓸 수 있는 " + discountLabel + " 쿠폰이 도착했어요";
			for (UserEntity u : recipients) {
				notificationService.createNotification(u.getId(), NotificationEntity.TYPE_STORE_COUPON_AVAILABLE,
						message, "/coupons", "region_coupon:" + campaign.getId() + ":" + u.getId());
			}
		}
		return new RegionCampaignResult(true, coupons.size());
	}

	/** 쿠폰 발행 모달 미리보기용 — 선택한 시도에 사는 발급 대상 회원 수. 실제 발행 때는 서버가 다시 계산한다. */
	@Transactional(readOnly = true)
	public int countRegionRecipients(List<String> sidos) {
		if (sidos == null || sidos.isEmpty()) {
			return 0;
		}
		return findRegionRecipients(sidos.stream().filter(SidoParser.SIDO_LIST::contains).distinct().toList()).size();
	}

	private List<UserEntity> findRegionRecipients(List<String> targetSidos) {
		if (targetSidos.isEmpty()) {
			return List.of();
		}
		return userRepository.findAll().stream()
				.filter(u -> UserEntity.ROLE_USER.equals(u.getRole()))
				.filter(u -> u.getStatus() == null || UserEntity.STATUS_ACTIVE.equals(u.getStatus()))
				.filter(u -> targetSidos.contains(SidoParser.parse(u.getRegion())))
				.toList();
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
		long claimed = couponRepository.countByCampaignId(c.getId());
		Integer claimPercent = c.getIssueLimit() != null && c.getIssueLimit() > 0
				? (int) Math.min(100, claimed * 100 / c.getIssueLimit()) : null;
		return CouponCampaignRowDto.builder()
				.id(c.getId())
				.name(c.getName())
				.code(c.getCode())
				.discountRate(c.getDiscountRate())
				.discountLabel(CouponCampaignEntity.DISCOUNT_TYPE_AMOUNT.equals(c.getDiscountType())
						? c.getDiscountAmount() + "원" : c.getDiscountRate() + "%")
				.scopeLabel(scopeLabel(c))
				.typeLabel(CouponCampaignEntity.SCOPE_REGION.equals(c.getScope()) ? "지역 지정"
						: CouponCampaignEntity.SCOPE_STORE.equals(c.getScope()) ? "매장 지정" : "코드형")
				.issueLimit(c.getIssueLimit())
				.daysLeft(expired ? null : (int) java.time.temporal.ChronoUnit.DAYS.between(now.toLocalDate(), c.getExpiresAt().toLocalDate()))
				.expiresAtLabel(c.getExpiresAt().toLocalDate().format(DATE_LABEL))
				.active(c.isActive())
				.expired(expired)
				.claimedCount(claimed)
				.claimPercent(claimPercent)
				.statusLabel(statusLabel)
				.build();
	}

	// 캠페인 목록의 "코드" 칸 — 코드형은 코드, 매장/지역 지정형은 코드가 없으니 적용 범위를 보여준다.
	private String scopeLabel(CouponCampaignEntity c) {
		if (CouponCampaignEntity.SCOPE_REGION.equals(c.getScope())) {
			return "대상 지역 " + couponRegionRepository.findByCampaignId(c.getId()).stream()
					.map(CouponRegionEntity::getSido).collect(Collectors.joining("·"));
		}
		if (CouponCampaignEntity.SCOPE_STORE.equals(c.getScope())) {
			return "대상 매장 " + couponStoreRepository.findByCampaignId(c.getId()).size() + "곳";
		}
		return null;
	}
}

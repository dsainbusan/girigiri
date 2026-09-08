package net.dsa.girigiri.service;

import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.StoreRecentStatsDto;
import net.dsa.girigiri.domain.entity.ProductEntity;
import net.dsa.girigiri.domain.entity.SettlementEntity;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.domain.entity.UserEntity;
import net.dsa.girigiri.repository.LikeRepository;
import net.dsa.girigiri.repository.ListingTemplateRepository;
import net.dsa.girigiri.repository.MenuItemRepository;
import net.dsa.girigiri.repository.ProductRepository;
import net.dsa.girigiri.repository.ReportRepository;
import net.dsa.girigiri.repository.ReservationRepository;
import net.dsa.girigiri.repository.ReviewRepository;
import net.dsa.girigiri.repository.ReviewSummaryRepository;
import net.dsa.girigiri.repository.SettlementRepository;
import net.dsa.girigiri.repository.StoreRepository;
import net.dsa.girigiri.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 슈퍼어드민 "매장 관리" 도메인 서비스 (2026-09-03, 레이어 규칙 2단계 — SuperAdminController 도메인 분리).
 *
 * SuperAdminController의 매장 관리 관련 Repository 직접 호출·검증·상태 변경 로직을 옮겨온다.
 */
@Service
@RequiredArgsConstructor
public class SuperAdminStoreService {

	// SuperAdminMemberService.canWithdraw()와 동일한 가드 — 미완료 예약이 있으면 삭제를 막는다.
	// (2026-09-08, 코드 감사) ReservationService.INCOMPLETE_STATUSES로 통일 — "ready" 누락 수정.
	private static final List<String> INCOMPLETE_RESERVATION_STATUSES = ReservationService.INCOMPLETE_STATUSES;

	private final StoreRepository storeRepository;
	private final ProductRepository productRepository;
	private final ReservationRepository reservationRepository;
	private final UserRepository userRepository;
	private final LookupService lookupService;
	// 추가됨 (2026-09-08, 코드 감사) — 매장 삭제 시 미지급 정산 차단 + 참조 테이블 정리용.
	private final SettlementRepository settlementRepository;
	private final MenuItemRepository menuItemRepository;
	private final ListingTemplateRepository listingTemplateRepository;
	private final LikeRepository likeRepository;
	private final ReviewRepository reviewRepository;
	private final ReviewSummaryRepository reviewSummaryRepository;
	private final ReportRepository reportRepository;

	// 변경됨 — 왜: "승인대기/매장목록을 따로 나누지 말고 전체 하나로, 대기 매장은 필터로 보게 해달라"는
	// 요청 — REJECTED만 빼고 전부 한 리스트로 묶은 뒤, filter=PENDING일 때만 대기 매장으로 좁힌다.
	// 추가됨 (2026-09-08) — 왜: 매장 검색 기능 요청. SuperAdminMemberService.findFilteredMembers()와
	// 같은 자리(필터 전에)에서 걸러낸다 — 매장명/주소/연락처 중 하나라도 검색어를 포함하면 매칭.
	// 이미 findAll() 후 자바에서 필터링하던 기존 방식 그대로라, 리포지토리 쿼리 메서드는 새로 안 만들었다.
	@Transactional(readOnly = true)
	public List<StoreEntity> findStores(String q, String normalizedFilter) {
		String keyword = q == null ? "" : q.trim();
		List<StoreEntity> all = storeRepository.findAll().stream()
				.filter(s -> !StoreEntity.STATUS_REJECTED.equals(s.getApprovalStatus()))
				.filter(s -> keyword.isEmpty() || matchesKeyword(s, keyword))
				.toList();
		return "PENDING".equals(normalizedFilter)
				? all.stream().filter(s -> StoreEntity.STATUS_PENDING.equals(s.getApprovalStatus())).toList()
				: all;
	}

	private boolean matchesKeyword(StoreEntity store, String keyword) {
		String lower = keyword.toLowerCase();
		return containsIgnoreCase(store.getStoreName(), lower)
				|| containsIgnoreCase(store.getAddress(), lower)
				|| containsIgnoreCase(store.getPhone(), lower);
	}

	private boolean containsIgnoreCase(String value, String lowerKeyword) {
		return value != null && value.toLowerCase().contains(lowerKeyword);
	}

	// 추가됨 (2026-09-08) — 왜: "선택 매장 정지/정지 해제" 요청. SuperAdminMemberService.bulkSuspend/
	// bulkUnsuspend와 동일한 패턴.
	@Transactional
	public void bulkSuspend(List<Long> ids) {
		if (ids != null && !ids.isEmpty()) {
			List<StoreEntity> targets = storeRepository.findAllById(ids);
			targets.forEach(s -> s.setStatus(StoreEntity.STATUS_SUSPENDED));
			storeRepository.saveAll(targets);
		}
	}

	@Transactional
	public void bulkUnsuspend(List<Long> ids) {
		if (ids != null && !ids.isEmpty()) {
			List<StoreEntity> targets = storeRepository.findAllById(ids);
			targets.forEach(s -> s.setStatus(StoreEntity.STATUS_ACTIVE));
			storeRepository.saveAll(targets);
		}
	}

	/**
	 * "입점 승인 대기" 목록의 승인 버튼 처리. AuthController#ownerApply 주석에 이미 "운영자의 심사/승인
	 * 후 role=OWNER로 전환됨"이라고 적혀 있던 대로, 매장 승인과 함께 신청자 계정의 role도 USER → OWNER로
	 * 올려준다.
	 */
	@Transactional
	public void approve(Long id) {
		StoreEntity store = lookupService.getStore(id);
		store.setApprovalStatus(StoreEntity.STATUS_APPROVED);
		storeRepository.save(store);

		userRepository.findById(store.getOwnerId()).ifPresent(owner -> {
			owner.setRole(UserEntity.ROLE_OWNER);
			userRepository.save(owner);
		});
	}

	@Transactional(readOnly = true)
	public boolean canDelete(Long id) {
		// 변경됨 (2026-09-08, 코드 감사) — 진행중 예약뿐 아니라 미지급/이월대기 정산(settlement)도
		// 확인한다. 예전엔 이 체크가 없어서 PENDING(지급 대기)·CARRIED(이월 대기) 정산이 있는 매장도
		// 그냥 삭제됐다 — 그 돈이 지급 파이프라인(SettlementBatchService)에서 다시는 안 보이게 되는
		// 문제(감사에서 발견).
		boolean hasIncompleteReservation = reservationRepository.existsByStoreIdAndStatusIn(id, INCOMPLETE_RESERVATION_STATUSES);
		boolean hasUnsettledPayout = settlementRepository.existsByStoreIdAndStatusIn(
				id, List.of(SettlementEntity.STATUS_PENDING, SettlementEntity.STATUS_CARRIED));
		return !hasIncompleteReservation && !hasUnsettledPayout;
	}

	/**
	 * FK가 연관관계로 매핑돼 있지 않아(ERD 확정 전까지 plain Long id 컬럼만 쓰는 컨벤션) 매장을 지우면
	 * 그 매장을 참조하는 데이터들이 고아로 남는다. approve()가 승인 시 OWNER로 올려주는
	 * 것의 반대로, 삭제 시 소유자가 아직 OWNER면 USER로 되돌린다(다른 매장을 또 만들 수도 있으니 강제
	 * 탈퇴는 아님).
	 *
	 * 변경됨 (2026-09-08, 코드 감사) — 예전엔 상품(product)만 같이 지웠다. 그 외 참조 테이블을 점검해서
	 * 두 그룹으로 나눴다:
	 *  - 매장 전용 부산물이라 다른 화면이 다시 조회할 일이 없는 것(menu_item/listing_template/likes/
	 *    review/review_summary/report)은 상품과 함께 지운다.
	 *  - 회계·이력 성격이라 보존해야 하는 것(reservation/payment/settlement — 완료건)은 그대로 둔다.
	 *    미지급 정산은 위 canDelete()가 이미 막아서 여기까지 오지 않는다. inquiry.storeId/
	 *    complaint.targetStoreId는 각 화면이 이미 store 조회 실패를 null-safe하게 처리하고 있어(예:
	 *    InquiryService#getStoreName) 손대지 않는다.
	 */
	@Transactional
	public void delete(Long id) {
		StoreEntity store = lookupService.getStore(id);

		productRepository.deleteAll(productRepository.findByStoreId(id));
		menuItemRepository.deleteByStoreId(id);
		listingTemplateRepository.deleteByStoreId(id);
		likeRepository.deleteByStoreId(id);
		reviewRepository.deleteByStoreId(id);
		reviewSummaryRepository.deleteByStoreId(id);
		reportRepository.deleteByStoreId(id);

		userRepository.findById(store.getOwnerId())
				.filter(owner -> UserEntity.ROLE_OWNER.equals(owner.getRole()))
				.ifPresent(owner -> {
					owner.setRole(UserEntity.ROLE_USER);
					userRepository.save(owner);
				});

		storeRepository.deleteById(id);
	}

	@Transactional(readOnly = true)
	public Optional<UserEntity> findOwner(Long ownerId) {
		return userRepository.findById(ownerId);
	}

	/**
	 * 운영자가 특정 매장 하나를 골라 긴급 연락처·최근 판매율(구제율)을 확인하는 용도라,
	 * StoreController.dashboard()(점주 본인용, "오늘" 기준)와 달리 등록/판매가 없는 날도 의미 있게
	 * 보이도록 최근 7일 창으로 구제율을 계산한다.
	 */
	@Transactional(readOnly = true)
	public StoreRecentStatsDto getRecentStats(Long storeId) {
		LocalDateTime rangeStart = LocalDate.now().minusDays(6).atStartOfDay();
		List<ProductEntity> recentProducts = productRepository.findByStoreId(storeId).stream()
				.filter(p -> p.getRegisteredAt() != null && !p.getRegisteredAt().isBefore(rangeStart))
				.toList();

		int registeredCount7d = recentProducts.size();
		int soldCount7d = recentProducts.stream()
				.mapToInt(p -> p.getQuantity() - p.getRemainingQuantity())
				.sum();
		int totalQuantity7d = recentProducts.stream().mapToInt(ProductEntity::getQuantity).sum();
		int rescueRate7d = totalQuantity7d == 0 ? 0 : (int) Math.round(100.0 * soldCount7d / totalQuantity7d);

		return new StoreRecentStatsDto(registeredCount7d, soldCount7d, totalQuantity7d, rescueRate7d);
	}

	public boolean isEditValid(String storeName, String category, String phone, String address) {
		return !(storeName == null || storeName.isBlank() || category == null || category.isBlank()
				|| phone == null || phone.isBlank() || address == null || address.isBlank());
	}

	/**
	 * 점주 본인용 /store/edit는 상호명/사업자번호/주소를 승인 심사 근거라는 이유로 일부러 막아뒀지만,
	 * 운영자는 그 제한을 받을 이유가 없어(오히려 오탈자·정보 오류를 고쳐줘야 하는 쪽) 전체 필드를 연다.
	 * approvalStatus는 여기서 안 건드린다 — "입점 승인 대기" 액션이 생기면 그쪽에서 따로 처리한다.
	 */
	@Transactional
	public void updateStoreInfo(Long id, String storeName, String category, String phone, String address,
	                             String businessNumber, String operatingHours, Double latitude, Double longitude) {
		StoreEntity store = lookupService.getStore(id);

		store.setStoreName(storeName.trim());
		store.setCategory(category.trim());
		store.setPhone(phone.trim());
		store.setAddress(address.trim());
		store.setBusinessNumber(businessNumber != null && !businessNumber.isBlank() ? businessNumber.trim() : null);
		store.setOperatingHours(operatingHours != null && !operatingHours.isBlank() ? operatingHours.trim() : null);
		store.setLatitude(latitude);
		store.setLongitude(longitude);
		storeRepository.save(store);
	}
}

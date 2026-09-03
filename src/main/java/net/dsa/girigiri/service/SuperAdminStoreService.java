package net.dsa.girigiri.service;

import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.StoreRecentStatsDto;
import net.dsa.girigiri.domain.entity.ProductEntity;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.domain.entity.UserEntity;
import net.dsa.girigiri.repository.ProductRepository;
import net.dsa.girigiri.repository.ReservationRepository;
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
	private static final List<String> INCOMPLETE_RESERVATION_STATUSES = List.of("pending", "confirmed");

	private final StoreRepository storeRepository;
	private final ProductRepository productRepository;
	private final ReservationRepository reservationRepository;
	private final UserRepository userRepository;
	private final LookupService lookupService;

	// 변경됨 — 왜: "승인대기/매장목록을 따로 나누지 말고 전체 하나로, 대기 매장은 필터로 보게 해달라"는
	// 요청 — REJECTED만 빼고 전부 한 리스트로 묶은 뒤, filter=PENDING일 때만 대기 매장으로 좁힌다.
	@Transactional(readOnly = true)
	public List<StoreEntity> findStores(String normalizedFilter) {
		List<StoreEntity> all = storeRepository.findAll().stream()
				.filter(s -> !StoreEntity.STATUS_REJECTED.equals(s.getApprovalStatus()))
				.toList();
		return "PENDING".equals(normalizedFilter)
				? all.stream().filter(s -> StoreEntity.STATUS_PENDING.equals(s.getApprovalStatus())).toList()
				: all;
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
		return !reservationRepository.existsByStoreIdAndStatusIn(id, INCOMPLETE_RESERVATION_STATUSES);
	}

	/**
	 * FK가 연관관계로 매핑돼 있지 않아(ERD 확정 전까지 plain Long id 컬럼만 쓰는 컨벤션) 매장을 지우면
	 * 그 매장의 ProductEntity들이 고아로 남으므로 같이 지운다. approve()가 승인 시 OWNER로 올려주는
	 * 것의 반대로, 삭제 시 소유자가 아직 OWNER면 USER로 되돌린다(다른 매장을 또 만들 수도 있으니 강제
	 * 탈퇴는 아님).
	 */
	@Transactional
	public void delete(Long id) {
		StoreEntity store = lookupService.getStore(id);

		productRepository.deleteAll(productRepository.findByStoreId(id));

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

package net.dsa.girigiri.service;

import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.ProductFormDto;
import net.dsa.girigiri.domain.entity.ProductEntity;
import net.dsa.girigiri.domain.entity.ReservationEntity;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.repository.ProductRepository;
import net.dsa.girigiri.repository.ReservationRepository;
import net.dsa.girigiri.repository.StoreRepository;
import net.dsa.girigiri.util.DiscountRateCalculator;
import net.dsa.girigiri.util.FileStorageUtil;
import net.dsa.girigiri.util.StoreHoursUtil;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 점주 상품(재고) CRUD — WBS 3.0 (원래 김태훈 담당 → 2026-08-26 문창호 인계).
 *
 * - 할인가는 폼에서 안 받는다: "원가 + 마감까지 남은 시간" 기준으로 서버가 자동 계산
 *   (DiscountRateCalculator + StoreHoursUtil — PosApiController와 동일 정책).
 * - 사진은 FileStorageUtil(강노은 공용 유틸)로 로컬 upload/product/ 아래 저장, 웹 경로만 DB에.
 * - 모든 변경 메서드는 "세션 점주(ownerId)가 그 상품이 속한 매장의 주인인지"를 먼저 검증한다
 *   (URL 조작으로 남의 상품을 건드리는 걸 막기 위해).
 */
@Service
@RequiredArgsConstructor
public class ProductService {

	private static final String IMAGE_SUBDIR = "product";

	private final ProductRepository productRepository;
	private final StoreRepository storeRepository;
	private final ReservationRepository reservationRepository;
	private final FileStorageUtil fileStorageUtil;

	/** 점주 재고 목록 (최근 등록순). */
	public List<ProductEntity> listForOwner(Long ownerId) {
		return productRepository.findByStoreIdOrderByRegisteredAtDesc(requireStore(ownerId).getId());
	}

	/** 수정 화면에 채워 넣을 값 조회 (소유권 검증 포함). */
	public ProductEntity getOwnedProduct(Long ownerId, Long productId) {
		return requireOwnedProduct(requireStore(ownerId), productId);
	}

	@Transactional
	public Long create(Long ownerId, ProductFormDto form, MultipartFile image) {
		StoreEntity store = requireStore(ownerId);
		validate(form);
		Integer ownerRate = resolveOwnerRate(store, form.getDiscountRate());

		// 새로 올린 파일이 우선, 없으면 POS 카탈로그에서 넘어온 사진 URL(currentImageUrl)을 그대로 쓴다.
		String uploaded = fileStorageUtil.store(image, IMAGE_SUBDIR);
		String imageUrl = uploaded != null ? uploaded : blankToNull(form.getCurrentImageUrl());

		ProductEntity product = ProductEntity.builder()
				.storeId(store.getId())
				.name(form.getName().trim())
				.originalPrice(form.getOriginalPrice())
				.discountedPrice(calcDiscountedPrice(store, form.getOriginalPrice(), ownerRate))
				.quantity(form.getQuantity())
				.remainingQuantity(form.getQuantity())
				.description(blankToNull(form.getDescription()))
				.imageUrl(imageUrl)
				.status("active")
				.build();
		productRepository.save(product);
		return product.getId();
	}

	@Transactional
	public void update(Long ownerId, Long productId, ProductFormDto form, MultipartFile image, boolean removeImage) {
		StoreEntity store = requireStore(ownerId);
		// 변경됨 (2026-09-08, 코드 감사) — 락 없는 requireOwnedProduct(findById) 대신 findByIdForUpdate로
		// 이 상품 행을 잠근다. 점주가 수정 폼을 띄운 사이 손님이 예약(StockService.decreaseStock, 같은
		// 락을 씀)하면 그 감소분이 아래 remainingQuantity 재계산에 반영이 안 돼 이 save()로 지워지는
		// lost update가 있었다 — 락으로 두 트랜잭션이 겹치지 않게 한다.
		ProductEntity product = requireOwnedProductForUpdate(store, productId);
		validate(form);
		Integer ownerRate = resolveOwnerRate(store, form.getDiscountRate());

		int soldQuantity = product.getQuantity() - product.getRemainingQuantity();
		if (form.getQuantity() < soldQuantity) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"이미 예약·판매된 수량(" + soldQuantity + "개)보다 적게 줄일 수 없어요.");
		}

		product.setName(form.getName().trim());
		product.setOriginalPrice(form.getOriginalPrice());
		product.setDiscountedPrice(calcDiscountedPrice(store, form.getOriginalPrice(), ownerRate));
		// 수량을 늘리면 남은 재고도 같은 만큼 늘린다 (이미 팔린 분은 유지).
		product.setRemainingQuantity(form.getQuantity() - soldQuantity);
		product.setQuantity(form.getQuantity());
		product.setDescription(blankToNull(form.getDescription()));

		if (image != null && !image.isEmpty()) {
			// 새 사진으로 교체
			String previous = product.getImageUrl();
			product.setImageUrl(fileStorageUtil.store(image, IMAGE_SUBDIR));
			fileStorageUtil.deleteIfOwned(previous, IMAGE_SUBDIR);
		} else if (removeImage) {
			// 사진 삭제 → 카테고리 기본 이미지로. (템플릿에서 온 사진이면 파일은 공유 중이라 안 지우고
			//  참조만 끊는다 — deleteIfOwned가 upload/product/ 접두사만 지운다.)
			fileStorageUtil.deleteIfOwned(product.getImageUrl(), IMAGE_SUBDIR);
			product.setImageUrl(null);
		}

		// 품절(status='sold') 상태는 수정으로 자동 해제하지 않는다 — 사장님이 "판매 재개" 버튼으로 직접.
		// (예전엔 markSoldOut이 재고를 0으로 만들어서 "수량 늘리면 자동 재개"가 필요했지만, 이제
		//  재고를 안 건드리므로 명시적 재개만 둔다.)
		productRepository.save(product);
	}

	/**
	 * "오늘의 구제" 초안 → 실제 판매(active)로 전환. [바로 올리기].
	 * @return 발행됐으면(또는 이미 발행돼 있으면) true, 마감 임박/정리됨 등으로 못 올렸으면 false
	 */
	@Transactional
	public boolean publishDraft(Long ownerId, Long productId) {
		StoreEntity store = requireStore(ownerId);
		ProductEntity product = requireOwnedProduct(store, productId);
		if ("active".equals(product.getStatus())) {
			return true;   // 중복 클릭 — 이미 발행됨
		}
		if (!"draft".equals(product.getStatus())) {
			return false;  // skipped 등 — 이미 정리됨
		}
		// 마감 10분 전을 넘겼으면 발행 거부 (손님이 예약·픽업할 시간이 없다).
		// 상태는 draft로 두고 스케줄러 expireStaleDrafts가 실제 마감에 정리 → 그때까진 "등록 마감"으로 카드는 보인다.
		LocalDateTime closeAt = StoreHoursUtil.parse(store.getOperatingHours(), StoreHoursUtil.URGENT_THRESHOLD_MINUTES).closeAt();
		if (!StoreHoursUtil.canPublishNow(closeAt)) {
			return false;
		}
		product.setStatus("active");
		productRepository.save(product);
		return true;
	}

	/**
	 * "오늘의 구제" 초안 폐기. [오늘 안 함].
	 * 행을 지우지 않고 status='skipped'로 둔다 — 지우면 ListingDraftScheduler가 "오늘 이 템플릿으로
	 * 만든 게 없네" 하고 5분 뒤 초안을 다시 만들어버린다(재생성). skipped 행이 있으면 "오늘 이미 처리함"으로
	 * 인식해서 재생성 안 한다. skipped는 발행 대기·목록·대시보드·홈 어디에도 안 뜬다.
	 * 사진은 템플릿이 공유 중이라 지우지 않는다.
	 */
	@Transactional
	public void discardDraft(Long ownerId, Long productId) {
		ProductEntity product = requireOwnedProduct(requireStore(ownerId), productId);
		if (!"draft".equals(product.getStatus())) {
			return;
		}
		product.setStatus("skipped");
		productRepository.save(product);
	}

	/** 사장님이 직접 "품절" 처리. 남은 재고 수치는 건드리지 않는다 — "판매 재개" 때 그대로 되살리기 위해. */
	@Transactional
	public void markSoldOut(Long ownerId, Long productId) {
		ProductEntity product = requireOwnedProduct(requireStore(ownerId), productId);
		product.setStatus("sold");
		productRepository.save(product);
	}

	/**
	 * "판매 재개" — 직접 품절 처리했던 상품(status='sold')을 다시 판매중(active)으로.
	 * 남은 재고가 0이면(예전 markSoldOut이 재고를 0으로 만든 데이터거나 그 밖의 이유) 실제 예약된
	 * 수량만 빼고 재고를 복구한다 — 안 그러면 재개해도 "재고 0"이라 화면상 계속 품절로 보인다.
	 */
	@Transactional
	public void resumeSelling(Long ownerId, Long productId) {
		ProductEntity product = requireOwnedProduct(requireStore(ownerId), productId);
		if (!"sold".equals(product.getStatus())) {
			return;
		}
		product.setStatus("active");

		if (product.getRemainingQuantity() == null || product.getRemainingQuantity() == 0) {
			// 수정됨 (2026-09-08, 코드 감사) — 예전엔 "cancelled 상태만 아니면 다 뺀다"였는데, 그러면
			// cancelByStore/cancelByAdmin으로 매장 귀책 취소된 예약(재고를 의도적으로 복구 안 함,
			// ReservationService 참고 — "재고 착오라 복구하면 없는 재고가 있는 것처럼 된다")까지
			// "이제 다시 판매 가능한 수량"으로 쳐서, 판매 재개 버튼 한 번에 실재하지 않는 음식이
			// 되살아났다. cancelledBy가 USER/SYSTEM인 취소(취소 시점에 실제로 재고를 복구한 경로)만
			// "빠진 것"으로 치고, 그 외(진행중 상태 + STORE/ADMIN 취소 + 노쇼)는 여전히 "쓴 것"으로 센다.
			int reserved = reservationRepository.findByProductIdIn(List.of(productId)).stream()
					.filter(r -> !stockAlreadyRestoredAtCancel(r))
					.mapToInt(r -> r.getReservedQuantity() == null ? 0 : r.getReservedQuantity())
					.sum();
			int total = product.getQuantity() == null ? 0 : product.getQuantity();
			product.setRemainingQuantity(Math.max(0, total - reserved));
		}
		productRepository.save(product);
	}

	@Transactional
	public void delete(Long ownerId, Long productId) {
		ProductEntity product = requireOwnedProduct(requireStore(ownerId), productId);

		// 오늘 이 템플릿으로 만들어진 상품(초안이든 발행됐든)을 지우면, 스케줄러가 다시 만들어버린다.
		// 그래서 삭제 대신 skipped로 둬서 "오늘은 안 함"으로 처리한다 (내일 다시 정상 생성됨).
		boolean fromTemplateToday = product.getTemplateId() != null
				&& product.getRegisteredAt() != null
				&& product.getRegisteredAt().toLocalDate().equals(LocalDate.now());
		if (fromTemplateToday) {
			product.setStatus("skipped");
			productRepository.save(product);
			return;
		}

		fileStorageUtil.deleteIfOwned(product.getImageUrl(), IMAGE_SUBDIR);
		productRepository.delete(product);
	}

	// ---------------------------------------------------------------------

	private StoreEntity requireStore(Long ownerId) {
		if (ownerId == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요해요.");
		}
		return storeRepository.findByOwnerId(ownerId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "등록된 매장이 없어요."));
	}

	private ProductEntity requireOwnedProduct(StoreEntity store, Long productId) {
		ProductEntity product = productRepository.findById(productId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "상품을 찾을 수 없어요."));
		if (!store.getId().equals(product.getStoreId())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "다른 매장의 상품은 건드릴 수 없어요.");
		}
		return product;
	}

	// 추가됨 (2026-09-08, 코드 감사) — update()처럼 remainingQuantity를 읽어서 재계산 후 저장하는
	// 메서드 전용. StockService(예약 재고 차감/복구)와 같은 findByIdForUpdate 락을 써서, 이 상품에
	// 대한 예약 처리와 점주의 수정 저장이 서로 겹치지 않게 한다.
	private ProductEntity requireOwnedProductForUpdate(StoreEntity store, Long productId) {
		ProductEntity product = productRepository.findByIdForUpdate(productId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "상품을 찾을 수 없어요."));
		if (!store.getId().equals(product.getStoreId())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "다른 매장의 상품은 건드릴 수 없어요.");
		}
		return product;
	}

	/**
	 * 할인가 계산. ownerRate(폼에서 점주가 직접 입력한 할인율 %)가 null이면 마감시간 기준 자동값,
	 * 값이 있으면 그 값으로 하되 자동값보다 낮으면 자동값으로 끌어올린다 (DiscountRateCalculator.effectiveRate).
	 */
	private int calcDiscountedPrice(StoreEntity store, int originalPrice, Integer ownerRate) {
		LocalDateTime closeAt = StoreHoursUtil.parse(store.getOperatingHours(), StoreHoursUtil.URGENT_THRESHOLD_MINUTES).closeAt();
		int rate = DiscountRateCalculator.effectiveRate(ownerRate, closeAt);
		return DiscountRateCalculator.applyDiscount(originalPrice, rate);
	}

	/**
	 * 폼의 할인율 문자열을 검증해서 Integer(또는 null)로. 비우면 null(자동).
	 * 자동값보다 낮게 넣으면 거부한다 — "많이만 적을 수 있게" (POS 메뉴 설정과 동일 정책).
	 */
	private Integer resolveOwnerRate(StoreEntity store, String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		int rate;
		try {
			rate = Integer.parseInt(raw.trim());
		} catch (NumberFormatException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "할인율은 숫자로 입력해 주세요.");
		}
		if (rate > 90) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "할인율은 90%를 넘을 수 없어요.");
		}
		int auto = DiscountRateCalculator.calculateRate(
				StoreHoursUtil.parse(store.getOperatingHours(), StoreHoursUtil.URGENT_THRESHOLD_MINUTES).closeAt());
		if (rate < auto) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"할인율은 마감시간 기준 자동값(" + auto + "%)보다 낮출 수 없어요. 더 깎는 건 가능해요.");
		}
		return rate;
	}

	/**
	 * cancelReservation(USER)·expireStalePendingReservations/confirmPayment 실패 처리(SYSTEM)는
	 * 취소 시점에 stockService.restoreStock을 호출해 재고를 실제로 돌려놓는다 — 이 예약들은
	 * resumeSelling의 재계산에서 "이미 반영된 것"이니 다시 빼면 안 된다. cancelByStore(STORE)/
	 * cancelByAdmin(ADMIN)은 그 호출을 의도적으로 안 하므로 여전히 "재고를 쓴 채로 남아있는 것".
	 */
	private boolean stockAlreadyRestoredAtCancel(ReservationEntity reservation) {
		return "cancelled".equals(reservation.getStatus())
				&& ("USER".equals(reservation.getCancelledBy()) || "SYSTEM".equals(reservation.getCancelledBy()));
	}

	private void validate(ProductFormDto form) {
		if (form.getName() == null || form.getName().isBlank()
				|| form.getOriginalPrice() == null || form.getOriginalPrice() <= 0
				|| form.getQuantity() == null || form.getQuantity() <= 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "필수 항목을 모두 올바르게 입력해 주세요.");
		}
	}

	private String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}
}

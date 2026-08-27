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
	private static final long URGENT_THRESHOLD_MINUTES = 60;

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

		// 새로 올린 파일이 우선, 없으면 POS 카탈로그에서 넘어온 사진 URL(currentImageUrl)을 그대로 쓴다.
		String uploaded = fileStorageUtil.store(image, IMAGE_SUBDIR);
		String imageUrl = uploaded != null ? uploaded : blankToNull(form.getCurrentImageUrl());

		ProductEntity product = ProductEntity.builder()
				.storeId(store.getId())
				.name(form.getName().trim())
				.originalPrice(form.getOriginalPrice())
				.discountedPrice(calcDiscountedPrice(store, form.getOriginalPrice()))
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
		ProductEntity product = requireOwnedProduct(store, productId);
		validate(form);

		int soldQuantity = product.getQuantity() - product.getRemainingQuantity();
		if (form.getQuantity() < soldQuantity) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"이미 예약·판매된 수량(" + soldQuantity + "개)보다 적게 줄일 수 없어요.");
		}

		product.setName(form.getName().trim());
		product.setOriginalPrice(form.getOriginalPrice());
		product.setDiscountedPrice(calcDiscountedPrice(store, form.getOriginalPrice()));
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

	/** "오늘의 구제" 초안 → 실제 판매(active)로 전환. [바로 올리기]. */
	@Transactional
	public void publishDraft(Long ownerId, Long productId) {
		ProductEntity product = requireOwnedProduct(requireStore(ownerId), productId);
		if (!"draft".equals(product.getStatus())) {
			return;   // 이미 발행됐거나 삭제됨 — 중복 클릭 무시
		}
		product.setStatus("active");
		productRepository.save(product);
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
			int reserved = reservationRepository.findByProductIdIn(List.of(productId)).stream()
					.filter(r -> !"cancelled".equals(r.getStatus()))
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

	private int calcDiscountedPrice(StoreEntity store, int originalPrice) {
		StoreHoursUtil.ClosingInfo closingInfo = StoreHoursUtil.parse(store.getOperatingHours(), URGENT_THRESHOLD_MINUTES);
		int rate = DiscountRateCalculator.calculateRate(closingInfo.closeAt());
		return DiscountRateCalculator.applyDiscount(originalPrice, rate);
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

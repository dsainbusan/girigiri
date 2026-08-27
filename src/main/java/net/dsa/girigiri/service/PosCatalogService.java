package net.dsa.girigiri.service;

import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.PosMenuItemDto;
import net.dsa.girigiri.domain.dto.PosStockDto;
import net.dsa.girigiri.domain.entity.MenuItemEntity;
import net.dsa.girigiri.domain.entity.ProductEntity;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.repository.MenuItemRepository;
import net.dsa.girigiri.repository.ProductRepository;
import net.dsa.girigiri.repository.StoreRepository;
import net.dsa.girigiri.util.DiscountRateCalculator;
import net.dsa.girigiri.util.StoreHoursUtil;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

/**
 * "POS json 카탈로그 연동 (가정)" — 2026-08-27 신규 (문창호).
 *
 * 진짜 POS 단말이 없어서 connect()는 mock이다: POS사·매장 코드를 받아 저장하고, 그 매장 카테고리에
 * 맞는 샘플 메뉴 카탈로그(MenuItemEntity)를 밀어넣는다. 실제 연동이면 이 자리에서 POS사 API를
 * 호출해 카탈로그를 받아온다.
 *
 * applyCatalog()는 "실제 POS가 JSON 배열을 push했을 때"의 경로 (PosApiController#receiveCatalog).
 * connect/resync와 upsert 로직을 공유한다.
 */
@Service
@RequiredArgsConstructor
public class PosCatalogService {

	private static final Set<String> PROVIDERS = Set.of("okpos", "posbank", "unionpos", "etc");
	private static final long URGENT_THRESHOLD_MINUTES = 60;

	private final StoreRepository storeRepository;
	private final MenuItemRepository menuItemRepository;
	private final ProductRepository productRepository;

	/** POS사 코드 → 사람이 읽는 이름. 여러 화면(연동/매장설정/오늘의 구제)에서 공용. */
	public static String providerLabel(String provider) {
		if (provider == null) {
			return "";
		}
		return switch (provider) {
			case "okpos" -> "오케이포스";
			case "posbank" -> "포스뱅크";
			case "unionpos" -> "유니온포스";
			default -> "기타 POS";
		};
	}

	public StoreEntity requireStore(Long ownerId) {
		if (ownerId == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요해요.");
		}
		return storeRepository.findByOwnerId(ownerId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "등록된 매장이 없어요."));
	}

	public List<MenuItemEntity> listMenu(Long ownerId) {
		return menuItemRepository.findByStoreIdOrderByNameAsc(requireStore(ownerId).getId());
	}

	public MenuItemEntity getOwnedMenuItem(Long ownerId, Long menuItemId) {
		StoreEntity store = requireStore(ownerId);
		MenuItemEntity item = menuItemRepository.findById(menuItemId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "메뉴를 찾을 수 없어요."));
		if (!store.getId().equals(item.getStoreId())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "다른 매장의 메뉴예요.");
		}
		return item;
	}

	/** [연동하기] — mock. POS사·코드 저장 + 카테고리별 샘플 카탈로그 주입. */
	@Transactional
	public void connect(Long ownerId, String provider, String storeCode) {
		StoreEntity store = requireStore(ownerId);
		if (provider == null || !PROVIDERS.contains(provider)
				|| storeCode == null || storeCode.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "POS사와 매장 코드를 확인해 주세요.");
		}

		store.setPosProvider(provider);
		store.setPosStoreCode(storeCode.trim());
		store.setPosConnectedAt(LocalDateTime.now());

		replaceCatalog(store.getId(), sampleCatalogFor(store.getCategory()));
		store.setPosLastSyncAt(LocalDateTime.now());
		storeRepository.save(store);
	}

	/** [메뉴 다시 불러오기] — mock. 같은 샘플을 다시 밀어넣는다. */
	@Transactional
	public void resync(Long ownerId) {
		StoreEntity store = requireStore(ownerId);
		if (store.getPosProvider() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "먼저 POS를 연동해 주세요.");
		}
		replaceCatalog(store.getId(), sampleCatalogFor(store.getCategory()));
		store.setPosLastSyncAt(LocalDateTime.now());
		storeRepository.save(store);
	}

	/** [연동 해제] — POS 정보 + 카탈로그 삭제. (이미 등록된 상품/템플릿은 건드리지 않는다.) */
	@Transactional
	public void disconnect(Long ownerId) {
		StoreEntity store = requireStore(ownerId);
		menuItemRepository.deleteByStoreId(store.getId());
		store.setPosProvider(null);
		store.setPosStoreCode(null);
		store.setPosConnectedAt(null);
		store.setPosLastSyncAt(null);
		storeRepository.save(store);
	}

	// --- 재고 스냅샷(B안) ------------------------------------------------

	/**
	 * POS가 현재 재고를 push했을 때 (PosApiController#receiveStock). posSku 기준으로 stockQuantity 갱신.
	 * 카탈로그에 없는 sku는 무시한다(먼저 카탈로그를 받아야 함).
	 */
	@Transactional
	public int applyStock(Long ownerId, List<PosStockDto> items) {
		StoreEntity store = requireStore(ownerId);
		int applied = 0;
		for (PosStockDto dto : items) {
			if (dto.getPosSku() == null || dto.getRemaining() == null || dto.getRemaining() < 0) {
				continue;
			}
			MenuItemEntity m = menuItemRepository.findByStoreIdAndPosSku(store.getId(), dto.getPosSku()).orElse(null);
			if (m == null) {
				continue;
			}
			m.setStockQuantity(dto.getRemaining());
			menuItemRepository.save(m);
			applied++;
		}
		store.setPosLastSyncAt(LocalDateTime.now());
		storeRepository.save(store);
		return applied;
	}

	/**
	 * 연동 화면 — 메뉴별 "앱 판매 켬/끔" + 할인율 + 앱 판매 최대 수량 설정.
	 * 할인율은 비우면 자동(마감시간 기준). 값을 넣으면 자동값보다 "더 깎는" 것만 허용 —
	 * 덜 깎으려 하면 거부한다(2026-08-27 정책). 수량은 비우면 재고 전량.
	 */
	@Transactional
	public void updateMenuSaleSettings(Long ownerId, Long menuItemId, boolean appSaleEnabled,
	                                   Integer discountRate, Integer appSaleQuantity) {
		StoreEntity store = requireStore(ownerId);
		MenuItemEntity m = getOwnedMenuItem(ownerId, menuItemId);
		m.setAppSaleEnabled(appSaleEnabled);
		if (discountRate != null) {
			int floor = DiscountRateCalculator.calculateRate(
					StoreHoursUtil.parse(store.getOperatingHours(), URGENT_THRESHOLD_MINUTES).closeAt());
			if (discountRate < floor) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
						"할인율은 자동값(" + floor + "%)보다 낮출 수 없어요. 더 깎는 건 가능해요.");
			}
			if (discountRate > 90) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "할인율은 90%를 넘을 수 없어요.");
			}
		}
		if (appSaleQuantity != null && appSaleQuantity < 1) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "앱 판매 수량은 1개 이상이어야 해요. (전량이면 비워 두세요)");
		}
		m.setDiscountRate(discountRate);
		m.setAppSaleQuantity(appSaleQuantity);
		menuItemRepository.save(m);
	}

	/** 연동 화면 — 매일 몇 시에 POS 재고로 물어볼지. 빈 값이면 자동 생성 끔. */
	@Transactional
	public void updateDraftPromptTime(Long ownerId, String time) {
		StoreEntity store = requireStore(ownerId);
		store.setPosDraftPromptTime(time == null || time.isBlank() ? null : LocalTime.parse(time.trim()));
		storeRepository.save(store);
	}

	/**
	 * "지금 남은 재고로 초안 만들기" — 스케줄러(정해진 시각)와 시뮬레이터 버튼이 공유.
	 * appSaleEnabled 이고 재고 > 0 인 메뉴마다 ProductEntity(status='draft')를 만든다.
	 * 오늘 이미 그 메뉴로 만든 상품(draft/active/skipped)이 있으면 건너뛴다(dedup).
	 * @return 새로 만든 초안 개수
	 */
	@Transactional
	public int generateDraftsFromStock(StoreEntity store) {
		LocalDate today = LocalDate.now();
		List<ProductEntity> storeProducts = productRepository.findByStoreId(store.getId());
		LocalDateTime closeAt = StoreHoursUtil.parse(store.getOperatingHours(), URGENT_THRESHOLD_MINUTES).closeAt();

		int created = 0;
		for (MenuItemEntity m : menuItemRepository.findByStoreIdOrderByNameAsc(store.getId())) {
			if (!m.isAppSaleEnabled() || m.getStockQuantity() == null || m.getStockQuantity() <= 0) {
				continue;
			}
			boolean alreadyToday = storeProducts.stream().anyMatch(p ->
					m.getId().equals(p.getMenuItemId())
							&& p.getRegisteredAt() != null
							&& p.getRegisteredAt().toLocalDate().equals(today));
			if (alreadyToday) {
				continue;
			}
			// 할인가는 "만들 때 한 번" 확정하고 이후 자동으로 안 바꾼다 (2026-08-27 팀 결정: A안).
			// 시간이 지나 마감이 가까워져도 이 상품 가격은 그대로 — 손님/점주 모두 예측 가능하게.
			// 더 깊은 할인을 원하면 점주가 pos_draft_prompt_time을 마감에 더 가깝게 잡으면 된다.
			int rate = DiscountRateCalculator.effectiveRate(m.getDiscountRate(), closeAt);
			int discounted = DiscountRateCalculator.applyDiscount(m.getOriginalPrice(), rate);
			// 앱 판매 수량 상한이 있으면 그만큼만 (재고가 더 적으면 재고만큼).
			int qty = m.getAppSaleQuantity() != null
					? Math.min(m.getStockQuantity(), m.getAppSaleQuantity())
					: m.getStockQuantity();
			productRepository.save(ProductEntity.builder()
					.storeId(store.getId())
					.menuItemId(m.getId())
					.name(m.getName())
					.originalPrice(m.getOriginalPrice())
					.discountedPrice(discounted)
					.quantity(qty)
					.remainingQuantity(qty)
					.imageUrl(m.getImageUrl())
					.status("draft")
					.build());
			created++;
		}
		return created;
	}

	// --- 시뮬레이터(시연용) --------------------------------------------

	/** "아침 생산" — 앱 판매 대상 메뉴들 재고를 넉넉하게 채운다 (가격 낮을수록 많이). */
	@Transactional
	public void simRestock(Long ownerId) {
		StoreEntity store = requireStore(ownerId);
		for (MenuItemEntity m : menuItemRepository.findByStoreIdOrderByNameAsc(store.getId())) {
			m.setStockQuantity(Math.max(5, Math.min(40, 60000 / Math.max(1, m.getOriginalPrice()))));
			menuItemRepository.save(m);
		}
		store.setPosLastSyncAt(LocalDateTime.now());
		storeRepository.save(store);
	}

	/** "하루 장사 빨리감기" — 재고를 40~85% 정도 팔린 걸로 줄인다. */
	@Transactional
	public void simSellDown(Long ownerId) {
		StoreEntity store = requireStore(ownerId);
		java.util.Random rnd = new java.util.Random();
		for (MenuItemEntity m : menuItemRepository.findByStoreIdOrderByNameAsc(store.getId())) {
			int cur = m.getStockQuantity() == null ? 0 : m.getStockQuantity();
			if (cur <= 0) {
				continue;
			}
			double soldRatio = 0.40 + rnd.nextDouble() * 0.45;   // 40~85% 판매
			m.setStockQuantity((int) Math.round(cur * (1 - soldRatio)));
			menuItemRepository.save(m);
		}
		store.setPosLastSyncAt(LocalDateTime.now());
		storeRepository.save(store);
	}

	/** 시뮬레이터에서 재고 직접 입력. */
	@Transactional
	public void simSetStock(Long ownerId, Long menuItemId, int remaining) {
		MenuItemEntity m = getOwnedMenuItem(ownerId, menuItemId);
		m.setStockQuantity(Math.max(0, remaining));
		menuItemRepository.save(m);
	}

	// --- 카탈로그 (기존) -----------------------------------------------

	/** 실제 POS가 JSON 배열을 push했을 때 (PosApiController). posSku(없으면 name) 기준 upsert. */
	@Transactional
	public int applyCatalog(Long ownerId, List<PosMenuItemDto> items) {
		StoreEntity store = requireStore(ownerId);
		int applied = 0;
		for (PosMenuItemDto dto : items) {
			if (dto.getName() == null || dto.getName().isBlank()
					|| dto.getOriginalPrice() == null || dto.getOriginalPrice() <= 0) {
				continue;
			}
			upsert(store.getId(), dto.getPosSku(), dto.getName().trim(), dto.getOriginalPrice(), dto.getImageUrl());
			applied++;
		}
		if (store.getPosProvider() == null) {
			store.setPosProvider("etc");
			store.setPosConnectedAt(LocalDateTime.now());
		}
		store.setPosLastSyncAt(LocalDateTime.now());
		storeRepository.save(store);
		return applied;
	}

	// ---------------------------------------------------------------------

	private void replaceCatalog(Long storeId, List<PosMenuItemDto> items) {
		menuItemRepository.deleteByStoreId(storeId);
		for (PosMenuItemDto dto : items) {
			int price = dto.getOriginalPrice();
			menuItemRepository.save(MenuItemEntity.builder()
					.storeId(storeId)
					.posSku(dto.getPosSku())
					.name(dto.getName())
					.originalPrice(price)
					.imageUrl(dto.getImageUrl())
					// 연동하면 일단 전부 앱 판매 대상 + "아침 생산" 재고를 채워둔다 (점주가 화면에서 조정).
					.appSaleEnabled(true)
					.stockQuantity(Math.max(5, Math.min(40, 60000 / Math.max(1, price))))
					.build());
		}
	}

	private void upsert(Long storeId, String posSku, String name, int originalPrice, String imageUrl) {
		MenuItemEntity item = (posSku != null && !posSku.isBlank())
				? menuItemRepository.findByStoreIdAndPosSku(storeId, posSku).orElse(null)
				: null;
		if (item == null) {
			item = MenuItemEntity.builder().storeId(storeId).posSku(posSku).build();
		}
		item.setName(name);
		item.setOriginalPrice(originalPrice);
		if (imageUrl != null) {
			item.setImageUrl(imageUrl);
		}
		menuItemRepository.save(item);
	}

	private PosMenuItemDto item(String sku, String name, int price) {
		PosMenuItemDto d = new PosMenuItemDto();
		d.setPosSku(sku);
		d.setName(name);
		d.setOriginalPrice(price);
		return d;
	}

	/** mock 카탈로그 — 매장 카테고리에 맞춰 그럴듯한 메뉴를 돌려준다. */
	private List<PosMenuItemDto> sampleCatalogFor(String category) {
		String c = category == null ? "" : category;
		if (c.contains("베이커리")) {
			return List.of(
					item("BR001", "크루아상", 3500), item("BR002", "소금빵", 3000),
					item("BR003", "단팥빵", 2800), item("BR004", "초코 스콘", 4200),
					item("BR005", "밀크 식빵", 5500), item("BR006", "통밀 캄파뉴", 7000),
					item("BR007", "에그타르트", 3200), item("BR008", "베이글", 3800));
		}
		if (c.contains("카페")) {
			return List.of(
					item("CF001", "아메리카노", 4500), item("CF002", "카페라떼", 5000),
					item("CF003", "바닐라라떼", 5500), item("CF004", "복숭아 아이스티", 4000),
					item("CF005", "조각 케이크", 6500), item("CF006", "수제 쿠키", 3000),
					item("CF007", "플레인 스콘", 3500), item("CF008", "마카롱", 2500));
		}
		if (c.contains("반찬")) {
			return List.of(
					item("SD001", "제육볶음", 8000), item("SD002", "진미채무침", 5000),
					item("SD003", "시금치나물", 3500), item("SD004", "계란말이", 4500),
					item("SD005", "멸치볶음", 4000), item("SD006", "포기김치", 6000));
		}
		if (c.contains("도시락") || c.contains("샐러드")) {
			return List.of(
					item("LB001", "불고기 도시락", 6500), item("LB002", "제육 도시락", 6500),
					item("LB003", "치킨마요 덮밥", 5500), item("LB004", "참치마요 삼각김밥", 1500),
					item("LB005", "닭가슴살 샐러드", 5000), item("LB006", "연어 포케", 8500));
		}
		if (c.contains("마트") || c.contains("식료품")) {
			return List.of(
					item("MT001", "우유 1L", 2900), item("MT002", "두부 300g", 1800),
					item("MT003", "계란 10구", 4500), item("MT004", "사과 3입", 5900),
					item("MT005", "삼겹살 500g", 12000), item("MT006", "식빵", 3200));
		}
		return List.of(
				item("GN001", "오늘의 메뉴 A", 6000), item("GN002", "오늘의 메뉴 B", 7000),
				item("GN003", "오늘의 메뉴 C", 8000), item("GN004", "사이드 세트", 4000));
	}
}

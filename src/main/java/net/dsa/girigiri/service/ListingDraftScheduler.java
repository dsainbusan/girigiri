package net.dsa.girigiri.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dsa.girigiri.domain.entity.ListingTemplateEntity;
import net.dsa.girigiri.domain.entity.ProductEntity;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.repository.ListingTemplateRepository;
import net.dsa.girigiri.repository.ProductRepository;
import net.dsa.girigiri.repository.StoreRepository;
import net.dsa.girigiri.util.DiscountRateCalculator;
import net.dsa.girigiri.util.StoreHoursUtil;
import java.time.temporal.ChronoUnit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * "오늘의 구제 자동 등록" 스케줄러 — 2026-08-26 신규 (문창호).
 * (@EnableScheduling은 GirigiriApplication에 이미 있음 — NoShowScheduler / NotificationTriggerScheduler와 동일.)
 *
 * 매 5분마다 활성 템플릿을 훑어서, 오늘 요일이 맞고 알림 시각이 지났고 오늘 아직 초안을 안 만든
 * 템플릿에 대해 ProductEntity(status='draft')를 생성한다. 할인가는 그 시점 마감까지 남은 시간 기준 자동 계산.
 *
 * 초안은 홈/검색(status='active'만 노출)과 대시보드 집계(draft 제외)에서 안 보인다 —
 * 사장님이 [바로 올리기]를 눌러 active로 바꿔야 실제 판매가 시작된다.
 *
 * 변경됨 (2026-08-27) — 왜: 예전엔 여기서 NotificationService로 점주에게 알림을 만들었는데,
 * 그 알림함(/user/alerts)은 손님용 화면이라 점주 진입점이 없어서 죽은 데이터였다. 점주는 대시보드
 * 최상단 "처리할 일" 배너 + /store/products "발행 대기" 카드로 확인한다(StoreController.dashboard 참고).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ListingDraftScheduler {

	private static final long SCAN_INTERVAL_MS = 60 * 1000L;   // 1분마다 (빠른 감지 및 시연/테스트 반응성 향상)
	// promptTime이 지난 뒤 이 시간 안에만 초안을 만든다 — 서버가 한참 꺼져 있다 켜졌을 때
	// 지난 며칠치가 한꺼번에 쏟아지는 걸 막기 위한 상한.
	private static final int PROMPT_WINDOW_HOURS = 6;
	private static final long URGENT_THRESHOLD_MINUTES = 60;

	private final ListingTemplateRepository templateRepository;
	private final ProductRepository productRepository;
	private final StoreRepository storeRepository;
	private final PosCatalogService posCatalogService;

	@Scheduled(fixedRate = SCAN_INTERVAL_MS)
	@Transactional
	public void scan() {
		scanTemplates();
		scanPosStockSnapshots();
	}

	// --- 방식 1: 템플릿 (POS 없는 매장) --------------------------------
	private void scanTemplates() {
		LocalDate today = LocalDate.now();
		int isoToday = today.getDayOfWeek().getValue();   // 1=월 … 7=일
		LocalTime now = LocalTime.now();

		for (ListingTemplateEntity template : templateRepository.findByActiveTrue()) {
			if (!ListingTemplateService.weekdayMatches(template.getWeekdays(), isoToday)) {
				continue;
			}
			// "알림 시각이 지났고, 지난 지 PROMPT_WINDOW_HOURS 이내인가" — 같은 날 기준으로만 본다
			// (자정을 넘기면 그날치는 포기 — LocalTime.plusHours로 계산하면 23:00+6h=05:00으로 wrap돼서
			//  버그가 났었다). 분 단위로 비교.
			int minutesSincePrompt = now.toSecondOfDay() / 60 - template.getPromptTime().toSecondOfDay() / 60;
			if (minutesSincePrompt < 0 || minutesSincePrompt > PROMPT_WINDOW_HOURS * 60) {
				continue;
			}
			if (createdTodayFor(template, today)) {
				continue;
			}

			StoreEntity store = storeRepository.findById(template.getStoreId()).orElse(null);
			if (store == null) {
				continue;
			}

			int discountedPrice = calcDiscountedPrice(store, template.getOriginalPrice());
			ProductEntity draft = ProductEntity.builder()
					.storeId(template.getStoreId())
					.templateId(template.getId())
					.name(template.getName())
					.originalPrice(template.getOriginalPrice())
					.discountedPrice(discountedPrice)
					.quantity(template.getDefaultQuantity())
					.remainingQuantity(template.getDefaultQuantity())
					.description(template.getDescription())
					.imageUrl(template.getImageUrl())
					.status("draft")
					.build();
			productRepository.save(draft);
			log.info("오늘의 구제 초안 생성(템플릿): templateId={}, productId={}, storeId={}",
					template.getId(), draft.getId(), template.getStoreId());
		}
	}

	// --- 방식 2: POS 재고 스냅샷 (B안, "미리 만들어 파는 집") ----------
	private void scanPosStockSnapshots() {
		LocalTime now = LocalTime.now();
		for (StoreEntity store : storeRepository.findByPosProviderIsNotNullAndPosDraftPromptTimeIsNotNull()) {
			long minutesSince = ChronoUnit.MINUTES.between(store.getPosDraftPromptTime(), now);
			// 같은 날 기준으로 시각이 지났고 PROMPT_WINDOW_HOURS 이내일 때만
			if (minutesSince < 0 || minutesSince > PROMPT_WINDOW_HOURS * 60L) {
				continue;
			}
			int created = posCatalogService.generateDraftsFromStock(store);
			if (created > 0) {
				log.info("오늘의 구제 초안 생성(POS 재고): storeId={}, 생성={}", store.getId(), created);
			}
		}
	}

	/** 오늘 이 템플릿으로 만든 상품(초안이든 이미 발행됐든)이 있으면 다시 안 만든다. */
	private boolean createdTodayFor(ListingTemplateEntity template, LocalDate today) {
		return productRepository.findByStoreId(template.getStoreId()).stream()
				.anyMatch(p -> template.getId().equals(p.getTemplateId())
						&& p.getRegisteredAt() != null
						&& p.getRegisteredAt().toLocalDate().equals(today));
	}

	private int calcDiscountedPrice(StoreEntity store, int originalPrice) {
		StoreHoursUtil.ClosingInfo closingInfo = StoreHoursUtil.parse(store.getOperatingHours(), URGENT_THRESHOLD_MINUTES);
		int rate = DiscountRateCalculator.calculateRate(closingInfo.closeAt());
		return DiscountRateCalculator.applyDiscount(originalPrice, rate);
	}
}

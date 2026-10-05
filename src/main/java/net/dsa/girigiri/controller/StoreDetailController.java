package net.dsa.girigiri.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.entity.ProductEntity;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.service.CouponService;
import net.dsa.girigiri.service.LikeService;
import net.dsa.girigiri.service.LookupService;
import net.dsa.girigiri.service.ReservationService;
import net.dsa.girigiri.service.ReviewService;
import net.dsa.girigiri.service.StoreAnnouncementService;
import net.dsa.girigiri.service.StoreDetailService;
import net.dsa.girigiri.util.CategoryDisplayUtil;
import net.dsa.girigiri.util.DiscountRateCalculator;
import net.dsa.girigiri.util.StoreHoursUtil;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Set;

/**
 * 소비자용 "가게 상세" 화면. /store/** (사장님용 대시보드, 송채현/김태훈 담당)와는 별개 라우팅이다.
 */
@Controller
@RequestMapping("/user/stores")
@RequiredArgsConstructor
public class StoreDetailController {


	private final LikeService likeService;
	private final CouponService couponService;
	private final ReviewService reviewService;
	private final LookupService lookupService;
	private final StoreDetailService storeDetailService;
	private final ReservationService reservationService;
	private final StoreAnnouncementService storeAnnouncementService;

	private static final Set<String> VALID_TABS = Set.of("info", "product", "review");

	// 추가됨 (2026-10-05, 문창호) — 구매내역(reservationView/myReservations.html)의 "리뷰 남기기"에서
	// ?tab=review&reservationId=N으로 들어오면 리뷰 탭이 바로 펼쳐진 채로 시작하게 한다. detail.html의
	// th:with(defaultTab) 지역변수 계산식에 requestedTab이란 이름으로 끼워 넣는다(모델 attribute를
	// "defaultTab"으로 직접 넣으면 그 지역변수에 가려져 무시된다 — th:with가 동명의 model attribute보다
	// 우선순위가 높음, 직접 재현해서 확인함).
	@GetMapping("/{id}")
	public String detail(@PathVariable Long id, @RequestParam(required = false) String tab,
	                     HttpSession session, Model model) {
		StoreEntity store = lookupService.getStore(id);

		List<ProductEntity> activeProducts = storeDetailService.getActiveProducts(id);

		StoreHoursUtil.ClosingInfo closingInfo = StoreHoursUtil.parse(store.getOperatingHours(), StoreHoursUtil.URGENT_THRESHOLD_MINUTES);

		Long userId = (Long) session.getAttribute("userId");
		String role = (String) session.getAttribute("role");

		model.addAttribute("store", store);
		// 추가됨 (2026-10-01, 매장 지정 쿠폰) — 이 매장을 대상으로 지금 받을 수 있는 쿠폰 카드 목록.
		model.addAttribute("storeCouponOffers", couponService.findStoreCouponOffers(id, userId));
		model.addAttribute("avgRating", String.format("%.1f", reviewService.getAverageRating(id)));
		model.addAttribute("reviewCount", reviewService.getReviewCount(id));
		model.addAttribute("reviews", reviewService.getReviews(id, userId, role));
		// 강노은: AI 리뷰 요약 (리뷰 10건 이상일 때만 값이 채워짐 — ReviewService.getReviewSummary 참고).
		model.addAttribute("reviewSummary", reviewService.getReviewSummary(id).orElse(null));
		model.addAttribute("loggedIn", userId != null);
		// 강노은 (2026-10-01): "매장당 리뷰 1건" → "픽업완료 예약당 1건"으로 바뀌면서 myRating==0 체크
		// 대신, 아직 리뷰 안 쓴 픽업완료 예약 목록을 보여주고 어느 구매에 대한 리뷰인지 직접 고르게 한다.
		model.addAttribute("reviewableReservations", reviewService.getReviewableReservations(userId, id));
		model.addAttribute("closingLabel", closingInfo.label());
		model.addAttribute("products", activeProducts.stream().map(this::toProductRow).toList());
		model.addAttribute("liked", likeService.isLiked(userId, id));
		model.addAttribute("thumbColor", thumbColor(store.getCategory()));
		model.addAttribute("thumbEmoji", thumbEmoji(store.getCategory()));
		// 추가됨 (2026-09-14, 매장 신뢰도 점수 기능, 담당: 송채현) — 손님이 주문하기 전에 "이 가게가
		// 결제 완료된 주문을 가게 사정으로 얼마나 자주 취소하는지" 참고할 수 있게 보여준다. 새 계산
		// 로직이 아니라 원래 있던 ReservationService.getStoreCancelStats(매장 취소율) 그대로 재사용.
		model.addAttribute("storeReliability", reservationService.getStoreCancelStats(id));
		// 추가됨 (2026-09-29) — 배민 스타일 슬림 띠 배너: 점주가 노출 중으로 지정한 공지 1건만 단정하게 전달한다.
		model.addAttribute("storeAnnouncements", storeAnnouncementService.getExposedForConsumer(id));
		model.addAttribute("requestedTab", tab != null && VALID_TABS.contains(tab) ? tab : null);
		return "storeView/detail";
	}

	// 변경됨 (2026-09-08, 코드 감사) — CategoryDisplayUtil로 위임(6곳 넘게 중복돼 있던 것 중 하나,
	// StoreProductController 사본에만 있던 "카페/디저트"·"도시락/샐러드" 변형 인식도 같이 딸려온다).
	private String thumbColor(String category) {
		return CategoryDisplayUtil.thumbColor(category);
	}

	private String thumbEmoji(String category) {
		return CategoryDisplayUtil.thumbEmoji(category);
	}

	private ProductRow toProductRow(ProductEntity product) {
		int discountRate = DiscountRateCalculator.fromPrices(product.getOriginalPrice(), product.getDiscountedPrice());
		return new ProductRow(product.getId(), product.getName(),
				formatWon(product.getOriginalPrice()), formatWon(product.getDiscountedPrice()),
				"-" + discountRate + "%", product.getRemainingQuantity());
	}

	private String formatWon(Integer price) {
		return price == null ? "0원" : String.format("%,d원", price);
	}

	public record ProductRow(Long id, String name, String origPrice, String salePrice, String discountRate, Integer remainingQuantity) {
	}
}

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

	@GetMapping("/{id}")
	public String detail(@PathVariable Long id, @RequestParam(required = false) String tab,
	                     @RequestParam(required = false) Long reservationId,
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
		// 변경됨 (2026-10-05, 사용자 요청) — "가게당 리뷰 1개"에서 "주문당 리뷰 1개"로 바뀌면서,
		// "내 리뷰 하나"를 가게 단위로 더 이상 특정할 수 없다(여러 개일 수 있음). 리뷰 작성은 이제
		// 구매내역(myReservations.html)의 "리뷰 남기기"에서 reservationId를 들고 들어올 때만
		// 가능하다 — 그 주문이 자격(픽업완료+72시간 이내+아직 리뷰 없음)을 충족해야 폼이 뜬다.
		// reservationId 없이 이 페이지에 오면(가게를 그냥 둘러보는 경우) 작성 폼 없이 목록만 보여준다.
		model.addAttribute("reviewReservationId", reservationId);
		model.addAttribute("canWriteForReservation",
				reservationId != null && reviewService.canWriteReviewForReservation(userId, id, reservationId));
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
		// 추가됨 (2026-10-05) — 왜: 구매내역(픽업완료 탭)에 "리뷰 남기기" 링크를 새로 만들면서, 이
		// 페이지에 도착했을 때 리뷰 탭이 바로 펼쳐져 있어야 했다. detail.html은 th:with로 지역변수
		// defaultTab을 "reviewMessage 있으면 review, 아니면 product"로 이미 계산하고 있어서(강노은,
		// 상단 주석 참고) 모델 attribute로 "defaultTab"을 넣어봤자 그 지역변수에 가려져 무시된다
		// (th:with 지역변수가 동명의 model attribute보다 우선순위가 높다 — 처음엔 이걸 놓쳐서 직접
		// "defaultTab"을 채웠다가 실제로는 아무 효과가 없는 걸 사용자 재현으로 확인했다). 그래서
		// 이름을 다르게 "requestedTab"으로 둬서 그 지역변수 계산식 안에 명시적으로 끼워 넣는다
		// (detail.html의 th:with 수정 참고) — ?tab= 쿼리파라미터가 없으면 null이라 기존 동작(상품 탭
		// 기본, 리뷰 작성/수정 직후엔 리뷰 탭)은 전혀 안 바뀐다.
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

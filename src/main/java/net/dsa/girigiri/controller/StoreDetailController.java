package net.dsa.girigiri.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.entity.ProductEntity;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.repository.ProductRepository;
import net.dsa.girigiri.repository.StoreRepository;
import net.dsa.girigiri.service.LikeService;
import net.dsa.girigiri.service.ReviewService;
import net.dsa.girigiri.util.StoreHoursUtil;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * 소비자용 "가게 상세" 화면. /store/** (사장님용 대시보드, 송채현/김태훈 담당)와는 별개 라우팅이다.
 */
@Controller
@RequestMapping("/user/stores")
@RequiredArgsConstructor
public class StoreDetailController {

	private static final long URGENT_THRESHOLD_MINUTES = 60;

	private final StoreRepository storeRepository;
	private final ProductRepository productRepository;
	private final LikeService likeService;
	private final ReviewService reviewService;

	@GetMapping("/{id}")
	public String detail(@PathVariable Long id, HttpSession session, Model model) {
		StoreEntity store = storeRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "가게를 찾을 수 없습니다: " + id));

		List<ProductEntity> activeProducts = productRepository.findAll().stream()
				.filter(p -> id.equals(p.getStoreId()))
				.filter(p -> "active".equals(p.getStatus()))
				.filter(p -> p.getRemainingQuantity() != null && p.getRemainingQuantity() > 0)
				.toList();

		StoreHoursUtil.ClosingInfo closingInfo = StoreHoursUtil.parse(store.getOperatingHours(), URGENT_THRESHOLD_MINUTES);

		Long userId = (Long) session.getAttribute("userId");
		String role = (String) session.getAttribute("role");
		var myReview = reviewService.getMyReview(userId, id);

		model.addAttribute("store", store);
		model.addAttribute("avgRating", String.format("%.1f", reviewService.getAverageRating(id)));
		model.addAttribute("reviewCount", reviewService.getReviewCount(id));
		model.addAttribute("reviews", reviewService.getReviews(id, userId, role));
		model.addAttribute("loggedIn", userId != null);
		model.addAttribute("myRating", myReview.map(r -> r.getRating()).orElse(0));
		model.addAttribute("myContent", myReview.map(r -> r.getContent()).orElse(""));
		model.addAttribute("myImageUrl", myReview.map(r -> r.getImageUrl()).orElse(""));
		model.addAttribute("closingLabel", closingInfo.label());
		model.addAttribute("products", activeProducts.stream().map(this::toProductRow).toList());
		model.addAttribute("liked", likeService.isLiked(userId, id));
		model.addAttribute("thumbColor", thumbColor(store.getCategory()));
		model.addAttribute("thumbEmoji", thumbEmoji(store.getCategory()));
		return "storeView/detail";
	}

	private String thumbColor(String category) {
		if (category == null) {
			return "var(--c-line-weak)";
		}
		return switch (category) {
			case "베이커리" -> "var(--c-accent-weak)";
			case "카페" -> "var(--c-info-weak)";
			case "반찬", "도시락" -> "var(--c-primary-weak)";
			default -> "var(--c-line-weak)";
		};
	}

	private String thumbEmoji(String category) {
		if (category == null) {
			return "🍽️";
		}
		return switch (category) {
			case "베이커리" -> "🥐";
			case "반찬" -> "🍚";
			case "도시락" -> "🍱";
			case "카페" -> "☕";
			default -> "🍽️";
		};
	}

	private ProductRow toProductRow(ProductEntity product) {
		int discountRate = discountRate(product);
		return new ProductRow(product.getId(), product.getName(),
				formatWon(product.getOriginalPrice()), formatWon(product.getDiscountedPrice()),
				"-" + discountRate + "%", product.getRemainingQuantity());
	}

	private int discountRate(ProductEntity product) {
		if (product.getOriginalPrice() == null || product.getOriginalPrice() == 0 || product.getDiscountedPrice() == null) {
			return 0;
		}
		return (int) Math.round(100.0 * (product.getOriginalPrice() - product.getDiscountedPrice()) / product.getOriginalPrice());
	}

	private String formatWon(Integer price) {
		return price == null ? "0원" : String.format("%,d원", price);
	}

	public record ProductRow(Long id, String name, String origPrice, String salePrice, String discountRate, Integer remainingQuantity) {
	}
}

package net.dsa.girigiri.controller;

import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.entity.ProductEntity;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.repository.StoreRepository;
import net.dsa.girigiri.service.LookupService;
import net.dsa.girigiri.service.ReviewService;
import net.dsa.girigiri.util.DiscountRateCalculator;
import net.dsa.girigiri.util.StoreHoursUtil;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/user/products")
@RequiredArgsConstructor
public class ProductController {

	private static final long URGENT_THRESHOLD_MINUTES = 60;

	private final StoreRepository storeRepository;
	private final ReviewService reviewService;
	private final LookupService lookupService;

	@GetMapping("/{id}")
	public String detail(@PathVariable Long id, Model model) {
		ProductEntity product = lookupService.getProduct(id);
		StoreEntity store = storeRepository.findById(product.getStoreId()).orElse(null);

		int discountRate = DiscountRateCalculator.fromPrices(product.getOriginalPrice(), product.getDiscountedPrice());
		int savedAmount = DiscountRateCalculator.savedAmount(product.getOriginalPrice(), product.getDiscountedPrice());

		double avgRating = 0;
		int reviewCount = 0;
		if (store != null) {
			avgRating = reviewService.getAverageRating(store.getId());
			reviewCount = reviewService.getReviewCount(store.getId());
		}

		StoreHoursUtil.ClosingInfo closingInfo = store == null
				? new StoreHoursUtil.ClosingInfo("", false, null)
				: StoreHoursUtil.parse(store.getOperatingHours(), URGENT_THRESHOLD_MINUTES);

		model.addAttribute("product", product);
		model.addAttribute("store", store);
		model.addAttribute("discountRate", discountRate);
		model.addAttribute("savedAmount", savedAmount);
		model.addAttribute("avgRating", String.format("%.1f", avgRating));
		model.addAttribute("reviewCount", reviewCount);
		model.addAttribute("pickupLabel", closingInfo.label());
		model.addAttribute("thumbColor", thumbColor(store == null ? null : store.getCategory()));
		model.addAttribute("thumbEmoji", thumbEmoji(store == null ? null : store.getCategory()));
		return "productView/detail";
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
}

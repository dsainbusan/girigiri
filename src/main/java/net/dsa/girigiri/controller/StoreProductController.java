package net.dsa.girigiri.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.ProductFormDto;
import net.dsa.girigiri.domain.dto.StockItemDto;
import net.dsa.girigiri.domain.entity.ProductEntity;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.domain.entity.MenuItemEntity;
import net.dsa.girigiri.domain.entity.ListingTemplateEntity;
import net.dsa.girigiri.repository.ListingTemplateRepository;
import net.dsa.girigiri.repository.StoreRepository;
import net.dsa.girigiri.service.PosCatalogService;
import net.dsa.girigiri.service.ProductService;
import net.dsa.girigiri.util.StoreHoursUtil;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 점주용 상품(재고) 등록/수정/삭제/품절 화면 — WBS 3.0.
 * (원래 CLAUDE.md 역할표상 김태훈 담당 → 2026-08-26 문창호 인계.)
 *
 * 라우팅(/store/products/**)은 StoreController(@RequestMapping("/store"))와 겹치지 않는다.
 * CRUD·소유권 검증·할인가 자동계산·사진 저장은 ProductService가 담당한다.
 */
@Controller
@RequestMapping("/store/products")
@RequiredArgsConstructor
public class StoreProductController {

	private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
	private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("M/d");

	private final ProductService productService;
	private final StoreRepository storeRepository;
	private final PosCatalogService posCatalogService;
	private final ListingTemplateRepository listingTemplateRepository;

	@GetMapping
	public String list(HttpSession session, Model model) {
		Long ownerId = (Long) session.getAttribute("userId");
		if (ownerId == null) {
			return "redirect:/auth/loginForm";
		}
		StoreEntity store = storeRepository.findByOwnerId(ownerId).orElse(null);
		if (store == null) {
			return "redirect:/auth/owner-apply";
		}

		String category = store.getCategory();
		List<ProductEntity> all = productService.listForOwner(ownerId);

		// "오늘의 구제" 초안(status='draft')은 상단 "발행 대기" 섹션에 따로. 'skipped'(오늘 안 함)는 아예 숨김.
		List<StockItemDto> drafts = all.stream()
				.filter(p -> "draft".equals(p.getStatus()))
				.map(p -> toStockItem(p, category))
				.toList();
		List<StockItemDto> items = all.stream()
				.filter(p -> !"draft".equals(p.getStatus()) && !"skipped".equals(p.getStatus()))
				.map(p -> toStockItem(p, category))
				.toList();

		model.addAttribute("drafts", drafts);
		model.addAttribute("items", items);
		model.addAttribute("totalCount", items.size());
		model.addAttribute("sellingCount", items.stream().filter(i -> "selling".equals(i.statusVariant())).count());
		model.addAttribute("soldOutCount", items.stream().filter(i -> "soldout".equals(i.statusVariant())).count());

		// 상단 상태 스트립 — "오늘의 구제 초안"이 어디서 오는지 점주가 알 수 있게 (2026-08-27 통합형 IA).
		boolean posConnected = store.getPosProvider() != null;
		model.addAttribute("posConnected", posConnected);
		model.addAttribute("posProviderLabel", PosCatalogService.providerLabel(store.getPosProvider()));
		model.addAttribute("posDraftPromptLabel",
				store.getPosDraftPromptTime() == null ? null : store.getPosDraftPromptTime().format(TIME_FMT));
		model.addAttribute("hasActiveTemplate",
				listingTemplateRepository.findByStoreId(store.getId()).stream().anyMatch(ListingTemplateEntity::isActive));

		// 마감 10분 전을 넘기면 초안 [바로 올리기]를 닫는다 (손님이 예약·픽업할 시간이 없어서).
		model.addAttribute("canPublishDrafts", StoreHoursUtil.canPublishNow(
				StoreHoursUtil.parse(store.getOperatingHours(), 60).closeAt()));
		model.addAttribute("publishCutoffMinutes", StoreHoursUtil.PUBLISH_CUTOFF_MINUTES);
		return "storeView/products";
	}

	@GetMapping("/new")
	public String createForm(@RequestParam(required = false) Long menuItemId, HttpSession session, Model model) {
		Long ownerId = (Long) session.getAttribute("userId");
		if (ownerId == null) {
			return "redirect:/auth/loginForm";
		}

		ProductFormDto form = ProductFormDto.empty();
		if (menuItemId != null) {
			// POS 카탈로그에서 넘어온 경우 — 품목명·원가·사진 자동완성 (수량만 입력하면 됨)
			MenuItemEntity menu = posCatalogService.getOwnedMenuItem(ownerId, menuItemId);
			form.setName(menu.getName());
			form.setOriginalPrice(menu.getOriginalPrice());
			form.setCurrentImageUrl(menu.getImageUrl());
		}

		model.addAttribute("mode", "create");
		model.addAttribute("isDraft", false);
		model.addAttribute("form", form);
		return "storeView/productForm";
	}

	@PostMapping
	public String create(@ModelAttribute ProductFormDto form,
	                     @RequestParam(required = false) MultipartFile image,
	                     HttpSession session) {
		Long ownerId = (Long) session.getAttribute("userId");
		if (ownerId == null) {
			return "redirect:/auth/loginForm";
		}
		try {
			productService.create(ownerId, form, image);
		} catch (ResponseStatusException e) {
			boolean rateIssue = e.getReason() != null && e.getReason().contains("할인율");
			return "redirect:/store/products/new?error" + (rateIssue ? "=rate" : "");
		}
		return "redirect:/store/products";
	}

	@GetMapping("/{id}/edit")
	public String editForm(@PathVariable Long id, HttpSession session, Model model) {
		Long ownerId = (Long) session.getAttribute("userId");
		if (ownerId == null) {
			return "redirect:/auth/loginForm";
		}
		ProductEntity product = productService.getOwnedProduct(ownerId, id);

		ProductFormDto form = new ProductFormDto();
		form.setName(product.getName());
		form.setOriginalPrice(product.getOriginalPrice());
		form.setQuantity(product.getQuantity());
		form.setDescription(product.getDescription());
		form.setCurrentImageUrl(product.getImageUrl());
		// 할인율은 따로 저장 안 하므로 원가·할인가로 역산한다. 단 "지금 자동값보다 큰 값"(=점주가 일부러
		// 더 깎은 값)일 때만 채워서 유지하고, 그 이하면 비워둔다 — 예전 자동값이 현재 자동값보다 낮아
		// 저장이 거부되는 걸 막기 위해(비어 있으면 저장 시 현재 자동값으로 재계산).
		StoreEntity ownStore = storeRepository.findByOwnerId(ownerId).orElse(null);
		if (ownStore != null && product.getOriginalPrice() != null && product.getOriginalPrice() > 0
				&& product.getDiscountedPrice() != null) {
			int rate = (int) Math.round(100.0 * (product.getOriginalPrice() - product.getDiscountedPrice()) / product.getOriginalPrice());
			int autoRate = net.dsa.girigiri.util.DiscountRateCalculator.calculateRate(
					StoreHoursUtil.parse(ownStore.getOperatingHours(), 60).closeAt());
			if (rate > autoRate) {
				form.setDiscountRate(String.valueOf(rate));
			}
		}

		model.addAttribute("mode", "edit");
		model.addAttribute("productId", id);
		model.addAttribute("isDraft", "draft".equals(product.getStatus()));
		model.addAttribute("form", form);
		return "storeView/productForm";
	}

	@PostMapping("/{id}")
	public String update(@PathVariable Long id,
	                     @ModelAttribute ProductFormDto form,
	                     @RequestParam(required = false) MultipartFile image,
	                     @RequestParam(defaultValue = "false") boolean removeImage,
	                     @RequestParam(defaultValue = "false") boolean publishAfter,
	                     HttpSession session) {
		Long ownerId = (Long) session.getAttribute("userId");
		if (ownerId == null) {
			return "redirect:/auth/loginForm";
		}
		boolean publishedOk = true;
		try {
			productService.update(ownerId, id, form, image, removeImage);
			if (publishAfter) {
				publishedOk = productService.publishDraft(ownerId, id);   // "수정 후 발행"
			}
		} catch (ResponseStatusException e) {
			boolean rateIssue = e.getReason() != null && e.getReason().contains("할인율");
			return "redirect:/store/products/" + id + "/edit?error" + (rateIssue ? "=rate" : "");
		}
		return publishedOk ? "redirect:/store/products" : "redirect:/store/products?tooLate";
	}

	@PostMapping("/{id}/publish")
	public String publish(@PathVariable Long id, HttpSession session) {
		Long ownerId = (Long) session.getAttribute("userId");
		if (ownerId == null) {
			return "redirect:/auth/loginForm";
		}
		boolean ok = productService.publishDraft(ownerId, id);
		return ok ? "redirect:/store/products" : "redirect:/store/products?tooLate";
	}

	@PostMapping("/{id}/discard")
	public String discard(@PathVariable Long id, HttpSession session) {
		Long ownerId = (Long) session.getAttribute("userId");
		if (ownerId == null) {
			return "redirect:/auth/loginForm";
		}
		productService.discardDraft(ownerId, id);
		return "redirect:/store/products";
	}

	@PostMapping("/{id}/soldout")
	public String soldOut(@PathVariable Long id, HttpSession session) {
		Long ownerId = (Long) session.getAttribute("userId");
		if (ownerId == null) {
			return "redirect:/auth/loginForm";
		}
		productService.markSoldOut(ownerId, id);
		return "redirect:/store/products";
	}

	@PostMapping("/{id}/resume")
	public String resume(@PathVariable Long id, HttpSession session) {
		Long ownerId = (Long) session.getAttribute("userId");
		if (ownerId == null) {
			return "redirect:/auth/loginForm";
		}
		productService.resumeSelling(ownerId, id);
		return "redirect:/store/products";
	}

	@PostMapping("/{id}/delete")
	public String delete(@PathVariable Long id, HttpSession session) {
		Long ownerId = (Long) session.getAttribute("userId");
		if (ownerId == null) {
			return "redirect:/auth/loginForm";
		}
		productService.delete(ownerId, id);
		return "redirect:/store/products";
	}

	// ---------------------------------------------------------------------

	private StockItemDto toStockItem(ProductEntity p, String category) {
		int discountRate = (p.getOriginalPrice() == null || p.getOriginalPrice() == 0 || p.getDiscountedPrice() == null)
				? 0
				: (int) Math.round(100.0 * (p.getOriginalPrice() - p.getDiscountedPrice()) / p.getOriginalPrice());

		boolean hasStock = p.getRemainingQuantity() != null && p.getRemainingQuantity() > 0;
		String statusLabel;
		String statusVariant;
		if ("expired".equals(p.getStatus())) {
			statusLabel = "마감";
			statusVariant = "closed";
		} else if ("sold".equals(p.getStatus()) || !hasStock) {
			statusLabel = "품절";
			statusVariant = "soldout";
		} else {
			statusLabel = "판매중";
			statusVariant = "selling";
		}

		String source = p.getMenuItemId() != null ? "pos"
				: p.getTemplateId() != null ? "template"
				: "manual";

		return new StockItemDto(
				p.getId(),
				p.getName(),
				p.getImageUrl(),
				categoryEmoji(category),
				categoryColor(category),
				statusLabel,
				statusVariant,
				"sold".equals(p.getStatus()),   // manualSoldOut — 사장님이 직접 품절 처리한 것
				discountRate,
				nz(p.getOriginalPrice()),
				nz(p.getDiscountedPrice()),
				nz(p.getRemainingQuantity()),
				nz(p.getQuantity()),
				registeredLabel(p.getRegisteredAt()),
				source);
	}

	private String registeredLabel(LocalDateTime registeredAt) {
		if (registeredAt == null) {
			return "";
		}
		if (registeredAt.toLocalDate().equals(LocalDate.now())) {
			return "오늘 " + registeredAt.format(TIME_FMT) + " 등록";
		}
		return registeredAt.format(DATE_FMT) + " 등록";
	}

	private String categoryEmoji(String category) {
		if (category == null) {
			return "🍽️";
		}
		return switch (category) {
			case "베이커리" -> "🥐";
			case "반찬" -> "🍚";
			case "도시락", "도시락/샐러드" -> "🍱";
			case "카페", "카페/디저트" -> "☕";
			default -> "🍽️";
		};
	}

	private String categoryColor(String category) {
		if (category == null) {
			return "var(--c-line-weak)";
		}
		return switch (category) {
			case "베이커리" -> "var(--c-accent-weak)";
			case "카페", "카페/디저트" -> "var(--c-info-weak)";
			case "반찬", "도시락", "도시락/샐러드" -> "var(--c-primary-weak)";
			default -> "var(--c-line-weak)";
		};
	}

	private int nz(Integer v) {
		return v == null ? 0 : v;
	}
}

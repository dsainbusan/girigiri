package net.dsa.girigiri.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.security.LoginRequired;
import net.dsa.girigiri.domain.dto.ProductFormDto;
import net.dsa.girigiri.domain.dto.StockItemDto;
import net.dsa.girigiri.domain.entity.ProductEntity;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.domain.entity.MenuItemEntity;
import net.dsa.girigiri.service.PosCatalogService;
import net.dsa.girigiri.service.ProductService;
import net.dsa.girigiri.service.StoreAccessService;
import net.dsa.girigiri.service.StoreProductService;
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
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

/**
 * 점주용 상품(재고) 목록/등록/수정 화면 — WBS 3.0.
 * (원래 CLAUDE.md 역할표상 김태훈 담당 → 2026-08-26 문창호 인계.)
 *
 * 라우팅(/store/products/**)은 StoreController(@RequestMapping("/store"))와 겹치지 않는다.
 * CRUD·소유권 검증·할인가 자동계산·사진 저장은 ProductService가 담당한다.
 *
 * 2026-09-08 — 발행/보류/품절/판매재개/삭제 같은 단순 상태 전환 액션 5개는
 * StoreProductActionController로 분리했다(코드 감사에서 이 파일이 300줄 넘김 지적, 레이어 규칙
 * 정리와 같은 취지). URL은 그대로라 하나도 안 바뀐다.
 */
@Controller
@RequestMapping("/store/products")
@RequiredArgsConstructor
@LoginRequired   // /store/** 로그인 강제는 LoginRequiredInterceptor가 담당 (2026-09-09 문창호)
public class StoreProductController {

	private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

	private final ProductService productService;
	private final StoreAccessService storeAccessService;
	private final PosCatalogService posCatalogService;
	private final StoreProductService storeProductService;

	@GetMapping
	public String list(HttpSession session, Model model) {
		Long ownerId = (Long) session.getAttribute("userId");
		StoreEntity store = storeAccessService.findMyStore(ownerId).orElse(null);
		if (store == null) {
			return "redirect:/auth/owner-apply";
		}

		String category = store.getCategory();
		List<ProductEntity> all = productService.listForOwner(ownerId);

		// "오늘의 구제" 초안(status='draft')은 상단 "발행 대기" 섹션에 따로. 'skipped'(오늘 안 함)는 아예 숨김.
		List<StockItemDto> drafts = all.stream()
				.filter(p -> "draft".equals(p.getStatus()))
				.map(p -> productService.toStockItem(p, category))
				.toList();
		// 이 화면은 "오늘의 구제" — 오늘 올릴 상품과 오늘 판매 중인 재고를 보는 곳이라 오늘 등록된 상품만 보여준다
		// (2026-09-21). "오늘의 구제 상품은 그날 장사용"(ListingDraftScheduler)이라 지난 날 상품은 이미 판매가
		// 끝난 기록이고, 그 이력은 판매/폐기 리포트·정산에서 본다. 오늘 마감 시간이 지나 끝난 상품은 오늘 재고
		// 현황의 일부라 남겨서 맨 아래 별도 구간으로 보낸다(sorted는 안정 정렬이라 각 구간 안의 순서는 그대로).
		LocalDate today = LocalDate.now();
		List<StockItemDto> items = all.stream()
				.filter(p -> !"draft".equals(p.getStatus()) && !"skipped".equals(p.getStatus()))
				.filter(p -> p.getRegisteredAt() != null && p.getRegisteredAt().toLocalDate().equals(today))
				.map(p -> productService.toStockItem(p, category))
				.sorted(Comparator.comparing(i -> "closed".equals(i.statusVariant())))
				.toList();
		long closedCount = items.stream().filter(i -> "closed".equals(i.statusVariant())).count();

		model.addAttribute("drafts", drafts);
		model.addAttribute("items", items);
		model.addAttribute("totalCount", items.size() - closedCount);
		model.addAttribute("closedCount", closedCount);
		model.addAttribute("sellingCount", items.stream().filter(i -> "selling".equals(i.statusVariant())).count());
		model.addAttribute("soldOutCount", items.stream().filter(i -> "soldout".equals(i.statusVariant())).count());

		// 상단 상태 스트립 — "오늘의 구제 초안"이 어디서 오는지 점주가 알 수 있게 (2026-08-27 통합형 IA).
		boolean posConnected = store.getPosProvider() != null;
		model.addAttribute("posConnected", posConnected);
		model.addAttribute("posProviderLabel", PosCatalogService.providerLabel(store.getPosProvider()));
		model.addAttribute("posDraftPromptLabel",
				store.getPosDraftPromptTime() == null ? null : store.getPosDraftPromptTime().format(TIME_FMT));
		model.addAttribute("hasActiveTemplate", storeProductService.hasActiveTemplate(store.getId()));

		// 마감 10분 전을 넘기면 초안 [바로 올리기]를 닫는다 (손님이 예약·픽업할 시간이 없어서).
		model.addAttribute("canPublishDrafts", StoreHoursUtil.canPublishNow(
				StoreHoursUtil.parse(store.getOperatingHours(), 60).closeAt()));
		model.addAttribute("publishCutoffMinutes", StoreHoursUtil.PUBLISH_CUTOFF_MINUTES);
		return "storeView/products";
	}

	@GetMapping("/new")
	public String createForm(@RequestParam(required = false) Long menuItemId, HttpSession session, Model model) {
		Long ownerId = (Long) session.getAttribute("userId");

		ProductFormDto form = ProductFormDto.empty();
		if (menuItemId != null) {
			// POS 카탈로그에서 넘어온 경우 — 품목명·원가·사진 자동완성 (수량만 입력하면 됨)
			MenuItemEntity menu = posCatalogService.getOwnedMenuItem(ownerId, menuItemId);
			form.setName(menu.getName());
			form.setOriginalPrice(menu.getOriginalPrice());
			form.setCurrentImageUrl(menu.getImageUrl());
		}

		// 추가됨 (2026-09-17) — 품목명을 매번 직접 타이핑하지 않고, POS 카탈로그에 이미 있는 메뉴
		// 중에서 골라 쓸 수 있게(선택하면 품목명·원가·사진 자동완성). POS 연동을 안 했거나 메뉴가
		// 없으면 목록이 비어서 화면에서 "직접 입력"만 남는다 — 별도 분기 없이 자연스럽게 처리된다.
		// posConnect.html "지금 직접 등록하기"(?menuItemId=)로 들어온 경우, select도 그 메뉴를
		// 미리 선택된 상태로 보여줘야 한다 — 안 그러면 입력칸엔 이름이 채워져 있는데 select만
		// "항목을 선택하세요" 플레이스홀더로 보이는 모순이 생긴다.
		model.addAttribute("menuItems", posCatalogService.listMenu(ownerId));
		model.addAttribute("selectedMenuItemId", menuItemId);

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
		ProductEntity product = productService.getOwnedProduct(ownerId, id);

		ProductFormDto form = new ProductFormDto();
		form.setName(product.getName());
		form.setOriginalPrice(product.getOriginalPrice());
		form.setQuantity(product.getQuantity());
		form.setDescription(product.getDescription());
		form.setCurrentImageUrl(product.getImageUrl());
		// 점주가 직접 지정한 할인율이 있으면(ownerDiscountRate) 그 값을 채워서 유지하고, 없으면(자동) 비워둔다 —
		// 비어 있으면 저장 시 현재 자동값으로 다시 계산된다. (2026-09-21 — 예전엔 가격에서 역산해 "자동값보다 큰
		// 값이면 직접 지정"으로 추측했는데, 동적 가격이 되면서 자동값도 계속 바뀌어 추측이 안 맞는다.)
		if (product.getOwnerDiscountRate() != null) {
			form.setDiscountRate(String.valueOf(product.getOwnerDiscountRate()));
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

	// ---------------------------------------------------------------------
	// 발행/보류/품절/판매재개/삭제 액션은 StoreProductActionController로 옮겼다
	// (2026-09-08, 코드 감사 — 이 파일이 자체 300줄 분리 기준을 넘겨서 분리).
	// 재고 카드 DTO 매핑(toStockItem 등)은 ProductService로 옮겼다 (2026-09-09, 레이어 규칙 —
	// 슈퍼어드민 "매장 재고 현황" 화면도 같은 매핑을 재사용하기 위해).
}

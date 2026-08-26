package net.dsa.girigiri.controller;

import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.ProductFormDto;
import net.dsa.girigiri.domain.dto.StockItemDto;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

/**
 * 점주용 상품(재고) 등록/수정/삭제/품절 화면 — WBS 3.0.
 * (원래 CLAUDE.md 역할표상 김태훈 담당 → 2026-08-26 문창호 인계.)
 *
 * ⚠️ 현재 단계: 목업(화면 확인용). GET 매핑만 있고 전부 하드코딩 샘플 데이터를 렌더한다.
 *   다음 단계에서 이 컨트롤러에 아래를 채운다:
 *     - 세션 점주 인증 (userId -> StoreRepository.findByOwnerId, approvalStatus == APPROVED)
 *     - 실데이터 조회 (ProductRepository.findByStoreId) + 할인가 자동계산 (DiscountRateCalculator)
 *     - POST /store/products              (등록, multipart 사진 업로드)
 *     - POST /store/products/{id}          (수정)
 *     - POST /store/products/{id}/soldout  (품절 처리)
 *     - POST /store/products/{id}/delete   (삭제)
 *   라우팅(/store/products/**)은 StoreController(@RequestMapping("/store"))와 겹치지 않는다.
 */
@Controller
@RequestMapping("/store/products")
@RequiredArgsConstructor
public class StoreProductController {

	@GetMapping
	public String list(Model model) {
		List<StockItemDto> items = sampleItems();
		model.addAttribute("items", items);
		model.addAttribute("totalCount", items.size());
		model.addAttribute("sellingCount", items.stream().filter(i -> "selling".equals(i.statusVariant())).count());
		model.addAttribute("soldOutCount", items.stream().filter(i -> "soldout".equals(i.statusVariant())).count());
		return "storeView/products";
	}

	@GetMapping("/new")
	public String createForm(Model model) {
		model.addAttribute("mode", "create");
		model.addAttribute("form", ProductFormDto.empty());
		return "storeView/productForm";
	}

	@GetMapping("/{id}/edit")
	public String editForm(@PathVariable Long id, Model model) {
		ProductFormDto form = new ProductFormDto();
		form.setName("오늘의 모둠빵 세트");
		form.setOriginalPrice(12000);
		form.setQuantity(10);
		form.setDescription("크루아상, 소금빵, 단팥빵 등 그날 구운 빵을 랜덤으로 담아드려요.");
		model.addAttribute("mode", "edit");
		model.addAttribute("productId", id);
		model.addAttribute("form", form);
		return "storeView/productForm";
	}

	/** 목업용 샘플. 실데이터 배선 시 통째로 제거하고 ProductRepository 조회로 교체한다. */
	private List<StockItemDto> sampleItems() {
		return List.of(
				new StockItemDto(1L, "오늘의 모둠빵 세트", null, "🥐", "var(--c-accent-weak)",
						"판매중", "selling", 30, 12000, 8400, 3, 10, "오늘 14:20 등록"),
				new StockItemDto(2L, "샌드위치 2종 세트", null, "🥪", "var(--c-primary-weak)",
						"판매중", "selling", 10, 7000, 6300, 6, 8, "오늘 11:05 등록"),
				new StockItemDto(3L, "수제 쿠키 박스", null, "🍪", "var(--c-info-weak)",
						"품절", "soldout", 50, 9000, 4500, 0, 5, "오늘 09:30 등록")
		);
	}
}

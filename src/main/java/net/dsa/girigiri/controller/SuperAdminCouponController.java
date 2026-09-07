package net.dsa.girigiri.controller;

import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.CouponCampaignRowDto;
import net.dsa.girigiri.domain.entity.CouponPolicyEntity;
import net.dsa.girigiri.service.CouponService;
import net.dsa.girigiri.service.SuperAdminCouponService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 슈퍼어드민(플랫폼 운영자) "프로모션 쿠폰 캠페인 관리" 화면 라우팅 — 2026-09-07 신규 (송채현).
 * SuperAdminNoticeController와 동일한 패턴(얇은 컨트롤러, 판정은 서비스가 함).
 *
 * 추가됨 (2026-09-07, 채채 확인) — 웰컴/매장취소보상 쿠폰의 할인율(정책값)도 이 화면에서 같이
 * 수정할 수 있게 했다. 프로모션 캠페인(CouponCampaignEntity, 여러 건)과 달리 정책값은 딱 하나뿐이라
 * 별도 페이지 없이 목록 화면 위에 작은 폼으로 넣었다 — CouponService가 그 값을 담당한다.
 */
@Controller
@RequestMapping("/superadmin/coupons")
@RequiredArgsConstructor
public class SuperAdminCouponController {

	private final SuperAdminCouponService couponService;
	private final CouponService memberCouponService;

	@GetMapping
	public String list(Model model) {
		List<CouponCampaignRowDto> campaigns = couponService.listAllSortedByNewest();
		CouponPolicyEntity policy = memberCouponService.getOrCreatePolicy();
		model.addAttribute("campaigns", campaigns);
		model.addAttribute("policy", policy);
		return "superAdminView/coupons";
	}

	@PostMapping("/policy")
	public String updatePolicy(@RequestParam Integer welcomeDiscountRate,
	                            @RequestParam Integer compensationDiscountRate) {
		CouponService.PolicyUpdateResult result = memberCouponService.updatePolicy(welcomeDiscountRate, compensationDiscountRate);
		return switch (result) {
			case INVALID -> "redirect:/superadmin/coupons?error=policy";
			case SUCCESS -> "redirect:/superadmin/coupons?saved";
		};
	}

	@GetMapping("/new")
	public String createForm() {
		return "superAdminView/couponNew";
	}

	@PostMapping("/new")
	public String create(@RequestParam String name,
	                     @RequestParam String code,
	                     @RequestParam(required = false) Integer discountRate,
	                     @RequestParam(required = false) String expiresAt) {
		SuperAdminCouponService.SaveResult result = couponService.create(name, code, discountRate, expiresAt);
		return switch (result) {
			case INVALID -> "redirect:/superadmin/coupons/new?error";
			case DUPLICATE_CODE -> "redirect:/superadmin/coupons/new?error=code";
			case SUCCESS -> "redirect:/superadmin/coupons";
		};
	}

	@PostMapping("/{id}/toggle")
	public String toggle(@PathVariable Long id) {
		couponService.toggleActive(id);
		return "redirect:/superadmin/coupons";
	}

	@PostMapping("/{id}/delete")
	public String delete(@PathVariable Long id) {
		couponService.delete(id);
		return "redirect:/superadmin/coupons";
	}
}

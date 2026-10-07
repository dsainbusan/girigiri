package net.dsa.girigiri.controller;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.CouponRowDto;
import net.dsa.girigiri.domain.entity.CouponCampaignEntity;
import net.dsa.girigiri.security.LoginRequired;
import net.dsa.girigiri.service.CouponService;
import net.dsa.girigiri.service.SuperAdminCouponService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * 회원용 "내 쿠폰함" + "코드로 쿠폰 받기" 화면 — 2026-09-07 신규 (송채현).
 * 웰컴/매장취소보상 쿠폰은 시스템이 자동으로 넣어주고, 프로모션 쿠폰만 여기서 코드 입력으로 받는다.
 */
@Controller
@RequestMapping("/coupons")
@RequiredArgsConstructor
public class CouponController {

	private final CouponService couponService;
	private final SuperAdminCouponService superAdminCouponService;

	@LoginRequired
	@GetMapping
	public String myCoupons(HttpSession session, Model model) {
		Long userId = (Long) session.getAttribute("userId");
		List<CouponRowDto> coupons = couponService.listForUser(userId);
		model.addAttribute("coupons", coupons);
		return "couponView/coupons";
	}

	@LoginRequired
	@GetMapping("/claim")
	public String claimForm(HttpSession session) {
		return "couponView/claim";
	}

	@LoginRequired
	@PostMapping("/claim")
	public String claim(@RequestParam String code, HttpSession session) {
		Long userId = (Long) session.getAttribute("userId");
		try {
			CouponCampaignEntity campaign = superAdminCouponService.findByCode(code);
			couponService.claimCampaignCoupon(userId, campaign);
		} catch (EntityNotFoundException e) {
			return "redirect:/coupons/claim?error=notfound";
		} catch (ResponseStatusException e) {
			return "redirect:/coupons/claim?error=cannot";
		}
		return "redirect:/coupons";
	}

	/**
	 * 매장 지정 쿠폰 "쿠폰 받기" 버튼 — 2026-10-01 신규. 코드 입력 없이 버튼 클릭으로 받는다
	 * (CouponService#claimStoreCampaignCoupon). 받은 뒤엔 원래 보던 매장 상세로 돌아간다.
	 */
	@LoginRequired
	@PostMapping("/store-campaigns/{campaignId}/claim")
	public String claimStoreCampaign(@PathVariable Long campaignId,
	                                  @RequestParam Long storeId,
	                                  HttpSession session,
	                                  RedirectAttributes redirectAttributes) {
		Long userId = (Long) session.getAttribute("userId");
		try {
			couponService.claimStoreCampaignCoupon(userId, campaignId, storeId);
			redirectAttributes.addFlashAttribute("couponClaimed", "쿠폰을 받았어요! 내 쿠폰함에서 확인할 수 있어요.");
		} catch (ResponseStatusException e) {
			redirectAttributes.addFlashAttribute("couponClaimError", e.getReason());
		}
		return "redirect:/user/stores/" + storeId;
	}
}

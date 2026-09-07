package net.dsa.girigiri.controller;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.CouponRowDto;
import net.dsa.girigiri.domain.entity.CouponCampaignEntity;
import net.dsa.girigiri.service.CouponService;
import net.dsa.girigiri.service.SuperAdminCouponService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

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

	@GetMapping
	public String myCoupons(HttpSession session, Model model) {
		Long userId = (Long) session.getAttribute("userId");
		if (userId == null) {
			return "redirect:/auth/loginForm";
		}
		List<CouponRowDto> coupons = couponService.listForUser(userId);
		model.addAttribute("coupons", coupons);
		return "couponView/coupons";
	}

	@GetMapping("/claim")
	public String claimForm(HttpSession session) {
		Long userId = (Long) session.getAttribute("userId");
		if (userId == null) {
			return "redirect:/auth/loginForm";
		}
		return "couponView/claim";
	}

	@PostMapping("/claim")
	public String claim(@RequestParam String code, HttpSession session) {
		Long userId = (Long) session.getAttribute("userId");
		if (userId == null) {
			return "redirect:/auth/loginForm";
		}
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
}

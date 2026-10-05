package net.dsa.girigiri.controller;

import jakarta.servlet.http.HttpSession;
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
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

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
		model.addAttribute("sidoList", net.dsa.girigiri.util.SidoParser.SIDO_LIST);
		List<CouponCampaignRowDto> campaigns = couponService.listAllSortedByNewest();
		CouponPolicyEntity policy = memberCouponService.getOrCreatePolicy();
		model.addAttribute("campaigns", campaigns);
		// 상단 요약 카드·탭 개수(2026-10-06 화면 개편) — 목록에서 바로 계산한다.
		long activeCount = campaigns.stream().filter(c -> "진행중".equals(c.getStatusLabel())).count();
		model.addAttribute("activeCount", activeCount);
		model.addAttribute("endedCount", campaigns.size() - activeCount);
		model.addAttribute("totalIssued", campaigns.stream().mapToLong(CouponCampaignRowDto::getClaimedCount).sum());
		model.addAttribute("expiringSoonCount", campaigns.stream()
				.filter(c -> "진행중".equals(c.getStatusLabel()) && c.getDaysLeft() != null && c.getDaysLeft() <= 3).count());
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

	/**
	 * 지역 지정 쿠폰 발행(2026-10-06) — 선택한 시도의 회원 쿠폰함으로 바로 발급한다.
	 * 서버에서 한 번 더 검증(SuperAdminCouponService#createRegionCampaign)하고, 통과 못 하면 목록으로 돌아간다.
	 */
	@PostMapping("/region")
	public String issueRegionCoupon(@RequestParam List<String> sidos,
	                                 @RequestParam String discountType,
	                                 @RequestParam(required = false) Integer discountRate,
	                                 @RequestParam(required = false) Integer discountAmount,
	                                 @RequestParam(required = false) Integer maxDiscountAmount,
	                                 @RequestParam(required = false) Integer minOrderAmount,
	                                 @RequestParam(defaultValue = "7") int validDays,
	                                 @RequestParam(required = false) String reason,
	                                 @RequestParam(required = false) boolean notify,
	                                 HttpSession session,
	                                 RedirectAttributes redirectAttributes) {
		Long adminId = (Long) session.getAttribute("userId");
		SuperAdminCouponService.RegionCampaignResult result = couponService.createRegionCampaign(
				sidos, discountType, discountRate, discountAmount, maxDiscountAmount, minOrderAmount,
				validDays, reason, adminId, notify);
		if (!result.success()) {
			redirectAttributes.addFlashAttribute("regionCouponError",
					"입력값을 다시 확인해 주세요 (선택한 지역에 쿠폰을 받을 회원이 없으면 발행할 수 없어요).");
		} else {
			redirectAttributes.addFlashAttribute("regionCouponSuccess",
					String.join("·", sidos) + " 회원 " + result.issuedCount() + "명에게 쿠폰을 발행했어요.");
		}
		return "redirect:/superadmin/coupons";
	}

	/** 지역 쿠폰 발행 모달 미리보기용 — 선택한 시도의 발급 대상 회원 수(순수 조회). */
	@GetMapping("/region/recipient-count")
	@ResponseBody
	public Map<String, Integer> regionRecipientCount(@RequestParam(required = false) List<String> sidos) {
		return Map.of("count", couponService.countRegionRecipients(sidos));
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

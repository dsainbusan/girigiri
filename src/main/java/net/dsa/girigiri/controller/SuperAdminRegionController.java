package net.dsa.girigiri.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.RegionStoreRowDto;
import net.dsa.girigiri.service.SuperAdminCouponService;
import net.dsa.girigiri.service.SuperAdminRegionService;
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
 * 슈퍼어드민 "지역 → 매장" 드릴다운 — 2026-10-01 신규 (통계 대시보드 "지역별 현황" 표 행 클릭 진입).
 * "문제 발견(대시보드) → 원인 매장 확인(이 화면) → 조치(쿠폰/제안)"를 한 흐름으로 묶는다.
 * SuperAdminStoreController가 이미 300줄에 근접해 있어 도메인을 분리했다(레이어 규칙 "컨트롤러 15개/300줄" 기준).
 */
@Controller
@RequestMapping("/superadmin/regions")
@RequiredArgsConstructor
public class SuperAdminRegionController {

	private final SuperAdminRegionService regionService;
	private final SuperAdminCouponService couponService;

	@GetMapping("/{sido}/stores")
	public String stores(@PathVariable String sido,
	                      @RequestParam(defaultValue = "7") int days,
	                      Model model) {
		int normalizedDays = (days == 30) ? 30 : 7;
		List<RegionStoreRowDto> rows = regionService.getRegionStores(sido, normalizedDays);

		long storeCount = rows.size();
		int totalRegistered = rows.stream().mapToInt(RegionStoreRowDto::registeredCount).sum();
		int totalSold = rows.stream().mapToInt(RegionStoreRowDto::soldCount).sum();
		Integer overallPercent = totalRegistered == 0 ? null : (int) Math.round(100.0 * totalSold / totalRegistered);

		model.addAttribute("sido", sido);
		model.addAttribute("days", normalizedDays);
		model.addAttribute("rows", rows);
		model.addAttribute("storeCount", storeCount);
		model.addAttribute("overallPercent", overallPercent);
		return "superAdminView/regionStores";
	}

	/**
	 * 매장 지정 쿠폰 발행. 체크한 매장 + 모달 입력값을 그대로 받는다 — 서버에서 한 번 더 검증
	 * (SuperAdminCouponService#createStoreCampaign)하고, 통과 못 하면 폼으로 돌아간다.
	 */
	@PostMapping("/{sido}/stores/coupons")
	public String issueCoupon(@PathVariable String sido,
	                           @RequestParam(defaultValue = "7") int days,
	                           @RequestParam List<Long> storeIds,
	                           @RequestParam String discountType,
	                           @RequestParam(required = false) Integer discountRate,
	                           @RequestParam(required = false) Integer discountAmount,
	                           @RequestParam(required = false) Integer maxDiscountAmount,
	                           @RequestParam(required = false) Integer minOrderAmount,
	                           @RequestParam(defaultValue = "7") int validDays,
	                           @RequestParam(required = false) String reason,
	                           @RequestParam(required = false) boolean notifyLiked,
	                           @RequestParam(required = false) boolean notifySameRegion,
	                           HttpSession session,
	                           RedirectAttributes redirectAttributes) {
		Long adminId = (Long) session.getAttribute("userId");
		SuperAdminCouponService.StoreCampaignResult result = couponService.createStoreCampaign(
				storeIds, discountType, discountRate, discountAmount, maxDiscountAmount, minOrderAmount,
				validDays, reason, adminId, notifyLiked, notifySameRegion);

		if (result == SuperAdminCouponService.StoreCampaignResult.INVALID) {
			redirectAttributes.addFlashAttribute("couponError",
					"입력값을 다시 확인해 주세요 (선택한 매장을 찜한 손님이 없으면 발행할 수 없어요).");
		} else {
			redirectAttributes.addFlashAttribute("couponSuccess",
					storeIds.size() + "개 매장 대상 쿠폰을 발행했어요.");
		}
		return "redirect:/superadmin/regions/" + sido + "/stores?days=" + days;
	}

	/**
	 * 쿠폰 발행 모달 미리보기용 — 2026-10-01 신규. 선택한 매장을 찜한 손님 수(= 발행 수량 상한이
	 * 될 값)를 그 자리에서 보여준다. 실제 발행 시에는 이 값을 다시 서버가 계산하므로(신뢰 X),
	 * 여기서는 순수 조회만 한다.
	 */
	@GetMapping("/{sido}/stores/liked-count")
	@ResponseBody
	public Map<String, Integer> likedCount(@PathVariable String sido, @RequestParam List<Long> storeIds) {
		return Map.of("count", couponService.countDistinctLikedCustomers(storeIds));
	}

	/** 점주에게 운영 제안 발송 — 할인율 상향 제안은 하지 않는다(SuperAdminRegionService 참고). */
	@PostMapping("/{sido}/stores/suggestions")
	public String sendSuggestions(@PathVariable String sido,
	                               @RequestParam(defaultValue = "7") int days,
	                               @RequestParam List<Long> storeIds,
	                               @RequestParam String template,
	                               @RequestParam(required = false) String customMessage,
	                               RedirectAttributes redirectAttributes) {
		int sent = regionService.sendOwnerSuggestions(storeIds, template, customMessage);
		redirectAttributes.addFlashAttribute("suggestionSuccess", sent + "개 매장에 제안을 보냈어요.");
		return "redirect:/superadmin/regions/" + sido + "/stores?days=" + days;
	}
}

package net.dsa.girigiri.controller;

import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.StoreRecentStatsDto;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.service.LookupService;
import net.dsa.girigiri.service.SuperAdminStoreService;
import net.dsa.girigiri.util.StoreHoursUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 슈퍼어드민(플랫폼 운영자) "매장 관리" 화면 라우팅.
 * 2026-09-03, SuperAdminController에서 도메인 분리 — 레이어 규칙 2단계.
 */
@Controller
@RequestMapping("/superadmin")
@RequiredArgsConstructor
public class SuperAdminStoreController {

	private final SuperAdminStoreService storeService;
	private final LookupService lookupService;

	@Value("${kakao.map.js-key}")
	private String kakaoMapJsKey;

	@GetMapping("/stores")
	public String stores(@RequestParam(required = false) String filter, Model model) {
		String normalizedFilter = "PENDING".equals(filter) ? "PENDING" : null;
		List<StoreEntity> stores = storeService.findStores(normalizedFilter);

		// 대기 매장은 "대기" 배지로 고정 표시하니 영업시간 기준 영업중/휴업 판정은 대기가 아닌 매장만 계산.
		Map<Long, Boolean> openStatusMap = new HashMap<>();
		for (StoreEntity store : stores) {
			if (StoreEntity.STATUS_PENDING.equals(store.getApprovalStatus())) {
				continue;
			}
			StoreHoursUtil.ClosingInfo closingInfo = StoreHoursUtil.parse(store.getOperatingHours(), 60);
			boolean isOpen = closingInfo.closeAt() == null || closingInfo.closeAt().isAfter(LocalDateTime.now());
			openStatusMap.put(store.getId(), isOpen);
		}

		model.addAttribute("stores", stores);
		model.addAttribute("openStatusMap", openStatusMap);
		model.addAttribute("filter", normalizedFilter);
		return "superAdminView/stores";
	}

	@PostMapping("/stores/{id}/approve")
	public String approveStore(@PathVariable Long id) {
		storeService.approve(id);
		return "redirect:/superadmin/stores";
	}

	@PostMapping("/stores/{id}/delete")
	public String deleteStore(@PathVariable Long id) {
		lookupService.getStore(id);

		if (!storeService.canDelete(id)) {
			return "redirect:/superadmin/stores?deleteError";
		}

		storeService.delete(id);

		return "redirect:/superadmin/stores";
	}

	/**
	 * 매장 목록에서 클릭해 들어오는 상세 화면. memberDetail.html의 "연결된 매장"과 대칭으로, 여기도
	 * 이 매장을 소유한 회원 계정("연결된 계정")을 보여주고 클릭하면 회원 상세로 가게 한다. owner_id가
	 * 없거나(이론상) 탈퇴 등으로 회원을 못 찾으면 그냥 섹션을 숨긴다.
	 * "← 매장 목록"이 원래 보던 필터 탭(대기 등)으로 정확히 돌아가게, 목록에서 실려온 filter를
	 * 그대로 모델에 얹는다.
	 */
	@GetMapping("/stores/{id}")
	public String storeDetail(@PathVariable Long id,
	                           @RequestParam(required = false) String filter,
	                           Model model) {
		StoreEntity store = lookupService.getStore(id);

		model.addAttribute("filter", filter);
		if (store.getOwnerId() != null) {
			storeService.findOwner(store.getOwnerId()).ifPresent(owner -> model.addAttribute("owner", owner));
		}

		StoreHoursUtil.ClosingInfo closingInfo = StoreHoursUtil.parse(store.getOperatingHours(), 60);
		boolean isOpen = closingInfo.closeAt() == null || closingInfo.closeAt().isAfter(LocalDateTime.now());

		StoreRecentStatsDto stats = storeService.getRecentStats(id);
		int rescueGoalPercent = store.getRescueGoalPercent() != null ? store.getRescueGoalPercent() : 70;

		model.addAttribute("store", store);
		model.addAttribute("isOpen", isOpen);
		model.addAttribute("rescueRate7d", stats.rescueRate7d());
		model.addAttribute("rescueGoalPercent", rescueGoalPercent);
		model.addAttribute("registeredCount7d", stats.registeredCount7d());
		model.addAttribute("soldCount7d", stats.soldCount7d());
		model.addAttribute("totalQuantity7d", stats.totalQuantity7d());
		return "superAdminView/storeDetail";
	}

	/**
	 * 점주 본인용 /store/edit는 상호명/사업자번호/주소를 승인 심사 근거라는 이유로 일부러 막아뒀지만,
	 * 운영자는 그 제한을 받을 이유가 없어서 전체 필드를 여는 별도 화면을 둔다.
	 */
	@GetMapping("/stores/{id}/edit")
	public String storeEditForm(@PathVariable Long id, Model model) {
		model.addAttribute("store", lookupService.getStore(id));
		model.addAttribute("kakaoMapJsKey", kakaoMapJsKey);
		return "superAdminView/storeEdit";
	}

	@PostMapping("/stores/{id}/edit")
	public String storeEditSubmit(@PathVariable Long id,
	                               @RequestParam String storeName,
	                               @RequestParam String category,
	                               @RequestParam String phone,
	                               @RequestParam String address,
	                               @RequestParam(required = false) String businessNumber,
	                               @RequestParam(required = false) String operatingHours,
	                               @RequestParam(required = false) Double latitude,
	                               @RequestParam(required = false) Double longitude) {
		lookupService.getStore(id);

		if (!storeService.isEditValid(storeName, category, phone, address)) {
			return "redirect:/superadmin/stores/" + id + "/edit?error";
		}

		storeService.updateStoreInfo(id, storeName, category, phone, address, businessNumber, operatingHours, latitude, longitude);

		return "redirect:/superadmin/stores/" + id;
	}
}

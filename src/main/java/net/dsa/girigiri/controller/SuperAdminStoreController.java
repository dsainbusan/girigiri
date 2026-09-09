package net.dsa.girigiri.controller;

import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.ReservationOrderItemDto;
import net.dsa.girigiri.domain.dto.StockItemDto;
import net.dsa.girigiri.domain.dto.StoreRecentStatsDto;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.service.LookupService;
import net.dsa.girigiri.service.ProductService;
import net.dsa.girigiri.service.ReservationService;
import net.dsa.girigiri.service.SuperAdminStoreService;
import net.dsa.girigiri.util.StoreHoursUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
	private final ProductService productService;
	private final ReservationService reservationService;

	@Value("${kakao.map.js-key}")
	private String kakaoMapJsKey;

	@GetMapping("/stores")
	public String stores(@RequestParam(required = false) String q,
	                      @RequestParam(required = false) String filter, Model model) {
		String normalizedFilter = "PENDING".equals(filter) ? "PENDING" : null;
		List<StoreEntity> stores = storeService.findStores(q, normalizedFilter);

		// 대기 매장은 "대기" 배지로 고정 표시하니 영업시간 기준 영업중/휴업 판정은 대기가 아닌 매장만 계산.
		Map<Long, Boolean> openStatusMap = new HashMap<>();
		for (StoreEntity store : stores) {
			if (StoreEntity.STATUS_PENDING.equals(store.getApprovalStatus())) {
				continue;
			}
			StoreHoursUtil.ClosingInfo closingInfo = StoreHoursUtil.parse(store.getOperatingHours(), 60);
			openStatusMap.put(store.getId(), StoreHoursUtil.isOpen(closingInfo.closeAt()));
		}

		model.addAttribute("stores", stores);
		model.addAttribute("openStatusMap", openStatusMap);
		model.addAttribute("filter", normalizedFilter);
		model.addAttribute("q", q);
		return "superAdminView/stores";
	}

	/**
	 * 검색/필터 조건 그대로 CSV로 내려받는다 — SuperAdminMemberController#exportMembers와 동일 패턴
	 * (BOM 붙여서 엑셀에서 한글 안 깨지게, csvField로 큰따옴표 이스케이프).
	 * 목록 표엔 "영업중/휴업"(영업시간 기준 실시간 계산값)이 배지로 보이지만, CSV엔 원본 승인상태
	 * (PENDING/APPROVED/REJECTED)만 담는다 — members.csv도 계산값이 아니라 저장된 status 그대로 담는
	 * 것과 같은 이유(내려받은 시점 이후엔 영업중/휴업 판정이 계속 바뀌는 값이라 스냅샷으로 안 맞음).
	 */
	@GetMapping("/stores/export")
	public ResponseEntity<byte[]> exportStores(@RequestParam(required = false) String q,
	                                            @RequestParam(required = false) String filter) {
		List<StoreEntity> stores = storeService.findStores(q, "PENDING".equals(filter) ? "PENDING" : null);

		DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd");
		StringBuilder csv = new StringBuilder("﻿");
		csv.append("매장 번호,매장명,카테고리,주소,연락처,승인상태,가입일\n");
		for (StoreEntity s : stores) {
			csv.append(s.getId()).append(',')
					.append(csvField(s.getStoreName())).append(',')
					.append(csvField(s.getCategory())).append(',')
					.append(csvField(s.getAddress())).append(',')
					.append(csvField(s.getPhone())).append(',')
					.append(csvField(s.getApprovalStatus())).append(',')
					.append(s.getCreatedAt() != null ? s.getCreatedAt().format(dateFormat) : "")
					.append('\n');
		}

		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=stores.csv")
				.contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
				.body(csv.toString().getBytes(StandardCharsets.UTF_8));
	}

	private String csvField(String value) {
		String safe = value == null ? "" : value.replace("\"", "\"\"");
		return "\"" + safe + "\"";
	}

	/**
	 * 표 왼쪽 체크박스로 여러 매장을 골라 한 번에 정지/정지 해제 — SuperAdminMemberController의
	 * bulk-suspend/bulk-unsuspend와 동일 패턴. q/filter를 리다이렉트에 실어 보내서 검색·필터 걸어둔
	 * 채로 처리해도 그 화면으로 돌아간다.
	 */
	@PostMapping("/stores/bulk-suspend")
	public String bulkSuspendStores(@RequestParam(required = false) List<Long> ids,
	                                 @RequestParam(required = false) String q,
	                                 @RequestParam(required = false) String filter) {
		storeService.bulkSuspend(ids);
		return "redirect:" + buildStoresRedirectUri(q, filter);
	}

	@PostMapping("/stores/bulk-unsuspend")
	public String bulkUnsuspendStores(@RequestParam(required = false) List<Long> ids,
	                                   @RequestParam(required = false) String q,
	                                   @RequestParam(required = false) String filter) {
		storeService.bulkUnsuspend(ids);
		return "redirect:" + buildStoresRedirectUri(q, filter);
	}

	private String buildStoresRedirectUri(String q, String filter) {
		return UriComponentsBuilder.fromPath("/superadmin/stores")
				.queryParamIfPresent("q", Optional.ofNullable(q).filter(s -> !s.isBlank()))
				.queryParamIfPresent("filter", Optional.ofNullable(filter))
				.build()
				.toUriString();
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
		// 변경됨 (2026-09-08, 코드 감사) — StoreHoursUtil.isOpen으로 위임(3곳 중복 중 하나).
		boolean isOpen = StoreHoursUtil.isOpen(closingInfo.closeAt());

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
	 * 추가됨 (2026-09-09) — 매장 상세 화면에서 "재고 현황" 링크로 들어오는, 그 매장의 상품(재고) 목록을
	 * 조회 전용으로 보여주는 화면. 점주용 /store/products와 달리 CRUD·상태 전환 버튼은 전혀 없다 —
	 * 운영자가 "이 매장이 지금 뭘 얼마에 몇 개 파는지" 확인만 하는 용도. 카드 매핑은 ProductService의
	 * toStockItem/listStockForStore를 그대로 재사용해서 점주 화면과 완전히 같은 기준(상태 라벨,
	 * 할인율, 출처)으로 보여준다.
	 */
	@GetMapping("/stores/{id}/inventory")
	public String storeInventory(@PathVariable Long id, Model model) {
		StoreEntity store = lookupService.getStore(id);
		List<StockItemDto> items = productService.listStockForStore(id, store.getCategory());

		model.addAttribute("store", store);
		model.addAttribute("items", items);
		model.addAttribute("totalCount", items.size());
		model.addAttribute("sellingCount", items.stream().filter(i -> "selling".equals(i.statusVariant())).count());
		model.addAttribute("soldOutCount", items.stream().filter(i -> "soldout".equals(i.statusVariant())).count());
		return "superAdminView/storeInventory";
	}

	/**
	 * 추가됨 (2026-09-09) — 매장 상세 화면에서 "주문 내역" 링크로 들어오는, 그 매장과 유저 사이의
	 * 예약(주문) 목록을 조회 전용으로 보여주는 화면. 점주/손님용 화면들과 달리 상태별로 나누지 않고
	 * 대기·확정·픽업가능·픽업완료·취소·노쇼를 전부 한 목록에서, 구매자가 누군지까지 보여준다
	 * (ReservationService#getOrdersForStore 참고).
	 */
	@GetMapping("/stores/{id}/orders")
	public String storeOrders(@PathVariable Long id, Model model) {
		StoreEntity store = lookupService.getStore(id);
		List<ReservationOrderItemDto> orders = reservationService.getOrdersForStore(id);

		model.addAttribute("store", store);
		model.addAttribute("orders", orders);
		model.addAttribute("totalCount", orders.size());
		return "superAdminView/storeOrders";
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

		if (!storeService.isEditValid(storeName, category, phone, address, operatingHours)) {
			return "redirect:/superadmin/stores/" + id + "/edit?error";
		}

		storeService.updateStoreInfo(id, storeName, category, phone, address, businessNumber, operatingHours, latitude, longitude);

		return "redirect:/superadmin/stores/" + id;
	}
}

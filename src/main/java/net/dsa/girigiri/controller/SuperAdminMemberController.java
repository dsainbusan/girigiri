package net.dsa.girigiri.controller;

import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.entity.UserEntity;
import net.dsa.girigiri.service.LookupService;
import net.dsa.girigiri.service.StoreAccessService;
import net.dsa.girigiri.service.SuperAdminMemberService;
import net.dsa.girigiri.util.PaginationUtil;
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
import java.util.List;
import java.util.Optional;

/**
 * 슈퍼어드민(플랫폼 운영자) "회원 관리" 화면 라우팅.
 * 2026-09-03, SuperAdminController에서 도메인 분리 — 레이어 규칙 2단계.
 */
@Controller
@RequestMapping("/superadmin")
@RequiredArgsConstructor
public class SuperAdminMemberController {

	// 글이 쌓일수록 목록이 길어지는 화면의 페이지당 행 수.
	private static final int PAGE_SIZE = 10;

	private final SuperAdminMemberService memberService;
	private final LookupService lookupService;
	private final StoreAccessService storeAccessService;

	@GetMapping("/members")
	public String members(@RequestParam(required = false) String q,
	                       @RequestParam(required = false) String filter,
	                       @RequestParam(defaultValue = "0") int page,
	                       Model model) {
		String normalizedFilter = memberService.normalizeFilter(filter);
		List<UserEntity> users = memberService.findFilteredMembers(q, normalizedFilter);

		model.addAttribute("users", users);
		model.addAttribute("pagedUsers", PaginationUtil.paginate(users, page, PAGE_SIZE));
		model.addAttribute("totalPages", PaginationUtil.totalPages(users.size(), PAGE_SIZE));
		model.addAttribute("page", page);
		model.addAttribute("q", q == null ? "" : q.trim());
		model.addAttribute("filter", normalizedFilter);

		return "superAdminView/members";
	}

	/**
	 * "회원 목록 다운로드" 버튼. 현재 검색(q)/필터(filter) 조건을 그대로 반영해서 CSV로 내려준다 —
	 * 순수 텍스트 조립이라 Apache POI(엑셀 리포트용)까지는 필요 없다.
	 * UTF-8 BOM을 붙이는 이유: BOM 없이 내려주면 엑셀에서 한글이 깨져 보인다.
	 */
	@GetMapping("/members/export")
	public ResponseEntity<byte[]> exportMembers(@RequestParam(required = false) String q,
	                                             @RequestParam(required = false) String filter) {
		List<UserEntity> users = memberService.findFilteredMembers(q, memberService.normalizeFilter(filter));

		DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd");
		StringBuilder csv = new StringBuilder("﻿");
		csv.append("닉네임,이메일,권한,상태,가입일,최근수정일\n");
		for (UserEntity u : users) {
			csv.append(csvField(u.getNickname())).append(',')
					.append(csvField(u.getEmail())).append(',')
					.append(csvField(u.getRole())).append(',')
					.append(csvField(u.getStatus())).append(',')
					.append(u.getCreatedAt() != null ? u.getCreatedAt().format(dateFormat) : "").append(',')
					.append(u.getUpdatedAt() != null ? u.getUpdatedAt().format(dateFormat) : "")
					.append('\n');
		}

		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=members.csv")
				.contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
				.body(csv.toString().getBytes(StandardCharsets.UTF_8));
	}

	private String csvField(String value) {
		String safe = value == null ? "" : value.replace("\"", "\"\"");
		return "\"" + safe + "\"";
	}

	/**
	 * 표 왼쪽 체크박스로 여러 명을 골라 한 번에 정지/정지 해제하는 일괄 액션.
	 * q/filter를 리다이렉트에 실어 보내는 이유: 검색·필터 걸어둔 채로 일괄 처리해도 그 화면으로 돌아가게
	 * 하려고 — UriComponentsBuilder로 인코딩해야 q에 한글이 있어도 깨지지 않는다.
	 */
	@PostMapping("/members/bulk-suspend")
	public String bulkSuspendMembers(@RequestParam(required = false) List<Long> ids,
	                                  @RequestParam(required = false) String q,
	                                  @RequestParam(required = false) String filter,
	                                  @RequestParam(required = false) Integer page) {
		memberService.bulkSuspend(ids);
		return "redirect:" + buildMembersRedirectUri(q, filter, page, false);
	}

	@PostMapping("/members/bulk-unsuspend")
	public String bulkUnsuspendMembers(@RequestParam(required = false) List<Long> ids,
	                                    @RequestParam(required = false) String q,
	                                    @RequestParam(required = false) String filter,
	                                    @RequestParam(required = false) Integer page) {
		memberService.bulkUnsuspend(ids);
		return "redirect:" + buildMembersRedirectUri(q, filter, page, false);
	}

	/**
	 * 개별 행 액션(정지/정지 해제/탈퇴)도 일괄 액션과 똑같이 q/filter/page를 그대로 들고 돌아간다.
	 */
	private String buildMembersRedirectUri(String q, String filter, Integer page, boolean withdrawError) {
		UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/superadmin/members")
				.queryParamIfPresent("q", Optional.ofNullable(q).filter(s -> !s.isBlank()))
				.queryParamIfPresent("filter", Optional.ofNullable(filter))
				.queryParamIfPresent("page", Optional.ofNullable(page).filter(p -> p > 0));
		if (withdrawError) {
			builder.queryParam("withdrawError");
		}
		return builder.build().encode().toUriString();
	}

	/**
	 * 회원 목록에서 이름을 클릭하면 상세 정보를 볼 수 있게 해달라는 요청. OWNER는 본인 소유 매장이
	 * 있으면 같이 보여줘서 슈퍼어드민이 바로 매장 상세로 넘어갈 수 있게 한다.
	 * "← 회원 목록"이 원래 보던 필터 탭으로 정확히 돌아가게, 목록에서 실려온 q/filter/page를
	 * 그대로 모델에 얹어서 돌려준다(history.back() 없이 URL로 왕복).
	 */
	@GetMapping("/members/{id}")
	public String memberDetail(@PathVariable Long id,
	                            @RequestParam(required = false) String q,
	                            @RequestParam(required = false) String filter,
	                            @RequestParam(required = false) Integer page,
	                            Model model) {
		UserEntity user = lookupService.getUser(id);

		model.addAttribute("member", user);
		model.addAttribute("q", q);
		model.addAttribute("filter", filter);
		model.addAttribute("page", page);
		if (UserEntity.ROLE_OWNER.equals(user.getRole())) {
			storeAccessService.findMyStore(user.getId()).ifPresent(store -> model.addAttribute("ownedStore", store));
		}

		model.addAttribute("memberActivity", memberService.getMemberActivity(id));

		return "superAdminView/memberDetail";
	}

	/**
	 * 목록의 "관리" 칸에 수정 기능도 넣어달라는 요청 — 닉네임/이메일/권한/활동 지역만 고친다.
	 */
	@GetMapping("/members/{id}/edit")
	public String memberEditForm(@PathVariable Long id, Model model) {
		model.addAttribute("member", lookupService.getUser(id));
		return "superAdminView/memberEdit";
	}

	@PostMapping("/members/{id}/edit")
	public String memberEditSubmit(@PathVariable Long id,
	                                @RequestParam String nickname,
	                                @RequestParam(required = false) String email,
	                                @RequestParam String role,
	                                @RequestParam(required = false) String region) {
		if (!memberService.updateMember(id, nickname, email, role, region)) {
			return "redirect:/superadmin/members/" + id + "/edit?error";
		}
		return "redirect:/superadmin/members/" + id;
	}

	@PostMapping("/members/{id}/suspend")
	public String suspendMember(@PathVariable Long id,
	                             @RequestParam(required = false) String q,
	                             @RequestParam(required = false) String filter,
	                             @RequestParam(required = false) Integer page) {
		memberService.suspend(id);
		return "redirect:" + buildMembersRedirectUri(q, filter, page, false);
	}

	@PostMapping("/members/{id}/unsuspend")
	public String unsuspendMember(@PathVariable Long id,
	                               @RequestParam(required = false) String q,
	                               @RequestParam(required = false) String filter,
	                               @RequestParam(required = false) Integer page) {
		memberService.unsuspend(id);
		return "redirect:" + buildMembersRedirectUri(q, filter, page, false);
	}

	/**
	 * MypageController#withdraw(자진 탈퇴)와 동일한 규칙으로 운영자가 강제 탈퇴시킨다 — 미완료 예약이
	 * 있으면 막고, 없으면 계정을 실제로 삭제한다(soft-delete 아님, 자진 탈퇴와 동일).
	 */
	@PostMapping("/members/{id}/withdraw")
	public String withdrawMember(@PathVariable Long id,
	                              @RequestParam(required = false) String q,
	                              @RequestParam(required = false) String filter,
	                              @RequestParam(required = false) Integer page) {
		lookupService.getUser(id);

		if (!memberService.canWithdraw(id)) {
			return "redirect:" + buildMembersRedirectUri(q, filter, page, true);
		}

		memberService.withdraw(id);

		return "redirect:" + buildMembersRedirectUri(q, filter, page, false);
	}
}

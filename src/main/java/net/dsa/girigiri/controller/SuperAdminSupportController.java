package net.dsa.girigiri.controller;

import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.SupportReportsDataDto;
import net.dsa.girigiri.domain.entity.InquiryEntity;
import net.dsa.girigiri.domain.entity.UserEntity;
import net.dsa.girigiri.service.InquiryService;
import net.dsa.girigiri.service.LookupService;
import net.dsa.girigiri.service.SuperAdminSupportService;
import net.dsa.girigiri.util.PaginationUtil;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 슈퍼어드민(플랫폼 운영자) "신고·문의(Support)" 화면 라우팅.
 * 2026-09-03, SuperAdminController에서 도메인 분리 — 레이어 규칙 2단계.
 */
@Controller
@RequestMapping("/superadmin")
@RequiredArgsConstructor
public class SuperAdminSupportController {

	// 글이 쌓일수록 목록이 길어지는 화면의 페이지당 행 수.
	private static final int PAGE_SIZE = 10;

	private final SuperAdminSupportService supportService;
	private final InquiryService inquiryService;
	private final LookupService lookupService;

	/**
	 * 신고접수/매장문의/유저문의를 탭으로 나눠 보여준다 — 다른 필터 탭들과 동일하게 쿼리파라미터(tab)로
	 * 서버가 골라서 그리는 방식(JS 없음). 세 섹션 데이터 전부 그대로 계산해서 모델에 담아두고,
	 * 템플릿에서 tab 값에 맞는 것만 th:if로 보여준다.
	 * 같은 page 파라미터를 세 탭이 공유하지만 항상 tab이 같이 붙어서 링크가 생성되므로(각 탭의
	 * totalPages 안에서만 링크를 만듦) 탭을 오갈 때 엉뚱한 페이지가 섞이지 않는다.
	 */
	@GetMapping("/reports")
	public String reports(@RequestParam(required = false) String tab,
	                       @RequestParam(defaultValue = "0") int page, Model model) {
		String normalizedTab = "store".equals(tab) || "user".equals(tab) ? tab : "report";
		model.addAttribute("tab", normalizedTab);
		model.addAttribute("page", page);

		SupportReportsDataDto data = supportService.getReportsData();

		model.addAttribute("complaints", PaginationUtil.paginate(data.allComplaints(), page, PAGE_SIZE));
		model.addAttribute("complaintsTotalPages", PaginationUtil.totalPages(data.allComplaints().size(), PAGE_SIZE));

		model.addAttribute("storeInquiries", PaginationUtil.paginate(data.storeInquiries(), page, PAGE_SIZE));
		model.addAttribute("storeInquiriesTotalPages", PaginationUtil.totalPages(data.storeInquiries().size(), PAGE_SIZE));
		model.addAttribute("userInquiries", PaginationUtil.paginate(data.userInquiries(), page, PAGE_SIZE));
		model.addAttribute("userInquiriesTotalPages", PaginationUtil.totalPages(data.userInquiries().size(), PAGE_SIZE));

		model.addAttribute("commentCounts", data.commentCounts());
		model.addAttribute("storeNames", data.storeNames());
		model.addAttribute("answeredAtByInquiryId", data.answeredAtByInquiryId());
		model.addAttribute("inquiryAuthorNames", data.inquiryAuthorNames());
		return "superAdminView/reports";
	}

	@PostMapping("/inquiries/{id}/reply")
	public String replyToInquiry(@PathVariable Long id, @RequestParam String content) {
		supportService.replyToInquiry(id, content);
		return "redirect:/superadmin/inquiries/" + id;
	}

	/**
	 * 문의 목록에서 글을 클릭하면 상세 페이지(작성자/사진/댓글)로 이동한다. 컨슈머용
	 * inquiryView/detail.html과 같은 InquiryService 메서드를 그대로 재사용한다 — 댓글의 canDelete
	 * 계산에 필요한 (userId, role)은 replyToInquiry와 동일한 스톱갭(role=ADMIN 첫 계정)을 쓴다.
	 */
	@GetMapping("/inquiries/{id}")
	public String inquiryDetail(@PathVariable Long id, Model model) {
		InquiryEntity inquiry = inquiryService.getInquiry(id);
		Long adminId = supportService.findAdminIdOrNull();

		model.addAttribute("inquiry", inquiry);
		model.addAttribute("authorName", inquiryService.getAuthorName(inquiry.getUserId()));
		model.addAttribute("authorExists", supportService.userExists(inquiry.getUserId()));
		model.addAttribute("storeName", inquiryService.getStoreName(inquiry.getStoreId()));
		model.addAttribute("comments", inquiryService.getComments(id, adminId, UserEntity.ROLE_ADMIN));
		return "superAdminView/inquiryDetail";
	}

	@GetMapping("/complaints/{id}")
	public String complaintDetail(@PathVariable Long id, Model model) {
		model.addAttribute("complaint", lookupService.getComplaint(id));
		return "superAdminView/complaintDetail";
	}

	@PostMapping("/complaints/{id}/reply")
	public String replyToComplaint(@PathVariable Long id, @RequestParam String content) {
		supportService.replyToComplaint(id, content);
		return "redirect:/superadmin/complaints/" + id;
	}
}

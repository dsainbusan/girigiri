package net.dsa.girigiri.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.entity.BankAccountChangeRequestEntity;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.service.BankAccountChangeService;
import net.dsa.girigiri.service.StoreAccessService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * 추가됨 (2026-10-07, 계좌 보안) — 점주용 "계좌 등록·변경 신청" 화면. /store/edit에서 더 이상 계좌를
 * 직접 수정할 수 없게 된 대신, 여기서 신청만 하고 슈퍼어드민 승인을 거쳐 반영된다
 * (BankAccountChangeService 참고). StoreController에서 분리한 이유는 StoreReportController와
 * 같다 — 2026-08-21 신설 StoreController가 다시 커지는 걸 막기 위해 도메인별로 쪼갠다.
 */
@Controller
@RequestMapping("/store/bank-account")
@RequiredArgsConstructor
public class StoreBankAccountController {

	private final StoreAccessService storeAccessService;
	private final BankAccountChangeService bankAccountChangeService;

	@GetMapping
	public String form(HttpSession session, Model model) {
		Long userId = (Long) session.getAttribute("userId");
		StoreEntity store = storeAccessService.getMyStore(userId);

		List<BankAccountChangeRequestEntity> history = bankAccountChangeService.getHistoryForStore(store.getId());

		model.addAttribute("store", store);
		model.addAttribute("history", history);
		model.addAttribute("hasPending", history.stream()
				.anyMatch(r -> BankAccountChangeRequestEntity.STATUS_PENDING.equals(r.getStatus())));
		return "storeView/bankAccount";
	}

	@PostMapping("/request")
	public String submit(@RequestParam String bankName,
	                      @RequestParam String bankAccount,
	                      @RequestParam String accountHolder,
	                      @RequestParam(required = false) MultipartFile passbook,
	                      HttpSession session,
	                      RedirectAttributes redirectAttributes) {
		Long userId = (Long) session.getAttribute("userId");
		StoreEntity store = storeAccessService.getMyStore(userId);

		bankAccountChangeService.submit(store, userId, bankName, bankAccount, accountHolder, passbook);

		redirectAttributes.addFlashAttribute("submitSuccess", "계좌 등록·변경 신청이 접수됐어요. 운영자 승인 후 반영돼요.");
		return "redirect:/store/bank-account";
	}
}

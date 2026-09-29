package net.dsa.girigiri.controller;

import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.service.SuperAdminSettlementService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

/**
 * 슈퍼어드민(플랫폼 운영자) "정산" 화면 라우팅 (2026-09-29 신설, 담당: 송보미).
 *
 * 코드 감사에서 "결제·취소·환불은 보이는데 정산 지급을 확인·대사할 방법이 없다"고 지적된 부분.
 * SettlementBatchService(문창호, WBS 2.0)가 "지급 처리 화면은 슈퍼어드민 영역"이라고 이미 남겨둔
 * 확정/지급 로직을 그대로 호출만 한다 — 계산 로직은 전혀 건드리지 않는다.
 */
@Controller
@RequestMapping("/superadmin")
@RequiredArgsConstructor
public class SuperAdminSettlementController {

	private final SuperAdminSettlementService settlementService;

	@GetMapping("/settlements")
	public String settlements(Model model) {
		model.addAttribute("pending", settlementService.getPendingSettlements());
		model.addAttribute("recent", settlementService.getRecentSettlements());
		return "superAdminView/settlements";
	}

	/**
	 * 은행 기업뱅킹 대량이체를 마친 뒤 선택한 건을 "지급 완료"로 표시한다. memo는 이체 확인용
	 * 메모(예: 이체 일시·담당자) — 비우면 SettlementBatchService가 기본값("은행 대량이체")을 채운다.
	 */
	@PostMapping("/settlements/pay")
	public String markPaid(@RequestParam(required = false) List<Long> ids,
	                        @RequestParam(required = false) String memo,
	                        RedirectAttributes redirectAttributes) {
		int paid = settlementService.markPaid(ids, memo);
		redirectAttributes.addFlashAttribute("paidCount", paid);
		return "redirect:/superadmin/settlements";
	}

	/**
	 * 지금 지급 대기 중인 전체 건의 은행 이체 목록 Excel. 은행 기업뱅킹 대량이체 업로드용
	 * (SettlementTransferExcelGenerator 참고 — 문창호가 이미 만들어둔 유틸을 그대로 재사용).
	 */
	@GetMapping("/settlements/export")
	public ResponseEntity<byte[]> exportTransferExcel() throws IOException {
		byte[] excel = settlementService.buildTransferExcel();
		String filename = "settlement-transfer-" + LocalDate.now() + ".xlsx";
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
				.body(excel);
	}
}

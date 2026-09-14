package net.dsa.girigiri.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.LedgerData;
import net.dsa.girigiri.security.LoginRequired;
import net.dsa.girigiri.service.LedgerService;
import net.dsa.girigiri.util.LedgerCsvGenerator;
import net.dsa.girigiri.util.LedgerPdfGenerator;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.time.LocalDate;

/**
 * 마이페이지 절약 가계부 (WBS 6.0, 문창호).
 *
 * 소비자(USER)용 화면 — 이번 달 절약 요약(히어로) + 탭 4개(내역/분석/목표/뱃지)로 구성해서(랭킹은 뱃지 탭 안 하위 토글)
 * 한 스크롤에 정보가 몰리지 않게 한다(탭 전환은 화면 쪽 JS, 데이터는 여기서 한 번에 다 내려준다).
 * 하단 탭바의 "가계부" 링크가 가리키는 경로가 이 컨트롤러다.
 */
@Controller
@RequestMapping("/user/ledger")
@RequiredArgsConstructor
@LoginRequired
public class LedgerController {

	private final LedgerService ledgerService;

	@GetMapping
	public String ledger(HttpSession session, Model model) {
		Long userId = (Long) session.getAttribute("userId");
		model.addAttribute("data", ledgerService.build(userId));
		model.addAttribute("ranking", ledgerService.buildRanking(userId));
		return "ledgerView/ledger";
	}

	/**
	 * 이번 달 절약 목표 설정. 빈 값으로 제출하면 목표를 해제한다(LedgerService.updateGoal).
	 * 최소 금액 미만이면(1만원 미만) 저장하지 않고 목표 탭에 안내만 보여준다.
	 */
	@PostMapping("/goal")
	public String updateGoal(@RequestParam(required = false) Integer goalAmount, HttpSession session) {
		Long userId = (Long) session.getAttribute("userId");
		LedgerService.GoalUpdateResult result = ledgerService.updateGoal(userId, goalAmount);
		if (result == LedgerService.GoalUpdateResult.TOO_LOW) {
			return "redirect:/user/ledger?tab=goal&error=goalTooLow";
		}
		return "redirect:/user/ledger?tab=goal";
	}

	/**
	 * 대표 뱃지 설정/해제.
	 * 비즈니스 로직(해금 여부 검증 등)은 모두 ledgerService.updateRepresentativeBadge에 위임한다.
	 */
	@PostMapping("/badge/representative")
	public String updateRepresentativeBadge(@RequestParam(required = false) String badgeCode, HttpSession session) {
		Long userId = (Long) session.getAttribute("userId");
		ledgerService.updateRepresentativeBadge(userId, badgeCode);
		return "redirect:/user/ledger?tab=badge";
	}

	/**
	 * 절약 가계부 CSV. 화면과 같은 집계(LedgerService.build)라 숫자가 항상 일치한다.
	 * CSV는 브라우저가 미리보기를 못 해서 attachment(바로 다운로드)로 준다 — PDF는 inline.
	 */
	@GetMapping("/csv")
	public ResponseEntity<byte[]> csv(HttpSession session) {
		Long userId = (Long) session.getAttribute("userId");
		LedgerData data = ledgerService.build(userId);
		byte[] csv = LedgerCsvGenerator.generate(data);
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename("csv") + "\"")
				.body(csv);
	}

	/** 절약 가계부 PDF. csv()와 데이터 소스 동일, 포맷만 PDF. 새 탭에서 바로 열리게 inline. */
	@GetMapping("/pdf")
	public ResponseEntity<byte[]> pdf(HttpSession session) throws IOException {
		Long userId = (Long) session.getAttribute("userId");
		LedgerData data = ledgerService.build(userId);
		byte[] pdf = LedgerPdfGenerator.generate(data);
		return ResponseEntity.ok()
				.contentType(MediaType.APPLICATION_PDF)
				.header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename("pdf") + "\"")
				.body(pdf);
	}

	/** 파일명에 한글(닉네임)을 넣으면 Content-Disposition 인코딩이 깨질 수 있어 ASCII로 고정. */
	private String filename(String ext) {
		return "savings-ledger-" + LocalDate.now() + "." + ext;
	}
}

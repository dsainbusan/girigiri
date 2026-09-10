package net.dsa.girigiri.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.entity.ReservationEntity;
import net.dsa.girigiri.exception.ReservationAccessDeniedException;
import net.dsa.girigiri.service.ComplaintService;
import net.dsa.girigiri.service.LookupService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 손님이 내 예약 목록에서 "신고하기"를 접수하는 화면.
 * 2026-09-10 (코드 감사 LOW 대응) — ReservationController가 686줄 → 348줄로 한 번 쪼갠 뒤에도 다시
 * 자체 300줄 기준을 넘겨서 재분리했다. "신고하기"는 2026-09-08에 나중에 붙은 별개 기능(신고 처리
 * 자체는 슈퍼어드민 담당, 여기는 접수만)이라 예약 생성 흐름(체크아웃~완료)과 묶여있을 이유가 없어서
 * 뗀다. @RequestMapping("/reservation")은 그대로라 URL은 하나도 안 바뀐다.
 */
@Controller
@RequestMapping("/reservation")
@RequiredArgsConstructor
public class ReservationReportController {

	private final LookupService lookupService;
	// 신고 처리(슈퍼어드민)와는 별개로, 신고 접수(손님)만 담당하는 서비스라 이름을 다르게 뒀다
	// (ComplaintService 상단 주석 참고).
	private final ComplaintService complaintService;

	@GetMapping("/{id}/report")
	public String reportForm(@PathVariable Long id, HttpSession session, Model model) {
		ReservationEntity reservation = lookupService.getReservation(id);
		if (!reservation.getUserId().equals(ReservationController.resolveCurrentUserId(session))) {
			throw new ReservationAccessDeniedException("본인 예약만 신고할 수 있어요.");
		}

		model.addAttribute("reservation", reservation);
		return "reservationView/report";
	}

	@PostMapping("/{id}/report")
	public String submitReport(@PathVariable Long id,
							   @RequestParam String reason,
							   @RequestParam String content,
							   HttpSession session,
							   RedirectAttributes redirectAttributes) {
		ReservationEntity reservation = lookupService.getReservation(id);
		if (!reservation.getUserId().equals(ReservationController.resolveCurrentUserId(session))) {
			throw new ReservationAccessDeniedException("본인 예약만 신고할 수 있어요.");
		}

		complaintService.submitFromReservation(reservation, reason, content);
		redirectAttributes.addFlashAttribute("reportedMessage", "신고가 접수됐어요. 운영자가 확인 후 처리할게요.");
		return "redirect:/reservation/my";
	}
}

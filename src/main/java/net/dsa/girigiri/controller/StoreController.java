package net.dsa.girigiri.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.entity.ProductEntity;
import net.dsa.girigiri.domain.entity.ReservationEntity;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.repository.ProductRepository;
import net.dsa.girigiri.repository.ReservationRepository;
import net.dsa.girigiri.repository.StoreRepository;
import net.dsa.girigiri.util.StoreHoursUtil;
import net.dsa.girigiri.util.StoreReportExcelGenerator;
import net.dsa.girigiri.util.StoreReportPdfGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 점주 대시보드 (WBS 3.0 매장 운영 — 문창호 담당: POS json 연동, 할인율 자동계산, 판매/등록 현황
 * 대시보드, 통계 그래프, 판매/폐기 리포트).
 *
 * 변경됨 (2026-08-21) — 왜: 목업 껍데기 단계를 지나 ProductRepository/ReservationRepository
 * 실데이터로 교체했다.
 * - 등록/판매중/폐기/구제율/오늘매출은 ProductEntity(registeredAt, status, quantity,
 *   remainingQuantity, discountedPrice)만으로 계산 — 새 컬럼 없이 지금 있는 필드로 충분했다.
 *   구제율 = 판매÷등록 (CLAUDE.md 리포트 스펙 그대로).
 * - 픽업 예약(대기/완료)은 ProductEntity엔 없는 개념이라 ReservationRepository로 별도 집계.
 * - "어제 대비" 매출 증감은 다음 단계 TODO — 지금 샘플 데이터가 전부 NOW() 타임스탬프라 어제
 *   데이터가 없어서, 가짜 값을 보여주느니 빈 값으로 둔다.
 */
@Controller
@RequestMapping("/store")
@RequiredArgsConstructor
public class StoreController {

	private static final DateTimeFormatter CLOSE_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

	private final StoreRepository storeRepository;
	private final ProductRepository productRepository;
	private final ReservationRepository reservationRepository;

	@Value("${kakao.map.js-key}")
	private String kakaoMapJsKey;

	@GetMapping("/dashboard")
	public String dashboard(HttpSession session, Model model) {
		Long userId = (Long) session.getAttribute("userId");
		if (userId == null) {
			return "redirect:/auth/loginForm";
		}

		StoreEntity store = storeRepository.findByOwnerId(userId).orElse(null);
		model.addAttribute("storeName", store != null ? store.getStoreName() : "매장 정보 없음");
		model.addAttribute("closingLabel", closingLabel(store));

		if (store == null) {
			addEmptyStats(model);
			return "storeView/dashboard";
		}

		LocalDateTime todayStart = LocalDate.now().atStartOfDay();
		LocalDateTime todayEnd = todayStart.plusDays(1);

		List<ProductEntity> todayProducts = fetchTodayProducts(store.getId(), todayStart, todayEnd);

		int registeredCount = todayProducts.size();
		int soldCount = todayProducts.stream()
				.mapToInt(p -> p.getQuantity() - p.getRemainingQuantity())
				.sum();
		int sellingNowCount = (int) todayProducts.stream().filter(p -> "active".equals(p.getStatus())).count();
		int expiredCount = (int) todayProducts.stream().filter(p -> "expired".equals(p.getStatus())).count();
		int rescueRate = registeredCount == 0 ? 0 : (int) Math.round(100.0 * soldCount / registeredCount);

		List<ReservationEntity> todayReservations =
				reservationRepository.findByStoreIdAndPickupTimeBetween(store.getId(), todayStart, todayEnd);
		int reservationCount = todayReservations.size();
		int reservationWaiting = (int) todayReservations.stream().filter(r -> "confirmed".equals(r.getStatus())).count();
		int reservationDone = (int) todayReservations.stream().filter(r -> "picked".equals(r.getStatus())).count();
		int reservationCancelled = (int) todayReservations.stream().filter(r -> "cancelled".equals(r.getStatus())).count();

		// 변경됨 — 왜: "오늘 매출"이 오늘 등록된 상품(product.registeredAt) 기준으로 계산돼서, 상품을
		// 며칠 전에 등록해두고 오늘 실제로 팔린 경우엔 매출이 전혀 안 잡히는 문제가 있었다. 실제로 오늘
		// 돈이 들어온 예약(reservation) 기준으로 바꾼다 — 취소(cancelled)는 환불되니 제외, pending은
		// 아직 결제 전이라 제외. noshowed는 환불 안 되므로(ReceiptService 참고) 매출에 포함한다.
		long todaySales = todayReservations.stream()
				.filter(r -> !"cancelled".equals(r.getStatus()) && !"pending".equals(r.getStatus()))
				.mapToLong(ReservationEntity::getTotalPrice)
				.sum();

		model.addAttribute("todaySales", formatWon(todaySales));
		model.addAttribute("salesDelta", ""); // TODO(문창호): 어제 매출 대비 증감 — 히스토리 데이터 쌓이면 계산
		model.addAttribute("soldCount", soldCount);
		model.addAttribute("registeredCount", registeredCount);
		model.addAttribute("sellingNowCount", sellingNowCount);
		model.addAttribute("reservationCount", reservationCount);
		model.addAttribute("reservationWaiting", reservationWaiting);
		model.addAttribute("reservationDone", reservationDone);
		model.addAttribute("reservationCancelled", reservationCancelled);
		model.addAttribute("expiredCount", expiredCount);
		model.addAttribute("rescueRate", rescueRate);
		model.addAttribute("rescueGoal", rescueRate >= 70 ? "목표 70% 달성" : "목표 70%");

		return "storeView/dashboard";
	}

	/**
	 * 추가됨 (2026-08-21) — 왜: 일간 판매·폐기 리포트 Excel 다운로드 (WBS 3.0). PDF는 이번 단계에서 제외.
	 * dashboard()와 똑같이 "오늘 등록된 상품" 기준으로 만들어서 화면 숫자랑 리포트 숫자가 항상 일치하게 한다.
	 */
	@GetMapping("/report/excel")
	public ResponseEntity<byte[]> reportExcel(HttpSession session) throws IOException {
		Long userId = (Long) session.getAttribute("userId");
		if (userId == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}

		StoreEntity store = storeRepository.findByOwnerId(userId).orElse(null);
		if (store == null) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
		}

		LocalDateTime todayStart = LocalDate.now().atStartOfDay();
		LocalDateTime todayEnd = todayStart.plusDays(1);
		List<ProductEntity> todayProducts = fetchTodayProducts(store.getId(), todayStart, todayEnd);

		byte[] excel = StoreReportExcelGenerator.generate(store.getStoreName(), todayProducts);

		// 파일명에 한글(매장명)을 그대로 넣으면 일부 브라우저에서 Content-Disposition 인코딩이 깨질 수
		// 있어서, 파일명은 날짜만 넣은 안전한 ASCII로 고정한다.
		String filename = "store-report-" + LocalDate.now() + ".xlsx";

		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
				.body(excel);
	}

	/**
	 * 추가됨 — 왜: dashboard.html에 "PDF는 이번 단계에서 제외"로 막아뒀던 버튼을 채운다 (WBS 3.0).
	 * reportExcel()과 데이터 소스(오늘 등록된 상품)는 동일 — 포맷만 PDF.
	 */
	@GetMapping("/report/pdf")
	public ResponseEntity<byte[]> reportPdf(HttpSession session) throws IOException {
		Long userId = (Long) session.getAttribute("userId");
		if (userId == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}

		StoreEntity store = storeRepository.findByOwnerId(userId).orElse(null);
		if (store == null) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
		}

		LocalDateTime todayStart = LocalDate.now().atStartOfDay();
		LocalDateTime todayEnd = todayStart.plusDays(1);
		List<ProductEntity> todayProducts = fetchTodayProducts(store.getId(), todayStart, todayEnd);

		byte[] pdf = StoreReportPdfGenerator.generate(store.getStoreName(), todayProducts);

		// reportExcel()과 동일한 이유로 파일명은 날짜만 넣은 안전한 ASCII로 고정한다.
		String filename = "store-report-" + LocalDate.now() + ".pdf";

		return ResponseEntity.ok()
				.contentType(MediaType.APPLICATION_PDF)
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
				.body(pdf);
	}

	/**
	 * 추가됨 — 왜: "매장 정보 등록/수정 (영업시간·위치·카테고리)" (WBS 3.0, 문창호 담당).
	 * /auth/owner-apply를 승인 후 수정 용도로 재사용하면 approvalStatus가 PENDING으로 리셋되는
	 * 문제가 있어서(그 화면은 "신청" 전용), 승인된 매장이 안전하게 정보만 고칠 수 있는 별도 화면을 둔다.
	 * 이 화면은 approvalStatus/businessNumber는 아예 건드리지 않는다.
	 */
	@GetMapping("/edit")
	public String editForm(HttpSession session, Model model) {
		Long userId = (Long) session.getAttribute("userId");
		if (userId == null) {
			return "redirect:/auth/loginForm";
		}

		StoreEntity store = storeRepository.findByOwnerId(userId).orElse(null);
		if (store == null) {
			return "redirect:/auth/owner-apply";
		}

		model.addAttribute("store", store);
		model.addAttribute("kakaoMapJsKey", kakaoMapJsKey);
		return "storeView/edit";
	}

	/**
	 * 상호명/사업자등록번호/주소는 승인 심사의 근거였던 값이라 여기서 안 받는다(폼에도 읽기전용으로만
	 * 표시) — 점주가 마음대로 바꾸면 "승인 안 된 가게가 승인된 척" 할 수 있는 구멍이 생긴다.
	 * 이 셋을 바꾸고 싶으면 운영자 재심사가 필요한데, 그건 슈퍼어드민 화면(송보미 담당)이 생긴 뒤
	 * "변경 요청 → 재승인" 플로우로 별도 구현할 예정 — 지금은 범위 밖.
	 *
	 * 위치(latitude/longitude)는 폼에서 직접 입력받지 않는다 — 주소를 사람이 손으로 좌표로 옮기는 건
	 * 비현실적이라, 화면에서 카카오맵 Geocoder(주소→좌표 변환)로 미리 계산해서 hidden input으로
	 * 같이 제출한다. 변환에 실패하면 null로 넘어와서 좌표 없이 저장되고(홈 지도엔 그 매장만 안 뜸),
	 * 그래도 나머지 정보 수정 자체는 막지 않는다.
	 */
	@PostMapping("/edit")
	public String editSubmit(@RequestParam String category,
	                         @RequestParam String phone,
	                         @RequestParam(required = false) String operatingHours,
	                         @RequestParam(required = false) Double latitude,
	                         @RequestParam(required = false) Double longitude,
	                         HttpSession session) {
		Long userId = (Long) session.getAttribute("userId");
		if (userId == null) {
			return "redirect:/auth/loginForm";
		}

		StoreEntity store = storeRepository.findByOwnerId(userId).orElse(null);
		if (store == null) {
			return "redirect:/auth/owner-apply";
		}

		if (category == null || category.isBlank() || phone == null || phone.isBlank()) {
			return "redirect:/store/edit?error";
		}

		store.setCategory(category.trim());
		store.setPhone(phone.trim());
		store.setOperatingHours(operatingHours != null && !operatingHours.isBlank() ? operatingHours.trim() : null);
		store.setLatitude(latitude);
		store.setLongitude(longitude);
		storeRepository.save(store);

		return "redirect:/store/dashboard?edited";
	}

	private List<ProductEntity> fetchTodayProducts(Long storeId, LocalDateTime todayStart, LocalDateTime todayEnd) {
		return productRepository.findByStoreId(storeId).stream()
				.filter(p -> p.getRegisteredAt() != null
						&& !p.getRegisteredAt().isBefore(todayStart)
						&& p.getRegisteredAt().isBefore(todayEnd))
				.toList();
	}

	private String closingLabel(StoreEntity store) {
		if (store == null) {
			return "";
		}
		StoreHoursUtil.ClosingInfo info = StoreHoursUtil.parse(store.getOperatingHours(), 60);
		return info.closeAt() != null ? "마감 " + info.closeAt().format(CLOSE_TIME_FORMAT) : "";
	}

	private String formatWon(long amount) {
		return String.format("%,d원", amount);
	}

	private void addEmptyStats(Model model) {
		model.addAttribute("todaySales", formatWon(0));
		model.addAttribute("salesDelta", "");
		model.addAttribute("soldCount", 0);
		model.addAttribute("registeredCount", 0);
		model.addAttribute("sellingNowCount", 0);
		model.addAttribute("reservationCount", 0);
		model.addAttribute("reservationWaiting", 0);
		model.addAttribute("reservationDone", 0);
		model.addAttribute("reservationCancelled", 0);
		model.addAttribute("expiredCount", 0);
		model.addAttribute("rescueRate", 0);
		model.addAttribute("rescueGoal", "목표 70%");
	}
}

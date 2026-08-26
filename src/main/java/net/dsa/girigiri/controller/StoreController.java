package net.dsa.girigiri.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.DailySalesBarDto;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

	private static final DateTimeFormatter DAY_LABEL_FORMAT = DateTimeFormatter.ofPattern("MM/dd");

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

		// 변경됨 — 왜: 구제율을 "판매수량 ÷ 상품 가짓수(registeredCount)"로 계산하고 있었다 — 수량을
		// 가짓수로 나누는 단위 불일치 버그라, 상품 1종류를 10개 등록해서 다 팔리면 1000%처럼 나올 수
		// 있었다. "오늘 판매 현황" 도넛 카드(아래)랑 분모를 맞추기 위해서라도, 등록 "수량 합계"
		// (totalQuantity) 기준으로 고친다.
		int totalQuantity = todayProducts.stream().mapToInt(ProductEntity::getQuantity).sum();
		int rescueRate = totalQuantity == 0 ? 0 : (int) Math.round(100.0 * soldCount / totalQuantity);

		// 추가됨 — 왜: "오늘 판매 현황" 도넛 카드 — 마감 전엔 판매(픽업완료)/예약됨(픽업대기)/남음(미정)
		// 3단계로, 마감 후엔 판매(=픽업완료+픽업대기 합산, 어차피 결제된 거라 다 "살린 것")/폐기 2단계로
		// 보여준다. "픽업했는지"는 ProductEntity엔 없는 개념이라, 오늘 등록 상품들에 걸린 예약을
		// 따로 모아서 status로 구분해야 한다.
		List<Long> todayProductIds = todayProducts.stream().map(ProductEntity::getId).toList();
		List<ReservationEntity> todayProductReservations =
				todayProductIds.isEmpty() ? List.of() : reservationRepository.findByProductIdIn(todayProductIds);
		int pickedCount = todayProductReservations.stream()
				.filter(r -> "picked".equals(r.getStatus()))
				.mapToInt(ReservationEntity::getReservedQuantity)
				.sum();
		int reservedNotPickedCount = soldCount - pickedCount;   // confirmed/ready — 취소분은 이미 remainingQuantity 복구로 soldCount에서 빠져있다.
		int idleCount = totalQuantity - soldCount;               // 아직 예약조차 안 된 수량

		StoreHoursUtil.ClosingInfo closingInfo = StoreHoursUtil.parse(store.getOperatingHours(), 60);
		boolean isClosed = closingInfo.closeAt() != null && !closingInfo.closeAt().isAfter(LocalDateTime.now());

		// conic-gradient에 바로 꽂을 수 있게 누적 퍼센트로 미리 계산해서 넘긴다 (Thymeleaf에서
		// 정수 나눗셈/반올림을 직접 하면 실수하기 쉬워서 컨트롤러에서 끝내둔다).
		int donutPickedPct = totalQuantity == 0 ? 0 : (int) Math.round(100.0 * pickedCount / totalQuantity);
		int donutReservedCumPct = totalQuantity == 0 ? 0 : (int) Math.round(100.0 * (pickedCount + reservedNotPickedCount) / totalQuantity);
		int donutSoldCumPct = totalQuantity == 0 ? 0 : (int) Math.round(100.0 * soldCount / totalQuantity);

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

		// 추가됨 — 왜: "오늘 매출"과 완전히 같은 방식(pickupTime 기준, 취소/대기 제외)으로 어제 매출을
		// 한 번 더 구해서 증감률을 낸다. 어제 매출이 0원이면 나눗셈이 안 되니 "신규 매출"로 따로 표시.
		LocalDateTime yesterdayStart = todayStart.minusDays(1);
		List<ReservationEntity> yesterdayReservations =
				reservationRepository.findByStoreIdAndPickupTimeBetween(store.getId(), yesterdayStart, todayStart);
		long yesterdaySales = yesterdayReservations.stream()
				.filter(r -> !"cancelled".equals(r.getStatus()) && !"pending".equals(r.getStatus()))
				.mapToLong(ReservationEntity::getTotalPrice)
				.sum();

		String salesDelta;
		String salesDeltaClass;
		if (yesterdaySales == 0) {
			salesDelta = todaySales == 0 ? "" : "어제 대비 신규 매출";
			salesDeltaClass = todaySales == 0 ? "u-mut" : "u-primary";
		} else {
			int changePct = (int) Math.round(100.0 * (todaySales - yesterdaySales) / yesterdaySales);
			if (changePct == 0) {
				salesDelta = "어제와 동일";
				salesDeltaClass = "u-mut";
			} else if (changePct > 0) {
				salesDelta = "▲ 어제 대비 +" + changePct + "%";
				salesDeltaClass = "u-primary";
			} else {
				salesDelta = "▼ 어제 대비 " + changePct + "%";
				salesDeltaClass = "u-danger";
			}
		}

		model.addAttribute("todaySales", formatWon(todaySales));
		model.addAttribute("salesDelta", salesDelta);
		model.addAttribute("salesDeltaClass", salesDeltaClass);
		model.addAttribute("soldCount", soldCount);
		model.addAttribute("registeredCount", registeredCount);
		model.addAttribute("sellingNowCount", sellingNowCount);
		model.addAttribute("reservationCount", reservationCount);
		model.addAttribute("reservationWaiting", reservationWaiting);
		model.addAttribute("reservationDone", reservationDone);
		model.addAttribute("reservationCancelled", reservationCancelled);
		model.addAttribute("expiredCount", expiredCount);
		int rescueGoalPercent = store.getRescueGoalPercent() != null ? store.getRescueGoalPercent() : 70;
		model.addAttribute("rescueRate", rescueRate);
		model.addAttribute("rescueGoalPercent", rescueGoalPercent);
		model.addAttribute("rescueGoal", rescueRate >= rescueGoalPercent
				? "목표 " + rescueGoalPercent + "% 달성"
				: "목표 " + rescueGoalPercent + "%");

		model.addAttribute("totalQuantity", totalQuantity);
		model.addAttribute("pickedCount", pickedCount);
		model.addAttribute("reservedNotPickedCount", reservedNotPickedCount);
		model.addAttribute("idleCount", idleCount);
		model.addAttribute("isClosed", isClosed);
		model.addAttribute("closingCountdownLabel", closingInfo.label());
		model.addAttribute("donutPickedPct", donutPickedPct);
		model.addAttribute("donutReservedCumPct", donutReservedCumPct);
		model.addAttribute("donutSoldCumPct", donutSoldCumPct);

		List<DailySalesBarDto> weeklySalesBars = buildWeeklySalesBars(store.getId());
		int weeklyTotalCount = weeklySalesBars.stream().mapToInt(DailySalesBarDto::count).sum();
		model.addAttribute("weeklySalesBars", weeklySalesBars);
		model.addAttribute("weeklyTotalCount", weeklyTotalCount);

		return "storeView/dashboard";
	}

	/**
	 * 추가됨 — 왜: "폐기 절감(구제율)" 카드의 목표치(70%)가 하드코딩이라 점주가 못 바꿨다. 별도 설정
	 * 화면을 만들 정도의 값은 아니라서, 대시보드 pill 옆 연필 아이콘 → prompt() → 이 엔드포인트로
	 * 바로 저장하는 가벼운 방식으로 처리한다.
	 */
	@PostMapping("/rescue-goal")
	public ResponseEntity<Void> updateRescueGoal(@RequestParam int percent, HttpSession session) {
		Long userId = (Long) session.getAttribute("userId");
		if (userId == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}

		StoreEntity store = storeRepository.findByOwnerId(userId).orElse(null);
		if (store == null) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
		}

		store.setRescueGoalPercent(Math.max(1, Math.min(100, percent)));
		storeRepository.save(store);
		return ResponseEntity.ok().build();
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

	/**
	 * 추가됨 — 왜: "오늘 판매 현황" 도넛 옆에 최근 7일 판매 개수를 막대그래프로 보여달라는 요청.
	 * 새 컬럼/새 리포지토리 메서드 없이, 이미 있는 findByStoreIdAndPickupTimeBetween 조회 기간만
	 * 7일로 넓혀서 재사용하고 날짜별 합산은 여기서 처리한다 — "오늘 매출"을 product.registeredAt이
	 * 아니라 reservation 기준으로 고친 것과 같은 이유로, 여기도 픽업 일자(pickupTime) 기준으로 묶는다.
	 */
	private List<DailySalesBarDto> buildWeeklySalesBars(Long storeId) {
		LocalDate today = LocalDate.now();
		LocalDateTime rangeStart = today.minusDays(6).atStartOfDay();
		LocalDateTime rangeEnd = today.plusDays(1).atStartOfDay();

		List<ReservationEntity> weekReservations =
				reservationRepository.findByStoreIdAndPickupTimeBetween(storeId, rangeStart, rangeEnd);

		Map<LocalDate, Integer> countsByDate = new HashMap<>();
		for (ReservationEntity r : weekReservations) {
			if ("cancelled".equals(r.getStatus()) || "pending".equals(r.getStatus())) {
				continue;
			}
			LocalDate date = r.getPickupTime().toLocalDate();
			countsByDate.merge(date, r.getReservedQuantity(), Integer::sum);
		}

		int maxCount = countsByDate.values().stream().mapToInt(Integer::intValue).max().orElse(0);

		List<DailySalesBarDto> bars = new ArrayList<>();
		for (int i = 6; i >= 0; i--) {
			LocalDate date = today.minusDays(i);
			int count = countsByDate.getOrDefault(date, 0);
			int heightPercent = maxCount == 0 ? 0 : (int) Math.round(100.0 * count / maxCount);
			// 판매가 있었는데도 비율이 너무 작아 막대가 안 보이면 "데이터 없음"과 구분이 안 되니 최소 높이를 준다.
			if (count > 0 && heightPercent < 6) {
				heightPercent = 6;
			}
			bars.add(new DailySalesBarDto(date.format(DAY_LABEL_FORMAT), count, heightPercent, date.equals(today)));
		}
		return bars;
	}

	private String formatWon(long amount) {
		return String.format("%,d원", amount);
	}

	private void addEmptyStats(Model model) {
		model.addAttribute("todaySales", formatWon(0));
		model.addAttribute("salesDelta", "");
		model.addAttribute("salesDeltaClass", "u-mut");
		model.addAttribute("soldCount", 0);
		model.addAttribute("registeredCount", 0);
		model.addAttribute("sellingNowCount", 0);
		model.addAttribute("reservationCount", 0);
		model.addAttribute("reservationWaiting", 0);
		model.addAttribute("reservationDone", 0);
		model.addAttribute("reservationCancelled", 0);
		model.addAttribute("expiredCount", 0);
		model.addAttribute("rescueRate", 0);
		model.addAttribute("rescueGoalPercent", 70);
		model.addAttribute("rescueGoal", "목표 70%");

		model.addAttribute("totalQuantity", 0);
		model.addAttribute("pickedCount", 0);
		model.addAttribute("reservedNotPickedCount", 0);
		model.addAttribute("idleCount", 0);
		model.addAttribute("isClosed", false);
		model.addAttribute("closingCountdownLabel", "");
		model.addAttribute("donutPickedPct", 0);
		model.addAttribute("donutReservedCumPct", 0);
		model.addAttribute("donutSoldCumPct", 0);

		model.addAttribute("weeklySalesBars", List.of());
		model.addAttribute("weeklyTotalCount", 0);
	}
}

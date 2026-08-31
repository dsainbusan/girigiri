package net.dsa.girigiri.service;

import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.StoreReportData;
import net.dsa.girigiri.domain.entity.ProductEntity;
import net.dsa.girigiri.domain.entity.ReservationEntity;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.repository.ProductRepository;
import net.dsa.girigiri.repository.ReservationRepository;
import net.dsa.girigiri.util.StoreHoursUtil;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 판매·폐기 리포트 데이터 조립 — WBS 3.0 "일/주간 판매·폐기 리포트" (문창호, 2026-08-30).
 *
 * 대시보드와 숫자가 어긋나지 않게 집계 방식을 맞춘다:
 *  - 일간: 상품 기준 (판매 = 등록수량 - 남은재고). 대시보드 "오늘 판매 현황" 도넛·"판매/등록" 카드와 동일.
 *  - 주간: 예약 기준 (판매·매출 = 픽업일이 최근 7일인 예약, 취소·결제대기 제외). 대시보드 "최근 7일" 막대와 동일.
 *          → 오늘 등록 안 된(며칠 전 등록됐거나 삭제된) 상품의 판매도 잡힌다.
 *  - 폐기 = 마감 지난 뒤(지난 날짜거나 오늘인데 영업 마감) 남은 재고. draft/skipped 제외.
 */
@Service
@RequiredArgsConstructor
public class StoreReportService {

	private static final long URGENT_THRESHOLD_MINUTES = 60;
	private static final double CO2_KG_PER_ITEM = 0.5;   // StoreController와 동일 계수 (팀 확정 전 임시)
	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MM/dd");

	private final ProductRepository productRepository;
	private final ReservationRepository reservationRepository;

	public StoreReportData build(StoreEntity store, boolean weekly) {
		return weekly ? buildWeekly(store) : buildDaily(store);
	}

	// --- 일간 (상품 기준) --------------------------------------------
	private StoreReportData buildDaily(StoreEntity store) {
		LocalDate today = LocalDate.now();
		LocalDateTime rangeStart = today.atStartOfDay();
		LocalDateTime rangeEnd = today.plusDays(1).atStartOfDay();
		boolean closedNow = closedNow(store);

		List<StoreReportData.Line> lines = new ArrayList<>();
		int totReg = 0, totSold = 0, totWaste = 0;
		long totSales = 0, totDiscount = 0;

		for (ProductEntity p : productsRegisteredBetween(store.getId(), rangeStart, rangeEnd)) {
			int orig = nz(p.getOriginalPrice());
			int disc = nz(p.getDiscountedPrice());
			int registered = nz(p.getQuantity());
			int sold = Math.max(0, registered - nz(p.getRemainingQuantity()));
			int wasted = (("expired".equals(p.getStatus())) || closedNow)
					? Math.max(0, nz(p.getRemainingQuantity())) : 0;
			int rate = orig == 0 ? 0 : (int) Math.round(100.0 * (orig - disc) / orig);
			long sales = (long) sold * disc;
			long discountGiven = (long) sold * (orig - disc);

			lines.add(new StoreReportData.Line(
					DAY.format(p.getRegisteredAt().toLocalDate()), p.getName(),
					orig, disc, rate, registered, sold, wasted, sales, discountGiven, ""));

			totReg += registered;
			totSold += sold;
			totWaste += wasted;
			totSales += sales;
			totDiscount += discountGiven;
		}

		int rescueRate = totReg == 0 ? 0 : (int) Math.round(100.0 * totSold / totReg);
		return new StoreReportData(store.getStoreName(), today + " (오늘)", false, lines,
				new StoreReportData.Totals(totReg, totSold, totWaste, totSales, totDiscount,
						rescueRate, String.format("%.1f", totSold * CO2_KG_PER_ITEM)));
	}

	// --- 주간 (예약 기준) --------------------------------------------
	private StoreReportData buildWeekly(StoreEntity store) {
		LocalDate today = LocalDate.now();
		LocalDate from = today.minusDays(6);
		LocalDateTime rangeStart = from.atStartOfDay();
		LocalDateTime rangeEnd = today.plusDays(1).atStartOfDay();
		boolean closedNow = closedNow(store);

		// 판매·매출 = 예약 (픽업일 기준, 취소·결제대기 제외)
		List<StoreReportData.Line> lines = new ArrayList<>();
		int totSold = 0;
		long totSales = 0;
		for (ReservationEntity r : reservationRepository.findByStoreIdAndPickupTimeBetween(store.getId(), rangeStart, rangeEnd)) {
			if ("cancelled".equals(r.getStatus()) || "pending".equals(r.getStatus()) || r.getPickupTime() == null) {
				continue;
			}
			int q = nz(r.getReservedQuantity());
			long price = nz(r.getTotalPrice());
			lines.add(new StoreReportData.Line(
					DAY.format(r.getPickupTime().toLocalDate()),
					r.getProductName() != null ? r.getProductName() : "(삭제된 상품)",
					0, 0, 0, 0, q, 0, price, 0, statusLabel(r.getStatus())));
			totSold += q;
			totSales += price;
		}
		lines.sort(Comparator.comparing(StoreReportData.Line::dateLabel));

		// 등록·폐기 = 상품 기준 (최근 7일에 등록된 상품)
		int totReg = 0, totWaste = 0;
		for (ProductEntity p : productsRegisteredBetween(store.getId(), rangeStart, rangeEnd)) {
			totReg += nz(p.getQuantity());
			LocalDate d = p.getRegisteredAt().toLocalDate();
			boolean windowOver = d.isBefore(today) || "expired".equals(p.getStatus())
					|| (d.equals(today) && closedNow);
			if (windowOver) {
				totWaste += Math.max(0, nz(p.getRemainingQuantity()));
			}
		}

		int denom = totSold + totWaste;
		int rescueRate = denom == 0 ? 0 : (int) Math.round(100.0 * totSold / denom);
		return new StoreReportData(store.getStoreName(), from + " ~ " + today + " (최근 7일)", true, lines,
				new StoreReportData.Totals(totReg, totSold, totWaste, totSales, 0L,
						rescueRate, String.format("%.1f", totSold * CO2_KG_PER_ITEM)));
	}

	// ---------------------------------------------------------------------

	private List<ProductEntity> productsRegisteredBetween(Long storeId, LocalDateTime start, LocalDateTime end) {
		return productRepository.findByStoreId(storeId).stream()
				.filter(p -> !"draft".equals(p.getStatus()) && !"skipped".equals(p.getStatus()))
				.filter(p -> p.getRegisteredAt() != null
						&& !p.getRegisteredAt().isBefore(start)
						&& p.getRegisteredAt().isBefore(end))
				.sorted(Comparator.comparing(ProductEntity::getRegisteredAt))
				.toList();
	}

	private boolean closedNow(StoreEntity store) {
		LocalDateTime closeAt = StoreHoursUtil.parse(store.getOperatingHours(), URGENT_THRESHOLD_MINUTES).closeAt();
		return closeAt != null && !closeAt.isAfter(LocalDateTime.now());
	}

	private String statusLabel(String status) {
		return switch (status) {
			case "picked" -> "픽업 완료";
			case "noshowed" -> "노쇼";
			case "ready" -> "픽업 대기";
			case "confirmed" -> "수락 대기";
			default -> status;
		};
	}

	private int nz(Integer v) {
		return v == null ? 0 : v;
	}
}

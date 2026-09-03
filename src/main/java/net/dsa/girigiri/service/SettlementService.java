package net.dsa.girigiri.service;

import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.SettlementData;
import net.dsa.girigiri.domain.dto.SettlementWeekPreview;
import net.dsa.girigiri.domain.entity.PayStatus;
import net.dsa.girigiri.domain.entity.PaymentEntity;
import net.dsa.girigiri.domain.entity.ReservationEntity;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.repository.PaymentRepository;
import net.dsa.girigiri.repository.ReservationRepository;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 매장 정산 데이터 조립 — WBS 2.0 "매장 정산 페이지" (문창호, 2026-08-31).
 *
 * 판매·폐기 리포트(StoreReportService)와는 다른 문서다:
 *  - 판매·폐기 리포트 = 운영·환경 지표 (구제율·폐기·CO₂)
 *  - 정산 = 회계 (결제 총액 → 플랫폼 수수료 뺀 정산 예정액, 환불 차감)
 *
 * 집계 기준: payment.paid_at (실제 결제 승인 시각). reservation.pickup_time이 아니라 결제일 기준으로
 * 묶어야 "이번 달 정산"이 회계 감각과 맞는다.
 *  - gross   = 기간 내 결제된 모든 금액 (PAID + 결제 후 취소된 것)
 *  - refund  = 그중 결제 후 취소·환불된 금액
 *  - net     = gross - refund  (= 유지된 결제액)
 *  - commission = net 기준 플랫폼 수수료 (환불된 건엔 수수료를 매기지 않는다)
 *  - payout  = net - commission  (정산 예정액)
 *
 * ⚠️ 지금은 MySQL(payment/reservation)에서 조회한다. 나중에 이 조립부만 Supabase REST(JSON)로
 *    갈아끼울 수 있게 화면(settlement.html)·생성기(PDF/Excel)와 분리해뒀다.
 */
@Service
@RequiredArgsConstructor
public class SettlementService {

	/**
	 * 플랫폼 수수료율 (%). 정책: 오픈 후 1년간 무료(0%) → 이후 결제액의 5%, 상한 5%.
	 * 이 앱은 "서비스 3년차" 가정이라 5%가 적용된 상태다.
	 * 나중에 매장별 우대율(초기 입점 매장 등)을 두려면 이 상수를 매장 설정값으로 빼면 된다.
	 */
	public static final int COMMISSION_RATE_PERCENT = 5;

	/** 최소 정산액 — 이 금액 미만이면 이번 주 지급을 미루고 다음 주 정산에 합산한다(소액 이체 방지). */
	public static final long MIN_PAYOUT = 10_000L;

	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MM/dd");
	private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

	private final ReservationRepository reservationRepository;
	private final PaymentRepository paymentRepository;

	public SettlementData build(StoreEntity store, String period) {
		return build(store, period, null, null);
	}

	/**
	 * from·to를 둘 다 주면(그리고 to가 from보다 앞이 아니면) 그 날짜 구간으로, 아니면 preset(period)으로 집계한다.
	 * 구간은 [from 00:00, to+1일 00:00) — to 당일까지 포함.
	 */
	public SettlementData build(StoreEntity store, String period, LocalDate from, LocalDate to) {
		Window w = (from != null && to != null && !to.isBefore(from))
				? new Window(from.atStartOfDay(), to.plusDays(1).atStartOfDay(),
						from.format(ISO_DATE) + " ~ " + to.format(ISO_DATE))
				: window(period);

		List<ReservationEntity> reservations = reservationRepository.findByStoreId(store.getId());
		if (reservations.isEmpty()) {
			return empty(store, w.label());
		}
		Map<Long, ReservationEntity> byResId = reservations.stream()
				.collect(Collectors.toMap(ReservationEntity::getId, r -> r, (a, b) -> a));
		List<PaymentEntity> payments =
				paymentRepository.findByReservationIdIn(new ArrayList<>(byResId.keySet()));

		List<SettlementData.Line> lines = new ArrayList<>();
		List<SettlementData.RefundLine> refunds = new ArrayList<>();
		long gross = 0, refund = 0, commission = 0;

		for (PaymentEntity pay : payments) {
			LocalDateTime paidAt = pay.getPaidAt();
			if (paidAt == null || paidAt.isBefore(w.start()) || !paidAt.isBefore(w.end())) {
				continue;   // 실제 결제된 적 없거나(READY/FAILED), 기간 밖
			}
			ReservationEntity r = byResId.get(pay.getReservationId());
			String name = (r != null && r.getProductName() != null) ? r.getProductName() : "(상품 정보 없음)";
			String dateLabel = DAY.format(paidAt.toLocalDate());
			long amount = pay.getAmount() == null ? 0 : pay.getAmount();

			if (pay.getPayStatus() == PayStatus.CANCELLED) {
				gross += amount;
				refund += amount;
				refunds.add(new SettlementData.RefundLine(dateLabel, name, amount));
			} else if (pay.getPayStatus() == PayStatus.PAID) {
				long fee = Math.round(amount * COMMISSION_RATE_PERCENT / 100.0);
				gross += amount;
				commission += fee;
				lines.add(new SettlementData.Line(dateLabel, name, amount, fee, amount - fee, statusLabel(r)));
			}
		}
		lines.sort(Comparator.comparing(SettlementData.Line::dateLabel));
		refunds.sort(Comparator.comparing(SettlementData.RefundLine::dateLabel));

		long net = gross - refund;
		long payout = net - commission;

		return new SettlementData(store.getStoreName(), w.label(), COMMISSION_RATE_PERCENT,
				gross, refund, net, commission, payout, lines, refunds);
	}

	// --- 주간 정산 확정용 집계 (라벨 없이 숫자만) ---------------------------

	/** 한 매장의 [start, end) 구간 집계 결과. build()와 동일 기준(결제 승인일). */
	public record Agg(long gross, long refund, long net, long commission) {
		public long weekAmount() {
			return net - commission;
		}
	}

	public Agg aggregate(StoreEntity store, LocalDateTime start, LocalDateTime end) {
		List<ReservationEntity> reservations = reservationRepository.findByStoreId(store.getId());
		if (reservations.isEmpty()) {
			return new Agg(0, 0, 0, 0);
		}
		Map<Long, ReservationEntity> byResId = reservations.stream()
				.collect(Collectors.toMap(ReservationEntity::getId, r -> r, (a, b) -> a));
		List<PaymentEntity> payments =
				paymentRepository.findByReservationIdIn(new ArrayList<>(byResId.keySet()));

		long gross = 0, refund = 0, commission = 0;
		for (PaymentEntity pay : payments) {
			LocalDateTime paidAt = pay.getPaidAt();
			if (paidAt == null || paidAt.isBefore(start) || !paidAt.isBefore(end)) {
				continue;
			}
			long amount = pay.getAmount() == null ? 0 : pay.getAmount();
			if (pay.getPayStatus() == PayStatus.CANCELLED) {
				gross += amount;
				refund += amount;
			} else if (pay.getPayStatus() == PayStatus.PAID) {
				gross += amount;
				commission += Math.round(amount * COMMISSION_RATE_PERCENT / 100.0);
			}
		}
		long net = gross - refund;
		return new Agg(gross, refund, net, commission);
	}

	// --- 이번 정산 주간 (진행 중 미리보기) -------------------------------

	/**
	 * 이번 정산 주간(이번 주 월~일)의 현재까지 쌓인 정산액. 다음 월요일 00:00에 확정된다.
	 * SettlementBatchService.confirmWeek()이 실제로 처리하는 "지난 주"와 같은 월~일 경계를 쓴다.
	 */
	public SettlementWeekPreview currentWeek(StoreEntity store) {
		LocalDate today = LocalDate.now();
		LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
		LocalDate sunday = monday.plusDays(6);
		Agg agg = aggregate(store, monday.atStartOfDay(), monday.plusDays(7).atStartOfDay());
		long amount = Math.max(0, agg.weekAmount());
		return new SettlementWeekPreview(monday, sunday, amount, sunday.plusDays(1), today.getDayOfWeek().getValue());
	}

	// --- 컨트롤러에서 이관 (2026-09-03, 레이어 규칙 정리) ---------------------

	public LocalDate parseDateOrNull(String s) {
		if (s == null || s.isBlank()) {
			return null;
		}
		try {
			return LocalDate.parse(s.trim());
		} catch (java.time.format.DateTimeParseException e) {
			return null;
		}
	}

	public String normalizeSettlementPeriod(String period) {
		return switch (period == null ? "" : period) {
			case "today", "week" -> period;
			default -> "month";
		};
	}

	public boolean isBankRegistered(StoreEntity store) {
		return store.getBankName() != null && !store.getBankName().isBlank()
				&& store.getBankAccount() != null && !store.getBankAccount().isBlank();
	}

	// --- 기간 ------------------------------------------------------------

	private record Window(LocalDateTime start, LocalDateTime end, String label) {}

	private Window window(String period) {
		LocalDate today = LocalDate.now();
		return switch (period == null ? "" : period) {
			case "today" -> new Window(today.atStartOfDay(), today.plusDays(1).atStartOfDay(),
					today.format(ISO_DATE) + " (오늘)");
			case "week" -> new Window(today.minusDays(6).atStartOfDay(), today.plusDays(1).atStartOfDay(),
					today.minusDays(6).format(ISO_DATE) + " ~ " + today.format(ISO_DATE) + " (최근 7일)");
			default -> {
				LocalDate first = today.withDayOfMonth(1);
				yield new Window(first.atStartOfDay(), first.plusMonths(1).atStartOfDay(),
						first.format(DateTimeFormatter.ofPattern("yyyy-MM")) + " (이번 달)");
			}
		};
	}

	private String statusLabel(ReservationEntity r) {
		if (r == null || r.getStatus() == null) {
			return "";
		}
		return switch (r.getStatus()) {
			case "picked" -> "픽업 완료";
			case "noshowed" -> "노쇼 (환불 없음)";
			case "ready" -> "픽업 대기";
			case "confirmed" -> "수락 대기";
			case "cancelled" -> "취소됨";
			default -> r.getStatus();
		};
	}

	private SettlementData empty(StoreEntity store, String label) {
		return new SettlementData(store.getStoreName(), label, COMMISSION_RATE_PERCENT,
				0, 0, 0, 0, 0, List.of(), List.of());
	}
}

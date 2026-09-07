package net.dsa.girigiri.service;

import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.SalesReportData;
import net.dsa.girigiri.domain.dto.SalesRow;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.util.SupabaseRestClient;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 매출 리포트 집계 (WBS 3.0 확장, 문창호).
 *
 * 데이터 소스가 MySQL이 아니라 Supabase(sales 테이블)라 SupabaseRestClient로 조회한다.
 * 판매·폐기 리포트(StoreReportService, MySQL · 구제율/폐기/CO₂)와는 목적도 소스도 다른 별개 기능이다.
 *
 * 기간 pill: today / thisweek(이번 주, 기본) / lastweek(지난 주) + from·to 직접 선택.
 * from·to가 둘 다 유효하면 pill을 무시하고 그 구간(custom)을 쓴다.
 * 주 경계는 월~일 — SettlementService의 정산 주와 동일하다(정산 확정분과 매출을 대조할 수 있게).
 * "이번 주 / 지난 주"일 때만 직전 주 대비 증감(전주 대비)을 함께 낸다.
 *
 * 이 서비스는 조회 전용이다. sales 테이블 적재는 실서비스라면 POS 웹훅이 담당하고,
 * 데모/개발용 시드 데이터는 sql/supabase-sales-seed.sql 로 Supabase에 직접 넣는다.
 */
@Service
@RequiredArgsConstructor
public class SalesReportService {

	private static final DateTimeFormatter MD = DateTimeFormatter.ofPattern("M/d");

	private final SupabaseRestClient supabase;

	/** .env에 Supabase 설정이 있는지. 화면에서 "준비 중" 분기용. */
	public boolean isConfigured() {
		return supabase.isConfigured();
	}

	/**
	 * period: today / thisweek(기본) / lastweek. from·to(yyyy-MM-dd)가 둘 다 유효하면 그 구간(custom).
	 * 주 경계는 월~일 (SettlementService의 정산 주와 동일).
	 */
	public SalesReportData build(StoreEntity store, String period, String from, String to) {
		LocalDate today = LocalDate.now();
		LocalDate thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

		LocalDate cf = parseDateOrNull(from);
		LocalDate ct = parseDateOrNull(to);
		boolean custom = cf != null && ct != null && !ct.isBefore(cf);

		LocalDate start;
		LocalDate end;
		String key;
		String label;
		boolean fullWeek = false;

		if (custom) {
			start = cf;
			end = ct;
			key = "custom";
			label = cf + " ~ " + ct;
		} else {
			key = switch (period == null ? "" : period) {
				case "today", "lastweek" -> period;
				default -> "thisweek";
			};
			switch (key) {
				case "today" -> {
					start = today;
					end = today;
					label = today + " (오늘)";
				}
				case "lastweek" -> {
					start = thisMonday.minusWeeks(1);
					end = start.plusDays(6);
					label = weekLabel(start, end) + " (지난 주)";
					fullWeek = true;
				}
				default -> {
					start = thisMonday;
					end = start.plusDays(6);
					label = weekLabel(start, end) + " (이번 주)";
					fullWeek = true;
				}
			}
		}

		List<SalesRow> rows = fetch(store.getId(), start, end);
		long total = rows.stream().mapToLong(SalesRow::amount).sum();
		int qty = rows.stream().mapToInt(SalesRow::qty).sum();

		// 전주 대비 — "한 주 전체"(이번 주/지난 주)를 볼 때만, 그리고 직전 주에 실제 매출이 있을 때만.
		// (직전 주 0원이면 나눌 수도 없고 "0원 대비"는 노이즈라 아예 표시 안 함 → prevTotal=null)
		Long prevTotal = null;
		Integer delta = null;
		if (fullWeek) {
			LocalDate prevStart = start.minusWeeks(1);
			LocalDate prevEnd = start.minusDays(1);
			long prev = fetch(store.getId(), prevStart, prevEnd).stream().mapToLong(SalesRow::amount).sum();
			if (prev > 0) {
				prevTotal = prev;
				delta = (int) Math.round((total - prev) * 100.0 / prev);
			}
		}

		boolean multiDay = !start.equals(end);

		return new SalesReportData(
				store.getStoreName(), label, key,
				custom ? cf.toString() : "", custom ? ct.toString() : "",
				multiDay, rows.isEmpty(),
				total, rows.size(), qty, prevTotal, delta,
				buildDays(rows, start, end, multiDay),
				buildProducts(rows, total));
	}

	// --- 내부 ---

	/** "9/8 ~ 9/14 (이번 주)" 식은 화면에서 만들고, 여기선 "2026-09-08 ~ 2026-09-14"만. */
	private String weekLabel(LocalDate monday, LocalDate sunday) {
		return monday + " ~ " + sunday;
	}

	/** "yyyy-MM-dd" → LocalDate. 비었거나 형식이 틀리면 null (호출부가 폴백). */
	private LocalDate parseDateOrNull(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return LocalDate.parse(value.trim());
		} catch (RuntimeException e) {
			return null;
		}
	}

	private List<SalesRow> fetch(Long storeId, LocalDate from, LocalDate toInclusive) {
		String query = "store_id=eq." + storeId
				+ "&sold_at=gte." + from
				+ "&sold_at=lte." + toInclusive
				+ "&order=sold_at.asc";
		return supabase.select("sales", query, SalesRow.class);
	}

	private List<SalesReportData.DayLine> buildDays(List<SalesRow> rows, LocalDate start, LocalDate end, boolean multiDay) {
		if (!multiDay) {
			return List.of();
		}
		Map<LocalDate, long[]> byDay = new TreeMap<>();   // [amount, count]
		for (SalesRow r : rows) {
			long[] agg = byDay.computeIfAbsent(r.soldAt(), k -> new long[2]);
			agg[0] += r.amount();
			agg[1] += 1;
		}
		long max = byDay.values().stream().mapToLong(a -> a[0]).max().orElse(0);

		long span = start.until(end).getDays() + 1;
		boolean labelByDayOfMonth = span >= 20;   // 한 달 단위면 라벨을 5일 간격으로 솎는다

		List<SalesReportData.DayLine> out = new ArrayList<>();
		for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
			long[] agg = byDay.getOrDefault(d, new long[2]);
			boolean showLabel;
			String label;
			if (labelByDayOfMonth) {
				int day = d.getDayOfMonth();
				label = String.valueOf(day);
				showLabel = day == 1 || day % 5 == 0 || d.equals(end);
			} else {
				label = d.format(MD);
				showLabel = true;
			}
			out.add(new SalesReportData.DayLine(
					label, showLabel, agg[0], (int) agg[1],
					max > 0 ? (int) Math.round(agg[0] * 100.0 / max) : 0));
		}
		return out;
	}

	private List<SalesReportData.ProductLine> buildProducts(List<SalesRow> rows, long total) {
		Map<String, long[]> byName = new LinkedHashMap<>();   // [qty, amount]
		Map<String, String> categoryOf = new HashMap<>();
		for (SalesRow r : rows) {
			long[] agg = byName.computeIfAbsent(r.productName(), k -> new long[2]);
			agg[0] += r.qty();
			agg[1] += r.amount();
			categoryOf.putIfAbsent(r.productName(), r.category());
		}
		return byName.entrySet().stream()
				.sorted((a, b) -> Long.compare(b.getValue()[1], a.getValue()[1]))
				.map(e -> new SalesReportData.ProductLine(
						e.getKey(),
						categoryOf.get(e.getKey()),
						(int) e.getValue()[0],
						e.getValue()[1],
						total > 0 ? (int) Math.round(e.getValue()[1] * 100.0 / total) : 0))
				.toList();
	}
}

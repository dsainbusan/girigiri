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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 매출 리포트 집계 (WBS 3.0, 문창호).
 *
 * 데이터 소스가 MySQL이 아니라 Supabase(sales 테이블)라 SupabaseRestClient로 조회한다.
 * 한 행 = (날짜, 상품)별 마감 진열 스냅샷(등록/판매/정상가/할인가)이라, 여기서
 * 매출(회수 매출·전주 대비)과 구제 성과(구제율·폐기·CO₂·할인 제공액)를 함께 낸다.
 * → /store/report(판매·폐기 리포트, MySQL)를 이 리포트로 대체한다.
 *
 * 기간 pill: today / thisweek(기본) / lastweek + from·to 직접 선택. 주 경계 월~일 = 정산 주와 동일.
 * "이번 주 / 지난 주"일 때만 직전 주 대비 증감(전주 대비)을 함께 낸다.
 *
 * 이 서비스는 조회 전용이다. sales 적재는 실서비스라면 POS 웹훅이,
 * 데모/개발용 시드는 sql/supabase-sales-seed.sql로 Supabase에 직접 넣는다.
 */
@Service
@RequiredArgsConstructor
public class SalesReportService {

	/**
	 * CO₂ 절감(kg) = 판매 수량 × 카테고리 계수.
	 * 계수 = (품목당 평균 중량 가정) × (식품 LCA 상 kg당 CO₂e 일반값). 정확한 값은 팀에서 튜닝.
	 * 무게를 품목마다 입력받지 않고 카테고리만으로 자동 산출하려는 절충안.
	 */
	private static final Map<String, Double> CO2_PER_ITEM_KG = Map.of(
			"베이커리", 0.3, "반찬", 0.6, "도시락", 1.0, "카페", 0.3,
			"음료", 0.3, "청과", 0.3, "정육", 1.5, "기타", 0.7);
	private static final double CO2_PER_ITEM_DEFAULT = 0.5;

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
					label = start + " ~ " + end + " (지난 주)";
					fullWeek = true;
				}
				default -> {
					start = thisMonday;
					end = start.plusDays(6);
					label = start + " ~ " + end + " (이번 주)";
					fullWeek = true;
				}
			}
		}

		List<SalesRow> rows = fetch(store.getId(), start, end);

		long totalSales = rows.stream().mapToLong(SalesRow::revenue).sum();
		long discountGiven = rows.stream().mapToLong(SalesRow::discountGiven).sum();
		int registered = rows.stream().mapToInt(SalesRow::registeredQty).sum();
		int sold = rows.stream().mapToInt(SalesRow::soldQty).sum();
		int wasted = registered - sold;
		int rescueRate = registered > 0 ? (int) Math.round(sold * 100.0 / registered) : 0;
		double co2 = round1(rows.stream().mapToDouble(this::co2Of).sum());

		Long prevTotal = null;
		Integer delta = null;
		if (fullWeek) {
			LocalDate prevStart = start.minusWeeks(1);
			LocalDate prevEnd = start.minusDays(1);
			long prev = fetch(store.getId(), prevStart, prevEnd).stream().mapToLong(SalesRow::revenue).sum();
			if (prev > 0) {
				prevTotal = prev;
				delta = (int) Math.round((totalSales - prev) * 100.0 / prev);
			}
		}

		boolean multiDay = !start.equals(end);

		return new SalesReportData(
				store.getStoreName(), label, key,
				custom ? cf.toString() : "", custom ? ct.toString() : "",
				multiDay, rows.isEmpty(),
				totalSales, discountGiven, prevTotal, delta,
				registered, sold, wasted, rescueRate, co2,
				buildDays(rows, start, end, multiDay),
				buildProducts(rows));
	}

	// --- 내부 ---

	private double co2Of(SalesRow r) {
		double coeff = CO2_PER_ITEM_KG.getOrDefault(r.category(), CO2_PER_ITEM_DEFAULT);
		return r.soldQty() * coeff;
	}

	private static double round1(double v) {
		return Math.round(v * 10.0) / 10.0;
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
				+ "&sale_date=gte." + from
				+ "&sale_date=lte." + toInclusive
				+ "&order=sale_date.asc";
		return supabase.select("sales", query, SalesRow.class);
	}

	private List<SalesReportData.DayLine> buildDays(List<SalesRow> rows, LocalDate start, LocalDate end, boolean multiDay) {
		if (!multiDay) {
			return List.of();
		}
		// [amount, sold, wasted, registered]
		Map<LocalDate, long[]> byDay = new TreeMap<>();
		for (SalesRow r : rows) {
			long[] agg = byDay.computeIfAbsent(r.saleDate(), k -> new long[4]);
			agg[0] += r.revenue();
			agg[1] += r.soldQty();
			agg[2] += r.wastedQty();
			agg[3] += r.registeredQty();
		}
		long maxReg = byDay.values().stream().mapToLong(a -> a[3]).max().orElse(0);

		long span = start.until(end).getDays() + 1;
		boolean thinLabels = span >= 20;   // 한 달 단위면 라벨을 5일 간격으로 솎는다

		List<SalesReportData.DayLine> out = new ArrayList<>();
		for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
			long[] agg = byDay.getOrDefault(d, new long[4]);
			boolean showLabel;
			String label;
			if (thinLabels) {
				int day = d.getDayOfMonth();
				label = String.valueOf(day);
				showLabel = day == 1 || day % 5 == 0 || d.equals(end);
			} else {
				label = d.format(MD);
				showLabel = true;
			}
			int soldH = maxReg > 0 ? (int) Math.round(agg[1] * 100.0 / maxReg) : 0;
			int wastedH = maxReg > 0 ? (int) Math.round(agg[2] * 100.0 / maxReg) : 0;
			out.add(new SalesReportData.DayLine(
					label, showLabel, agg[0], (int) agg[1], (int) agg[2], soldH, wastedH));
		}
		return out;
	}

	private List<SalesReportData.ProductLine> buildProducts(List<SalesRow> rows) {
		// [registered, sold, amount]
		Map<String, long[]> byName = new LinkedHashMap<>();
		Map<String, String> categoryOf = new LinkedHashMap<>();
		for (SalesRow r : rows) {
			long[] agg = byName.computeIfAbsent(r.productName(), k -> new long[3]);
			agg[0] += r.registeredQty();
			agg[1] += r.soldQty();
			agg[2] += r.revenue();
			categoryOf.putIfAbsent(r.productName(), r.category());
		}
		return byName.entrySet().stream()
				.sorted((a, b) -> Long.compare(b.getValue()[2], a.getValue()[2]))
				.map(e -> {
					long reg = e.getValue()[0];
					long s = e.getValue()[1];
					int rate = reg > 0 ? (int) Math.round(s * 100.0 / reg) : 0;
					return new SalesReportData.ProductLine(
							e.getKey(), categoryOf.get(e.getKey()),
							(int) reg, (int) s, (int) (reg - s), e.getValue()[2], rate);
				})
				.toList();
	}
}

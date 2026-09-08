package net.dsa.girigiri.util;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import net.dsa.girigiri.domain.dto.SalesReportData;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * 매출 리포트 PDF 생성 (WBS 3.0, 문창호). SalesReportExcelGenerator와 데이터 소스(SalesReportData) 동일,
 * 출력 포맷만 PDF. openhtmltopdf로 HTML 문자열 → PDF 렌더링.
 *
 * ⚠️ openhtmltopdf는 HTML을 XML로 파싱한다 — &nbsp; 같은 명명 엔티티를 쓰면 파싱 실패. 리터럴만 쓸 것.
 * 한글 폰트는 /fonts/UnDotum.ttf (프로젝트에 있는 유일한 폰트 파일).
 */
public final class SalesReportPdfGenerator {

	private static final String FONT_FAMILY = "UnDotum";
	private static final String FONT_RESOURCE_PATH = "/fonts/UnDotum.ttf";

	private SalesReportPdfGenerator() {
	}

	public static byte[] generate(SalesReportData data) throws IOException {
		String html = buildHtml(data);
		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			PdfRendererBuilder builder = new PdfRendererBuilder();
			builder.useFastMode();
			builder.useFont(() -> SalesReportPdfGenerator.class.getResourceAsStream(FONT_RESOURCE_PATH), FONT_FAMILY);
			builder.withHtmlContent(html, null);
			builder.toStream(out);
			builder.run();
			return out.toByteArray();
		} catch (IOException e) {
			throw e;
		} catch (Exception e) {
			throw new IOException("매출 리포트 PDF 생성에 실패했습니다.", e);
		}
	}

	private static String buildHtml(SalesReportData data) {
		return """
				<html>
				<head><style>
					@page { size: A4; margin: 30px 34px 34px; }
					body { font-family: '%s', sans-serif; color: #1f2937; font-size: 11px; }

					.head { border-top: 4px solid #15803D; padding-top: 12px; }
					.head h1 { font-size: 21px; font-weight: bold; margin: 0; letter-spacing: -0.02em; }
					.head .store { font-size: 12px; font-weight: bold; margin-top: 3px; }
					.metabar { width: 100%%; margin-top: 10px; border-bottom: 2px solid #1f2937; padding-bottom: 8px; }
					.metabar td { font-size: 10.5px; color: #4b5563; }
					.metabar .right { text-align: right; }

					.kpi { width: 100%%; border-collapse: separate; border-spacing: 7px 0; margin: 16px 0 6px; }
					.kpi td { width: 25%%; border: 1px solid #d1d5db; background: #fafafa; padding: 9px 10px; vertical-align: top; }
					.kpi .label { display: block; font-size: 9.5px; color: #6b7280; margin-bottom: 5px; }
					.kpi .value { display: block; font-size: 15px; font-weight: bold; }
					.kpi .value.accent { color: #15803D; }

					.aux { font-size: 10px; color: #4b5563; margin: 6px 2px 0; }
					.aux .up { color: #15803D; font-weight: bold; }
					.aux .down { color: #DC2626; font-weight: bold; }

					h2 { font-size: 12px; font-weight: bold; margin: 20px 0 6px; padding-left: 7px; border-left: 3px solid #15803D; }

					table.grid { width: 100%%; border-collapse: collapse; font-size: 10.5px; }
					table.grid th, table.grid td { border: 1px solid #e5e7eb; padding: 5px 8px; text-align: left; }
					table.grid th { background: #DCFCE7; color: #14532D; font-weight: bold; }
					table.grid td.n { text-align: right; font-variant-numeric: tabular-nums; }
					table.grid tr:nth-child(2n) td { background: #fbfbfb; }
					table.grid tr.total td { background: #f0fdf4; font-weight: bold; border-top: 1.5px solid #15803D; }
					table.grid td.waste { color: #DC2626; }

					.foot { margin-top: 18px; border-top: 1px solid #e5e7eb; padding-top: 8px; font-size: 9px; color: #9ca3af; }
				</style></head>
				<body>
					<div class="head">
						<h1>매출 리포트</h1>
						<div class="store">%s</div>
						<table class="metabar"><tr>
							<td>조회 기간: <b>%s</b></td>
							<td class="right">발행일 %s</td>
						</tr></table>
					</div>

					<table class="kpi"><tr>
						<td><span class="label">회수 매출</span><span class="value accent">%,d원</span></td>
						<td><span class="label">구제율</span><span class="value accent">%d%%</span></td>
						<td><span class="label">판매 / 등록</span><span class="value">%d / %d개</span></td>
						<td><span class="label">CO2 절감</span><span class="value">%skg</span></td>
					</tr></table>
					<p class="aux">폐기 %d개 · 할인 제공액 %,d원%s</p>

					<h2>상품별</h2>
					%s
					%s

					<div class="foot">구제율 = 판매 ÷ 등록 · CO2 절감은 카테고리별 배출계수 × 판매 수량 추정치 · 데이터 출처 Supabase · girigiri</div>
				</body>
				</html>
				""".formatted(FONT_FAMILY,
				esc(data.storeName()), esc(data.periodLabel()), esc(issuedDate()),
				data.totalSales(), data.rescueRatePercent(), data.soldCount(), data.registeredCount(), data.co2Kg(),
				data.wastedCount(), data.discountGiven(), deltaFragment(data),
				productsTable(data), daySection(data));
	}

	private static String deltaFragment(SalesReportData d) {
		if (d.prevTotalSales() == null || d.deltaPercent() == null) {
			return "";
		}
		boolean up = d.deltaPercent() >= 0;
		return " · 전주(%,d원) 대비 <span class=\"%s\">%s%d%%</span>".formatted(
				d.prevTotalSales(), up ? "up" : "down", up ? "▲ +" : "▼ ", d.deltaPercent());
	}

	private static String productsTable(SalesReportData data) {
		if (data.products().isEmpty()) {
			return "<table class=\"grid\"><tr><td>이 기간에 매출 데이터가 없습니다.</td></tr></table>";
		}
		StringBuilder rows = new StringBuilder();
		for (SalesReportData.ProductLine p : data.products()) {
			rows.append("""
					<tr>
						<td>%s</td><td>%s</td><td class="n">%d</td><td class="n">%d</td>
						<td class="n waste">%d</td><td class="n">%,d원</td><td class="n">%d%%</td>
					</tr>
					""".formatted(esc(p.name()), esc(p.category() == null ? "-" : p.category()),
					p.registeredQty(), p.soldQty(), p.wastedQty(), p.amount(), p.rescueRatePercent()));
		}
		rows.append("""
				<tr class="total">
					<td colspan="2">합계</td><td class="n">%d</td><td class="n">%d</td>
					<td class="n">%d</td><td class="n">%,d원</td><td class="n">%d%%</td>
				</tr>
				""".formatted(data.registeredCount(), data.soldCount(), data.wastedCount(),
				data.totalSales(), data.rescueRatePercent()));
		return """
				<table class="grid">
					<tr><th>상품명</th><th>카테고리</th><th>등록</th><th>판매</th><th>폐기</th><th>매출</th><th>구제율</th></tr>
					%s
				</table>
				""".formatted(rows);
	}

	private static String daySection(SalesReportData data) {
		if (!data.showGraph() || data.days().isEmpty()) {
			return "";
		}
		StringBuilder days = new StringBuilder();
		for (SalesReportData.DayLine d : data.days()) {
			days.append("""
					<tr><td>%s</td><td class="n">%d</td><td class="n waste">%d</td><td class="n">%,d원</td></tr>
					""".formatted(esc(d.label()), d.soldQty(), d.wastedQty(), d.amount()));
		}
		return """
				<h2>일별</h2>
				<table class="grid">
					<tr><th>날짜</th><th>판매</th><th>폐기</th><th>매출</th></tr>
					%s
				</table>
				""".formatted(days);
	}

	private static String issuedDate() {
		return java.time.LocalDate.now().toString();
	}

	private static String esc(String s) {
		if (s == null) {
			return "";
		}
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}

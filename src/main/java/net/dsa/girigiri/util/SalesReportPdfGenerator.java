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
		StringBuilder products = new StringBuilder();
		for (SalesReportData.ProductLine p : data.products()) {
			products.append("""
					<tr>
						<td>%s</td><td>%s</td><td class="n">%d</td><td class="n">%d</td>
						<td class="n">%d</td><td class="n">%,d원</td><td class="n">%d%%</td>
					</tr>
					""".formatted(esc(p.name()), esc(p.category() == null ? "-" : p.category()),
					p.registeredQty(), p.soldQty(), p.wastedQty(), p.amount(), p.rescueRatePercent()));
		}
		if (data.products().isEmpty()) {
			products.append("<tr><td colspan=\"7\">이 기간에 매출 데이터가 없습니다.</td></tr>");
		} else {
			products.append("""
					<tr class="total">
						<td colspan="2">합계</td><td class="n">%d</td><td class="n">%d</td>
						<td class="n">%d</td><td class="n">%,d원</td><td class="n">%d%%</td>
					</tr>
					""".formatted(data.registeredCount(), data.soldCount(), data.wastedCount(),
					data.totalSales(), data.rescueRatePercent()));
		}

		String daySection = "";
		if (data.showGraph() && !data.days().isEmpty()) {
			StringBuilder days = new StringBuilder();
			for (SalesReportData.DayLine d : data.days()) {
				days.append("""
						<tr><td>%s</td><td class="n">%d</td><td class="n">%d</td><td class="n">%,d원</td></tr>
						""".formatted(esc(d.label()), d.soldQty(), d.wastedQty(), d.amount()));
			}
			daySection = """
					<h2>일별</h2>
					<table>
						<tr><th>날짜</th><th>판매</th><th>폐기</th><th>매출</th></tr>
						%s
					</table>
					""".formatted(days);
		}

		return """
				<html>
				<head><style>
					body { font-family: '%s', sans-serif; padding: 24px; color: #1f2937; }
					h1 { font-size: 18px; margin: 0; }
					h2 { font-size: 13px; margin: 18px 0 6px; }
					.sub { color: #6b7280; font-size: 12px; margin: 4px 0 16px; }
					.summary { font-size: 12px; margin-bottom: 12px; line-height: 1.6; }
					.summary b { font-size: 14px; }
					table { width: 100%%; border-collapse: collapse; font-size: 11px; }
					th, td { border: 1px solid #e5e7eb; padding: 5px 7px; text-align: left; }
					th { background: #f3f4f6; }
					td.n { text-align: right; }
					tr.total td { background: #f9fafb; font-weight: bold; }
				</style></head>
				<body>
					<h1>%s 매출 리포트</h1>
					<p class="sub">%s</p>
					<p class="summary">%s</p>
					<h2>상품별</h2>
					<table>
						<tr><th>상품명</th><th>카테고리</th><th>등록</th><th>판매</th><th>폐기</th><th>매출</th><th>구제율</th></tr>
						%s
					</table>
					%s
				</body>
				</html>
				""".formatted(FONT_FAMILY, esc(data.storeName()), esc(data.periodLabel()),
				summaryLine(data), products, daySection);
	}

	private static String summaryLine(SalesReportData d) {
		String s = "회수 매출 <b>%,d원</b> · 판매 %d개 · 구제율 %d%% · 폐기 %d개 · CO2 절감 %skg · 할인 제공 %,d원".formatted(
				d.totalSales(), d.soldCount(), d.rescueRatePercent(), d.wastedCount(), d.co2Kg(), d.discountGiven());
		if (d.prevTotalSales() != null && d.deltaPercent() != null) {
			s += " · 전주 %,d원 대비 %s%d%%".formatted(
					d.prevTotalSales(), d.deltaPercent() >= 0 ? "+" : "", d.deltaPercent());
		}
		return s;
	}

	private static String esc(String s) {
		if (s == null) {
			return "";
		}
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}

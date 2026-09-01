package net.dsa.girigiri.util;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import net.dsa.girigiri.domain.dto.StoreReportData;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * 판매·폐기 리포트 PDF 생성 (WBS 3.0, 문창호). StoreReportExcelGenerator와 데이터 소스(StoreReportData)는
 * 동일, 출력 포맷만 PDF. ReceiptPdfGenerator와 같은 패턴 — HTML 문자열 → PDF 렌더링.
 *
 * ⚠️ openhtmltopdf는 HTML을 XML로 파싱한다 — &nbsp; 같은 명명 엔티티를 쓰면 파싱 실패한다. 리터럴만 쓸 것.
 */
public final class StoreReportPdfGenerator {

	// ReceiptPdfGenerator와 동일한 한글 폰트를 쓴다. (예전엔 존재하지 않는 NotoSansKR 경로를 가리켜서
	//  폰트 로딩이 조용히 실패 → openhtmltopdf 기본 폰트엔 한글 글리프가 없어 전부 '#'으로 나왔다.)
	private static final String FONT_FAMILY = "UnDotum";
	private static final String FONT_RESOURCE_PATH = "/fonts/UnDotum.ttf";

	private StoreReportPdfGenerator() {
	}

	public static byte[] generate(StoreReportData data) throws IOException {
		String html = buildHtml(data);
		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			PdfRendererBuilder builder = new PdfRendererBuilder();
			builder.useFastMode();
			builder.useFont(() -> StoreReportPdfGenerator.class.getResourceAsStream(FONT_RESOURCE_PATH), FONT_FAMILY);
			builder.withHtmlContent(html, null);
			builder.toStream(out);
			builder.run();
			return out.toByteArray();
		} catch (IOException e) {
			throw e;
		} catch (Exception e) {
			throw new IOException("판매·폐기 리포트 PDF 생성에 실패했습니다.", e);
		}
	}

	private static String buildHtml(StoreReportData data) {
		return data.weekly() ? buildWeekly(data) : buildDaily(data);
	}

	// --- 일간: 상품별 표 -------------------------------------------------
	private static String buildDaily(StoreReportData data) {
		StringBuilder rows = new StringBuilder();
		for (StoreReportData.Line l : data.lines()) {
			rows.append("""
					<tr>
						<td>%s</td><td class="n">%,d원</td><td class="n">%,d원</td><td class="n">%d%%</td>
						<td class="n">%d</td><td class="n">%d</td><td class="n">%d</td><td class="n">%,d원</td><td class="n">%,d원</td>
					</tr>
					""".formatted(esc(l.name()), l.originalPrice(), l.discountedPrice(), l.discountRate(),
					l.registered(), l.sold(), l.wasted(), l.sales(), l.discountGiven()));
		}
		if (data.lines().isEmpty()) {
			rows.append("<tr><td colspan=\"9\">오늘 등록된 상품이 없습니다.</td></tr>");
		}
		StoreReportData.Totals t = data.totals();
		String total = """
				<tr class="total">
					<td colspan="4">합계</td>
					<td class="n">%d</td><td class="n">%d</td><td class="n">%d</td><td class="n">%,d원</td><td class="n">%,d원</td>
				</tr>
				""".formatted(t.registered(), t.sold(), t.wasted(), t.sales(), t.discountGiven());

		String summary = ("구제율 <b>%d%%</b> · 등록 %d · 판매 %d · 폐기 %d · 회수 매출 %,d원 "
				+ "· 할인 제공 %,d원 · CO2 절감 %skg").formatted(
				t.rescueRate(), t.registered(), t.sold(), t.wasted(), t.sales(), t.discountGiven(), t.co2Kg());

		String head = "<th>상품명</th><th>원가</th><th>할인가</th><th>할인율</th>"
				+ "<th>등록</th><th>판매</th><th>폐기</th><th>회수 매출</th><th>할인액</th>";
		return page(esc(data.storeName()), esc(data.periodLabel()), summary, head, rows + total);
	}

	// --- 주간: 판매 내역(예약) 표 --------------------------------------
	private static String buildWeekly(StoreReportData data) {
		StringBuilder rows = new StringBuilder();
		for (StoreReportData.Line l : data.lines()) {
			rows.append("""
					<tr>
						<td>%s</td><td>%s</td><td class="n">%d</td><td class="n">%,d원</td><td>%s</td>
					</tr>
					""".formatted(esc(l.dateLabel()), esc(l.name()), l.sold(), l.sales(), esc(l.status())));
		}
		if (data.lines().isEmpty()) {
			rows.append("<tr><td colspan=\"5\">최근 7일간 판매 내역이 없습니다.</td></tr>");
		}
		StoreReportData.Totals t = data.totals();
		String total = """
				<tr class="total">
					<td colspan="2">합계</td><td class="n">%d</td><td class="n">%,d원</td><td></td>
				</tr>
				""".formatted(t.sold(), t.sales());

		String summary = ("구제율 <b>%d%%</b> · 판매 %d · 폐기 %d · 등록 %d · 회수 매출 %,d원 · CO2 절감 %skg").formatted(
				t.rescueRate(), t.sold(), t.wasted(), t.registered(), t.sales(), t.co2Kg());

		String head = "<th>픽업일</th><th>상품명</th><th>수량</th><th>결제 금액</th><th>상태</th>";
		return page(esc(data.storeName()), esc(data.periodLabel()), summary, head, rows + total);
	}

	private static String page(String storeName, String periodLabel, String summary, String headCells, String bodyRows) {
		return """
				<html>
				<head><style>
					body { font-family: '%s', sans-serif; padding: 24px; color: #1f2937; }
					h1 { font-size: 18px; margin: 0; }
					.sub { color: #6b7280; font-size: 12px; margin: 4px 0 16px; }
					.summary { font-size: 12px; margin-bottom: 12px; }
					.summary b { font-size: 14px; }
					table { width: 100%%; border-collapse: collapse; font-size: 11px; }
					th, td { border: 1px solid #e5e7eb; padding: 5px 7px; text-align: left; }
					th { background: #f3f4f6; }
					td.n { text-align: right; }
					tr.total td { background: #f9fafb; font-weight: bold; }
				</style></head>
				<body>
					<h1>%s 판매·폐기 리포트</h1>
					<p class="sub">%s</p>
					<p class="summary">%s</p>
					<table>
						<tr>%s</tr>
						%s
					</table>
				</body>
				</html>
				""".formatted(FONT_FAMILY, storeName, periodLabel, summary, headCells, bodyRows);
	}

	private static String esc(String s) {
		if (s == null) {
			return "";
		}
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}

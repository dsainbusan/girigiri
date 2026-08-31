package net.dsa.girigiri.util;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import net.dsa.girigiri.domain.dto.SettlementData;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * 매장 정산 PDF 생성 (WBS 2.0, 문창호). SettlementExcelGenerator와 데이터 소스(SettlementData)는
 * 동일, 출력 포맷만 PDF. StoreReportPdfGenerator와 같은 패턴 — HTML 문자열 → PDF 렌더링.
 *
 * ⚠️ openhtmltopdf는 HTML을 XML로 파싱한다 — &nbsp; 같은 명명 엔티티는 파싱 실패. 리터럴만 쓸 것.
 */
public final class SettlementPdfGenerator {

	private static final String FONT_FAMILY = "UnDotum";
	private static final String FONT_RESOURCE_PATH = "/fonts/UnDotum.ttf";

	private SettlementPdfGenerator() {
	}

	public static byte[] generate(SettlementData data) throws IOException {
		String html = buildHtml(data);
		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			PdfRendererBuilder builder = new PdfRendererBuilder();
			builder.useFastMode();
			builder.useFont(() -> SettlementPdfGenerator.class.getResourceAsStream(FONT_RESOURCE_PATH), FONT_FAMILY);
			builder.withHtmlContent(html, null);
			builder.toStream(out);
			builder.run();
			return out.toByteArray();
		} catch (IOException e) {
			throw e;
		} catch (Exception e) {
			throw new IOException("매장 정산 PDF 생성에 실패했습니다.", e);
		}
	}

	private static String buildHtml(SettlementData data) {
		StringBuilder rows = new StringBuilder();
		for (SettlementData.Line l : data.lines()) {
			rows.append("""
					<tr>
						<td>%s</td><td>%s</td><td class="n">%,d원</td><td class="n">%,d원</td><td class="n">%,d원</td><td>%s</td>
					</tr>
					""".formatted(esc(l.dateLabel()), esc(l.name()), l.amount(), l.commission(), l.payout(), esc(l.status())));
		}
		if (data.lines().isEmpty()) {
			rows.append("<tr><td colspan=\"6\">이 기간에 정산 대상 결제가 없습니다.</td></tr>");
		} else {
			rows.append("""
					<tr class="total">
						<td colspan="2">합계</td>
						<td class="n">%,d원</td><td class="n">%,d원</td><td class="n">%,d원</td><td></td>
					</tr>
					""".formatted(data.gross(), data.commission(), data.gross() - data.commission()));
		}

		StringBuilder refundRows = new StringBuilder();
		for (SettlementData.RefundLine r : data.refunds()) {
			refundRows.append("""
					<tr><td>%s</td><td>%s</td><td class="n">-%,d원</td></tr>
					""".formatted(esc(r.dateLabel()), esc(r.name()), r.amount()));
		}
		String refundBlock = data.refunds().isEmpty() ? "" : """
				<h2>환불 내역</h2>
				<table>
					<tr><th>환불일</th><th>상품명</th><th>환불액</th></tr>
					%s
				</table>
				""".formatted(refundRows);

		String summary = ("총 결제 <b>%,d원</b> · 환불 %,d원 · 순 결제액 %,d원 "
				+ "· 플랫폼 수수료(%d%%) %,d원 · 정산 예정액 <b>%,d원</b>").formatted(
				data.gross(), data.refund(), data.net(),
				data.commissionRatePercent(), data.commission(), data.payout());

		return """
				<html>
				<head><style>
					body { font-family: '%s', sans-serif; padding: 24px; color: #1f2937; }
					h1 { font-size: 18px; margin: 0; }
					h2 { font-size: 13px; margin: 20px 0 6px; }
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
					<h1>%s 매장 정산서</h1>
					<p class="sub">%s</p>
					<p class="summary">%s</p>
					<table>
						<tr><th>결제일</th><th>상품명</th><th>결제액</th><th>수수료</th><th>정산액</th><th>상태</th></tr>
						%s
					</table>
					%s
				</body>
				</html>
				""".formatted(FONT_FAMILY, esc(data.storeName()), esc(data.periodLabel()), summary, rows, refundBlock);
	}

	private static String esc(String s) {
		if (s == null) {
			return "";
		}
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}

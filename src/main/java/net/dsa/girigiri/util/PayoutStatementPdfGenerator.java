package net.dsa.girigiri.util;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import net.dsa.girigiri.domain.dto.PayoutStatementDto;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;

/**
 * 추가됨 (2026-10-07) — 점주용 "지급 명세서" PDF. 화면(settlementView/payoutStatement.html)과 같은
 * PayoutStatementDto를 그대로 찍는다. openhtmltopdf로 HTML → PDF(SettlementPdfGenerator와 같은 패턴).
 *
 * ⚠️ openhtmltopdf는 HTML을 XML로 파싱한다 — &nbsp; 같은 명명 엔티티는 파싱 실패. 리터럴만 쓸 것.
 */
public final class PayoutStatementPdfGenerator {

	private static final String FONT_FAMILY = "UnDotum";
	private static final String FONT_RESOURCE_PATH = "/fonts/UnDotum.ttf";
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
	private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

	private PayoutStatementPdfGenerator() {
	}

	public static byte[] generate(PayoutStatementDto s) throws IOException {
		String html = buildHtml(s);
		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			PdfRendererBuilder builder = new PdfRendererBuilder();
			builder.useFastMode();
			builder.useFont(() -> PayoutStatementPdfGenerator.class.getResourceAsStream(FONT_RESOURCE_PATH), FONT_FAMILY);
			builder.withHtmlContent(html, null);
			builder.toStream(out);
			builder.run();
			return out.toByteArray();
		} catch (IOException e) {
			throw e;
		} catch (Exception e) {
			throw new IOException("지급 명세서 PDF 생성에 실패했습니다.", e);
		}
	}

	private static String buildHtml(PayoutStatementDto s) {
		String carriedRow = s.carriedIn() > 0
				? row("이전 이월분 합산", "+" + won(s.carriedIn()))
				: "";
		String account = nz(s.bankName()) + " " + nz(s.maskedAccount()) + " (예금주 " + nz(s.accountHolder()) + ")";

		return """
				<html>
				<head><style>
					body { font-family: '%s', sans-serif; padding: 28px; color: #1f2937; }
					h1 { font-size: 20px; margin: 0 0 4px; }
					.sub { color: #6b7280; font-size: 11px; margin: 0 0 18px; }
					h2 { font-size: 13px; margin: 20px 0 6px; }
					table { width: 100%%; border-collapse: collapse; font-size: 12px; }
					th, td { border: 1px solid #e5e7eb; padding: 7px 9px; text-align: left; }
					th { background: #f3f4f6; width: 32%%; font-weight: normal; color: #4b5563; }
					td.n { text-align: right; }
					tr.total td, tr.total th { background: #ecfdf5; font-weight: bold; font-size: 13px; }
					.foot { margin-top: 24px; font-size: 10px; color: #6b7280; }
				</style></head>
				<body>
					<h1>지급 명세서</h1>
					<p class="sub">명세서 번호 %s · 발행일 %s · 발행 girigiri</p>

					<h2>지급 정보</h2>
					<table>
						<tr><th>매장명</th><td>%s</td></tr>
						<tr><th>사업자등록번호</th><td>%s</td></tr>
						<tr><th>정산 기간</th><td>%s ~ %s</td></tr>
						<tr><th>지급 예정일</th><td>%s</td></tr>
						<tr><th>지급 완료 일시</th><td>%s</td></tr>
						<tr><th>입금 계좌</th><td>%s</td></tr>
					</table>

					<h2>지급 금액 내역</h2>
					<table>
						%s
						%s
						%s
						%s
						%s
						<tr class="total"><th>지급액</th><td class="n">%s</td></tr>
					</table>

					<p class="foot">
						이 명세서는 girigiri가 정산 확정 기록을 바탕으로 발행한 지급 내역입니다.
						금액은 정산 확정 시점 기준이며, 문의는 앱 내 1:1 문의를 이용해 주세요.
					</p>
				</body>
				</html>
				""".formatted(
				FONT_FAMILY,
				esc(s.statementNo()), s.issuedDate().format(DATE),
				esc(s.storeName()),
				esc(s.businessNumber() == null ? "-" : s.businessNumber()),
				s.periodStart().format(DATE), s.periodEnd().format(DATE),
				s.scheduledPayoutDate().format(DATE),
				s.paidAt() == null ? "-" : s.paidAt().format(DATE_TIME),
				esc(account),
				row("총 결제액", won(s.gross())),
				row("환불", (s.refund() > 0 ? "-" : "") + won(s.refund())),
				row("순 결제액", won(s.netAmount())),
				row("플랫폼 수수료 (" + s.commissionRate() + "%)", (s.commission() > 0 ? "-" : "") + won(s.commission())),
				carriedRow,
				won(s.payout()));
	}

	private static String row(String label, String value) {
		return "<tr><th>" + esc(label) + "</th><td class=\"n\">" + esc(value) + "</td></tr>";
	}

	private static String won(long amount) {
		return "%,d원".formatted(amount);
	}

	private static String nz(String s) {
		return s == null ? "-" : s;
	}

	private static String esc(String s) {
		if (s == null) {
			return "";
		}
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}

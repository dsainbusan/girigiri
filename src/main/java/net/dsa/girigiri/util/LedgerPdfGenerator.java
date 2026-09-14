package net.dsa.girigiri.util;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import net.dsa.girigiri.domain.dto.LedgerData;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * 절약 가계부 내보내기 — PDF (WBS 6.0, 문창호). LedgerExcelGenerator와 데이터 소스(LedgerData)는
 * 동일, 출력 포맷만 PDF. openhtmltopdf로 HTML 문자열 → PDF 렌더링(다른 *PdfGenerator와 같은 패턴).
 *
 * ⚠️ openhtmltopdf는 HTML을 XML로 파싱한다 — &nbsp; 같은 명명 엔티티는 파싱 실패, "CO₂"의 아래첨자도
 * 폰트에 따라 깨질 수 있어 "CO2"로 표기한다(다른 PDF 생성기와 동일한 제약).
 */
public final class LedgerPdfGenerator {

	private static final String FONT_FAMILY = "UnDotum";
	private static final String FONT_RESOURCE_PATH = "/fonts/UnDotum.ttf";

	private LedgerPdfGenerator() {
	}

	public static byte[] generate(LedgerData data) throws IOException {
		String html = buildHtml(data);
		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			PdfRendererBuilder builder = new PdfRendererBuilder();
			builder.useFastMode();
			builder.useFont(() -> LedgerPdfGenerator.class.getResourceAsStream(FONT_RESOURCE_PATH), FONT_FAMILY);
			builder.withHtmlContent(html, null);
			builder.toStream(out);
			builder.run();
			return out.toByteArray();
		} catch (IOException e) {
			throw e;
		} catch (Exception e) {
			throw new IOException("절약 가계부 PDF 생성에 실패했습니다.", e);
		}
	}

	private static String buildHtml(LedgerData data) {
		StringBuilder rows = new StringBuilder();
		for (LedgerData.HistoryRow h : data.history()) {
			rows.append("""
					<tr>
						<td>%s</td><td>%s</td><td>%s</td><td class="n">%d개</td>
						<td class="n">%,d원</td><td class="n">%,d원</td><td class="n">%,d원</td>
					</tr>
					""".formatted(esc(h.dateLabel()), esc(h.storeName()), esc(h.productName()),
					h.quantity(), h.originalTotal(), h.paidTotal(), h.saved()));
		}
		if (data.history().isEmpty()) {
			rows.append("<tr><td colspan=\"7\">아직 구제 내역이 없습니다.</td></tr>");
		} else {
			rows.append("""
					<tr class="total">
						<td colspan="6">합계</td><td class="n">%,d원</td>
					</tr>
					""".formatted(data.totalSaved()));
		}

		StringBuilder catRows = new StringBuilder();
		for (LedgerData.CategoryRow c : data.categories()) {
			catRows.append("""
					<tr><td>%s</td><td class="n">%,d원</td><td class="n">%,d원</td><td class="n">%d%%</td></tr>
					""".formatted(esc(c.category()), c.spent(), c.saved(), c.percent()));
		}
		String categoryBlock = data.categories().isEmpty() ? "" : """
				<h2>카테고리별 소비·절약</h2>
				<table>
					<tr><th>카테고리</th><th>소비액</th><th>절약액</th><th>절약 비중</th></tr>
					%s
				</table>
				""".formatted(catRows);

		String goalLine = data.goalAmount() != null
				? ("이번 달 목표 <b>%,d원</b> 중 <b>%d%%</b> 달성").formatted(data.goalAmount(), data.goalPercent())
				: "이번 달 절약 목표가 설정돼 있지 않습니다.";

		String deltaLine = data.deltaPercent() != null
				? ("전월 대비 %s%,d원 (%s%d%%)").formatted(data.deltaAmount() >= 0 ? "+" : "-",
						Math.abs(data.deltaAmount()), data.deltaPercent() >= 0 ? "+" : "", data.deltaPercent())
				: "전월 비교 데이터 없음";

		return """
				<html>
				<head><style>
					body { font-family: '%s', sans-serif; padding: 24px; color: #1f2937; }
					h1 { font-size: 18px; margin: 0; display: inline-block; }
					h2 { font-size: 13px; margin: 20px 0 6px; }
					.tier { display: inline-block; margin-left: 10px; padding: 3px 10px; border-radius: 999px;
					        background: #dcfce7; color: #166534; font-size: 12px; font-weight: bold; }
					.sub { color: #6b7280; font-size: 12px; margin: 6px 0 16px; }
					.stat-row { width: 100%%; border-collapse: collapse; margin-bottom: 14px; }
					.stat-row td { width: 25%%; border: 1px solid #e5e7eb; padding: 8px 10px; text-align: center; }
					.stat-row .label { display: block; font-size: 10px; color: #6b7280; margin-bottom: 3px; }
					.stat-row .value { display: block; font-size: 15px; font-weight: bold; color: #15803d; }
					.goal { font-size: 11px; color: #16a34a; margin-bottom: 12px; }
					table { width: 100%%; border-collapse: collapse; font-size: 11px; }
					th, td { border: 1px solid #e5e7eb; padding: 5px 7px; text-align: left; }
					th { background: #f3f4f6; }
					td.n { text-align: right; }
					tr.total td { background: #f9fafb; font-weight: bold; }
				</style></head>
				<body>
					<h1>%s님의 절약 가계부</h1><span class="tier">%s 등급</span>
					<p class="sub">기리기리 · 발급일 %s · %s</p>
					<table class="stat-row">
						<tr>
							<td><span class="label">이번 달 절약</span><span class="value">%,d원</span></td>
							<td><span class="label">누적 절약</span><span class="value">%,d원</span></td>
							<td><span class="label">절약률</span><span class="value">%d%%</span></td>
							<td><span class="label">CO2 절감</span><span class="value">%.1fkg</span></td>
						</tr>
					</table>
					<p class="goal">%s</p>
					<h2>구매·절약 내역</h2>
					<table>
						<tr><th>날짜</th><th>매장</th><th>상품</th><th>수량</th><th>정상가 합</th><th>결제액</th><th>절약액</th></tr>
						%s
					</table>
					%s
				</body>
				</html>
				""".formatted(FONT_FAMILY, esc(data.nickname() != null ? data.nickname() : "회원"),
				esc(data.tier()), java.time.LocalDate.now(), deltaLine,
				data.thisMonthSaved(), data.totalSaved(), data.rescueRatePercent(), data.co2Kg(),
				goalLine, rows, categoryBlock);
	}

	private static String esc(String s) {
		if (s == null) {
			return "";
		}
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}

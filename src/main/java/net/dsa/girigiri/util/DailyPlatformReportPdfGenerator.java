package net.dsa.girigiri.util;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import net.dsa.girigiri.domain.dto.DailyPlatformStatsDto;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * 추가됨 (2026-10-05) — 슈퍼어드민 통계 대시보드 달력에서 뽑는 "일별 플랫폼 현황" PDF.
 * SalesReportPdfGenerator(문창호, 점주용)와 생성 방식은 같지만(openhtmltopdf), 점주 화면의
 * cream/navy 팔레트 대신 슈퍼어드민 화면 자체 톤(layout-admin.css의 blue/gray 토큰)을 그대로
 * 하드코딩했다 — superAdminView는 유저·매장과 디자인 시스템을 섞지 않는다(girigiri-dev 스킬 참고).
 *
 * ⚠️ openhtmltopdf는 HTML을 XML로 파싱한다 — 명명 엔티티(&nbsp; 등) 금지, 리터럴만 쓸 것.
 */
public final class DailyPlatformReportPdfGenerator {

	private static final String FONT_FAMILY = "UnDotum";
	private static final String FONT_RESOURCE_PATH = "/fonts/UnDotum.ttf";

	private DailyPlatformReportPdfGenerator() {
	}

	public static byte[] generate(DailyPlatformStatsDto data) throws IOException {
		String html = buildHtml(data);
		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			PdfRendererBuilder builder = new PdfRendererBuilder();
			builder.useFastMode();
			builder.useFont(() -> DailyPlatformReportPdfGenerator.class.getResourceAsStream(FONT_RESOURCE_PATH), FONT_FAMILY);
			builder.withHtmlContent(html, null);
			builder.toStream(out);
			builder.run();
			return out.toByteArray();
		} catch (IOException e) {
			throw e;
		} catch (Exception e) {
			throw new IOException("일별 플랫폼 현황 PDF 생성에 실패했습니다.", e);
		}
	}

	private static String buildHtml(DailyPlatformStatsDto data) {
		return """
				<html>
				<head><style>
					@page { size: A4; margin: 30px 34px 34px; }
					body { font-family: '%s', sans-serif; color: #1a1c1f; font-size: 11px; }

					.head { border-top: 4px solid #2f6fe0; padding-top: 12px; }
					.head h1 { font-size: 21px; font-weight: bold; margin: 0; letter-spacing: -0.02em; color: #2f6fe0; }
					.head .date { font-size: 12px; font-weight: bold; margin-top: 3px; color: #33363b; }
					.metabar { width: 100%%; margin-top: 10px; border-bottom: 2px solid #2f6fe0; padding-bottom: 8px; }
					.metabar td { font-size: 10.5px; color: #777b83; }
					.metabar .right { text-align: right; }

					.kpi { width: 100%%; border-collapse: separate; border-spacing: 7px 0; margin: 16px 0 6px; }
					.kpi td { width: 25%%; border: 1px solid #dfe2e7; background: #f2f4f7; padding: 9px 10px; vertical-align: top; }
					.kpi .label { display: block; font-size: 9.5px; color: #777b83; margin-bottom: 5px; }
					.kpi .value { display: block; font-size: 15px; font-weight: bold; color: #1a1c1f; }
					.kpi .value.accent { color: #2f6fe0; }
					.kpi .delta { display: block; font-size: 9px; font-weight: bold; margin-top: 3px; }
					.kpi .delta.up { color: #2f6fe0; }
					.kpi .delta.down { color: #f5424e; }

					.aux { font-size: 10px; color: #777b83; margin: 10px 2px 0; }

					.foot { margin-top: 18px; border-top: 1px solid #dfe2e7; padding-top: 8px; font-size: 9px; color: #777b83; }
				</style></head>
				<body>
					<div class="head">
						<h1>플랫폼 일별 현황 리포트</h1>
						<div class="date">%s</div>
						<table class="metabar"><tr>
							<td>대상 날짜: <b>%s</b></td>
							<td class="right">발행일 %s</td>
						</tr></table>
					</div>

					<table class="kpi"><tr>
						<td><span class="label">신규 회원</span><span class="value accent">%d명</span>%s</td>
						<td><span class="label">신규 입점 매장</span><span class="value accent">%d곳</span>%s</td>
						<td><span class="label">거래</span><span class="value">%d건</span>%s</td>
						<td><span class="label">거래액</span><span class="value">%,d원</span>%s</td>
					</tr></table>
					<table class="kpi"><tr>
						<td><span class="label">구해낸 음식</span><span class="value accent">%d개</span>%s</td>
						<td><span class="label">CO2 절감</span><span class="value">%.1fkg</span></td>
						<td><span class="label">취소</span><span class="value">%d건</span></td>
						<td><span class="label">노쇼</span><span class="value">%d건</span></td>
					</tr></table>
					<p class="aux">거래·거래액·구해낸 음식은 픽업 완료된 예약만 집계 · CO2 절감은 구제 수량 × 0.5kg 환산 · 증감률은 전월 같은 날짜 대비</p>

					<div class="foot">girigiri 슈퍼어드민 통계 대시보드</div>
				</body>
				</html>
				""".formatted(FONT_FAMILY,
				esc(data.dateLabel()), esc(data.date().toString()), esc(issuedDate()),
				data.newMemberCount(), deltaFragment(data.memberDeltaPercent()),
				data.newStoreCount(), deltaFragment(data.storeDeltaPercent()),
				data.transactionCount(), deltaFragment(data.transactionDeltaPercent()),
				data.revenue(), deltaFragment(data.revenueDeltaPercent()),
				data.rescuedQuantity(), deltaFragment(data.rescuedDeltaPercent()),
				data.co2Kg(), data.cancelledCount(), data.noshowedCount());
	}

	// 전월 같은 날짜 값이 0이라 비교 불가하면(null) 아무것도 안 그린다.
	private static String deltaFragment(Integer percent) {
		if (percent == null) {
			return "";
		}
		boolean up = percent >= 0;
		return "<span class=\"delta %s\">%s 전월 대비 %s%d%%</span>".formatted(
				up ? "up" : "down", up ? "▲" : "▼", up ? "+" : "", percent);
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

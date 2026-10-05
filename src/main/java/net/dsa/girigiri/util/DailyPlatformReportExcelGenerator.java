package net.dsa.girigiri.util;

import net.dsa.girigiri.domain.dto.DailyPlatformStatsDto;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * 추가됨 (2026-10-05) — 슈퍼어드민 통계 대시보드 달력에서 뽑는 "일별 플랫폼 현황" Excel.
 * SalesReportExcelGenerator(문창호, 점주용)와 같은 구조 — 데이터 소스만 DailyPlatformStatsDto.
 */
public final class DailyPlatformReportExcelGenerator {

	private static final String[] HEADERS = {"지표", "값"};
	private static final int[] WIDTHS = {20, 20};

	private DailyPlatformReportExcelGenerator() {
	}

	public static byte[] generate(DailyPlatformStatsDto data) throws IOException {
		try (Workbook wb = new XSSFWorkbook()) {
			Sheet sheet = wb.createSheet("일별 플랫폼 현황");
			CellStyle bold = boldStyle(wb);

			int r = 0;
			Row title = sheet.createRow(r++);
			title.createCell(0).setCellValue("플랫폼 일별 현황 리포트");
			title.getCell(0).setCellStyle(bold);
			sheet.createRow(r++).createCell(0).setCellValue(data.dateLabel());
			r++;

			Row header = sheet.createRow(r++);
			for (int i = 0; i < HEADERS.length; i++) {
				header.createCell(i).setCellValue(HEADERS[i]);
				header.getCell(i).setCellStyle(bold);
				sheet.setColumnWidth(i, WIDTHS[i] * 256);
			}

			r = row(sheet, r, "신규 회원", data.newMemberCount() + "명" + deltaSuffix(data.memberDeltaPercent()));
			r = row(sheet, r, "신규 입점 매장", data.newStoreCount() + "곳" + deltaSuffix(data.storeDeltaPercent()));
			r = row(sheet, r, "거래", data.transactionCount() + "건" + deltaSuffix(data.transactionDeltaPercent()));
			r = row(sheet, r, "거래액", String.format("%,d원", data.revenue()) + deltaSuffix(data.revenueDeltaPercent()));
			r = row(sheet, r, "구해낸 음식", data.rescuedQuantity() + "개" + deltaSuffix(data.rescuedDeltaPercent()));
			r = row(sheet, r, "CO2 절감", String.format("%.1fkg", data.co2Kg()));
			r = row(sheet, r, "취소", data.cancelledCount() + "건");
			row(sheet, r, "노쇼", data.noshowedCount() + "건");

			try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
				wb.write(out);
				return out.toByteArray();
			}
		}
	}

	// null이면(전월 같은 날짜 값이 0이라 비교 불가) 아무것도 안 붙인다.
	private static String deltaSuffix(Integer percent) {
		if (percent == null) {
			return "";
		}
		return " (전월 대비 " + (percent >= 0 ? "+" : "") + percent + "%)";
	}

	private static int row(Sheet sheet, int r, String label, String value) {
		Row row = sheet.createRow(r);
		row.createCell(0).setCellValue(label);
		row.createCell(1).setCellValue(value);
		return r + 1;
	}

	private static CellStyle boldStyle(Workbook wb) {
		Font f = wb.createFont();
		f.setBold(true);
		CellStyle s = wb.createCellStyle();
		s.setFont(f);
		return s;
	}
}

package net.dsa.girigiri.util;

import net.dsa.girigiri.domain.dto.SalesReportData;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * 매출 리포트 Excel 생성 (WBS 3.0, 문창호). PDF와 데이터 소스(SalesReportData) 동일, 포맷만 xlsx.
 * autoSizeColumn() 없이 setColumnWidth 고정폭만 (헤드리스 서버 폰트 메트릭 이슈, POI 알려진 문제).
 */
public final class SalesReportExcelGenerator {

	private static final String[] PRODUCT_HEADERS = {"상품명", "카테고리", "등록", "판매", "폐기", "매출", "구제율"};
	private static final int[] PRODUCT_WIDTHS = {22, 12, 8, 8, 8, 14, 8};
	private static final String[] DAY_HEADERS = {"날짜", "판매", "폐기", "매출"};
	private static final int[] DAY_WIDTHS = {12, 8, 8, 14};

	private SalesReportExcelGenerator() {
	}

	public static byte[] generate(SalesReportData data) throws IOException {
		try (Workbook wb = new XSSFWorkbook()) {
			Sheet sheet = wb.createSheet("매출 리포트");
			CellStyle bold = boldStyle(wb);

			int r = 0;
			cell(sheet.createRow(r++), 0, data.storeName() + " 매출 리포트", bold);
			cell(sheet.createRow(r++), 0, data.periodLabel(), null);
			cell(sheet.createRow(r++), 0, summaryLine(data), null);
			r++; // 빈 줄

			Row productHeader = sheet.createRow(r++);
			for (int i = 0; i < PRODUCT_HEADERS.length; i++) {
				cell(productHeader, i, PRODUCT_HEADERS[i], bold);
				sheet.setColumnWidth(i, PRODUCT_WIDTHS[i] * 256);
			}
			for (SalesReportData.ProductLine p : data.products()) {
				Row row = sheet.createRow(r++);
				row.createCell(0).setCellValue(p.name());
				row.createCell(1).setCellValue(p.category() == null ? "" : p.category());
				row.createCell(2).setCellValue(p.registeredQty());
				row.createCell(3).setCellValue(p.soldQty());
				row.createCell(4).setCellValue(p.wastedQty());
				row.createCell(5).setCellValue(p.amount());
				row.createCell(6).setCellValue(p.rescueRatePercent() + "%");
			}
			if (data.products().isEmpty()) {
				sheet.createRow(r++).createCell(0).setCellValue("이 기간에 매출 데이터가 없습니다.");
			} else {
				Row total = sheet.createRow(r++);
				cell(total, 0, "합계", bold);
				cell(total, 2, String.valueOf(data.registeredCount()), bold);
				cell(total, 3, String.valueOf(data.soldCount()), bold);
				cell(total, 4, String.valueOf(data.wastedCount()), bold);
				cell(total, 5, String.valueOf(data.totalSales()), bold);
				cell(total, 6, data.rescueRatePercent() + "%", bold);
			}

			if (data.showGraph() && !data.days().isEmpty()) {
				r++; // 빈 줄
				cell(sheet.createRow(r++), 0, "일별", bold);
				Row dayHeader = sheet.createRow(r++);
				for (int i = 0; i < DAY_HEADERS.length; i++) {
					cell(dayHeader, i, DAY_HEADERS[i], bold);
				}
				for (SalesReportData.DayLine d : data.days()) {
					Row row = sheet.createRow(r++);
					row.createCell(0).setCellValue(d.label());
					row.createCell(1).setCellValue(d.soldQty());
					row.createCell(2).setCellValue(d.wastedQty());
					row.createCell(3).setCellValue(d.amount());
				}
			}

			try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
				wb.write(out);
				return out.toByteArray();
			}
		}
	}

	private static String summaryLine(SalesReportData d) {
		String s = "회수 매출 " + String.format("%,d", d.totalSales()) + "원  ·  판매 " + d.soldCount()
				+ "개  ·  구제율 " + d.rescueRatePercent() + "%  ·  폐기 " + d.wastedCount()
				+ "개  ·  CO2 절감 " + d.co2Kg() + "kg  ·  할인 제공 " + String.format("%,d", d.discountGiven()) + "원";
		if (d.prevTotalSales() != null && d.deltaPercent() != null) {
			s += "  ·  전주 " + String.format("%,d", d.prevTotalSales()) + "원 대비 "
					+ (d.deltaPercent() >= 0 ? "+" : "") + d.deltaPercent() + "%";
		}
		return s;
	}

	private static void cell(Row row, int col, String value, CellStyle style) {
		Cell c = row.createCell(col);
		c.setCellValue(value);
		if (style != null) {
			c.setCellStyle(style);
		}
	}

	private static CellStyle boldStyle(Workbook wb) {
		Font f = wb.createFont();
		f.setBold(true);
		CellStyle s = wb.createCellStyle();
		s.setFont(f);
		return s;
	}
}

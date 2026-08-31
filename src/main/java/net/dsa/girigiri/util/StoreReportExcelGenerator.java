package net.dsa.girigiri.util;

import net.dsa.girigiri.domain.dto.StoreReportData;
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
 * 판매·폐기 리포트 Excel 생성 (WBS 3.0, 문창호). PDF와 데이터 소스(StoreReportData) 동일, 포맷만 xlsx.
 * 일간=상품별 표, 주간=판매 내역(예약) 표.
 *
 * autoSizeColumn()은 헤드리스 서버에서 폰트 메트릭 문제로 실패할 수 있어(POI 알려진 이슈) setColumnWidth 고정폭만.
 */
public final class StoreReportExcelGenerator {

	private static final String[] DAILY_HEADERS = {"상품명", "원가", "할인가", "할인율", "등록", "판매", "폐기", "회수 매출", "할인액"};
	private static final int[] DAILY_WIDTHS = {20, 10, 10, 8, 8, 8, 8, 12, 12};
	private static final String[] WEEKLY_HEADERS = {"픽업일", "상품명", "수량", "결제 금액", "상태"};
	private static final int[] WEEKLY_WIDTHS = {10, 22, 8, 14, 12};

	private StoreReportExcelGenerator() {
	}

	public static byte[] generate(StoreReportData data) throws IOException {
		try (Workbook wb = new XSSFWorkbook()) {
			Sheet sheet = wb.createSheet("판매·폐기 리포트");
			CellStyle bold = boldStyle(wb);
			StoreReportData.Totals t = data.totals();

			int r = 0;
			cell(sheet.createRow(r++), 0, data.storeName() + " 판매·폐기 리포트", bold);
			cell(sheet.createRow(r++), 0, data.periodLabel(), null);
			cell(sheet.createRow(r++), 0, summaryLine(data), null);
			r++; // 빈 줄

			if (data.weekly()) {
				Row header = sheet.createRow(r++);
				for (int i = 0; i < WEEKLY_HEADERS.length; i++) {
					cell(header, i, WEEKLY_HEADERS[i], bold);
					sheet.setColumnWidth(i, WEEKLY_WIDTHS[i] * 256);
				}
				for (StoreReportData.Line l : data.lines()) {
					Row row = sheet.createRow(r++);
					row.createCell(0).setCellValue(l.dateLabel());
					row.createCell(1).setCellValue(l.name());
					row.createCell(2).setCellValue(l.sold());
					row.createCell(3).setCellValue(l.sales());
					row.createCell(4).setCellValue(l.status());
				}
				if (data.lines().isEmpty()) {
					sheet.createRow(r).createCell(0).setCellValue("최근 7일간 판매 내역이 없습니다.");
				} else {
					Row total = sheet.createRow(r);
					cell(total, 0, "합계", bold);
					cell(total, 2, String.valueOf(t.sold()), bold);
					cell(total, 3, String.valueOf(t.sales()), bold);
				}
			} else {
				Row header = sheet.createRow(r++);
				for (int i = 0; i < DAILY_HEADERS.length; i++) {
					cell(header, i, DAILY_HEADERS[i], bold);
					sheet.setColumnWidth(i, DAILY_WIDTHS[i] * 256);
				}
				for (StoreReportData.Line l : data.lines()) {
					Row row = sheet.createRow(r++);
					row.createCell(0).setCellValue(l.name());
					row.createCell(1).setCellValue(l.originalPrice());
					row.createCell(2).setCellValue(l.discountedPrice());
					row.createCell(3).setCellValue(l.discountRate() + "%");
					row.createCell(4).setCellValue(l.registered());
					row.createCell(5).setCellValue(l.sold());
					row.createCell(6).setCellValue(l.wasted());
					row.createCell(7).setCellValue(l.sales());
					row.createCell(8).setCellValue(l.discountGiven());
				}
				if (data.lines().isEmpty()) {
					sheet.createRow(r).createCell(0).setCellValue("오늘 등록된 상품이 없습니다.");
				} else {
					Row total = sheet.createRow(r);
					cell(total, 0, "합계", bold);
					cell(total, 4, String.valueOf(t.registered()), bold);
					cell(total, 5, String.valueOf(t.sold()), bold);
					cell(total, 6, String.valueOf(t.wasted()), bold);
					cell(total, 7, String.valueOf(t.sales()), bold);
					cell(total, 8, String.valueOf(t.discountGiven()), bold);
				}
			}

			try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
				wb.write(out);
				return out.toByteArray();
			}
		}
	}

	private static String summaryLine(StoreReportData data) {
		StoreReportData.Totals t = data.totals();
		if (data.weekly()) {
			return "구제율 " + t.rescueRate() + "%  ·  판매 " + t.sold() + "  ·  폐기 " + t.wasted()
					+ "  ·  등록 " + t.registered() + "  ·  회수 매출 " + t.sales() + "원  ·  CO2 절감 " + t.co2Kg() + "kg";
		}
		return "구제율 " + t.rescueRate() + "%  ·  등록 " + t.registered() + "  ·  판매 " + t.sold()
				+ "  ·  폐기 " + t.wasted() + "  ·  회수 매출 " + t.sales() + "원  ·  할인 제공 "
				+ t.discountGiven() + "원  ·  CO2 절감 " + t.co2Kg() + "kg";
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

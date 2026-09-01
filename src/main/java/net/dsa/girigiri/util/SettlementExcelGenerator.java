package net.dsa.girigiri.util;

import net.dsa.girigiri.domain.dto.SettlementData;
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
 * 매장 정산 Excel 생성 (WBS 2.0, 문창호). PDF와 데이터 소스(SettlementData) 동일, 포맷만 xlsx.
 *
 * autoSizeColumn()은 헤드리스 서버에서 폰트 메트릭 문제로 실패할 수 있어(POI 알려진 이슈) 고정폭만.
 */
public final class SettlementExcelGenerator {

	private static final String[] HEADERS = {"결제일", "상품명", "결제액", "수수료", "정산액", "상태"};
	private static final int[] WIDTHS = {12, 24, 14, 12, 14, 16};
	private static final String[] REFUND_HEADERS = {"환불일", "상품명", "환불액"};
	private static final int[] REFUND_WIDTHS = {12, 24, 14};

	private SettlementExcelGenerator() {
	}

	public static byte[] generate(SettlementData data) throws IOException {
		try (Workbook wb = new XSSFWorkbook()) {
			Sheet sheet = wb.createSheet("매장 정산");
			CellStyle bold = boldStyle(wb);

			int r = 0;
			cell(sheet.createRow(r++), 0, data.storeName() + " 매장 정산서", bold);
			cell(sheet.createRow(r++), 0, data.periodLabel(), null);
			cell(sheet.createRow(r++), 0, summaryLine(data), null);
			r++; // 빈 줄

			Row header = sheet.createRow(r++);
			for (int i = 0; i < HEADERS.length; i++) {
				cell(header, i, HEADERS[i], bold);
				sheet.setColumnWidth(i, WIDTHS[i] * 256);
			}
			for (SettlementData.Line l : data.lines()) {
				Row row = sheet.createRow(r++);
				row.createCell(0).setCellValue(l.dateLabel());
				row.createCell(1).setCellValue(l.name());
				row.createCell(2).setCellValue(l.amount());
				row.createCell(3).setCellValue(l.commission());
				row.createCell(4).setCellValue(l.payout());
				row.createCell(5).setCellValue(l.status());
			}
			if (data.lines().isEmpty()) {
				sheet.createRow(r++).createCell(0).setCellValue("이 기간에 정산 대상 결제가 없습니다.");
			} else {
				Row total = sheet.createRow(r++);
				cell(total, 0, "합계", bold);
				cell(total, 2, String.valueOf(data.gross()), bold);
				cell(total, 3, String.valueOf(data.commission()), bold);
				cell(total, 4, String.valueOf(data.gross() - data.commission()), bold);
			}

			if (!data.refunds().isEmpty()) {
				r++; // 빈 줄
				cell(sheet.createRow(r++), 0, "환불 내역", bold);
				Row rHeader = sheet.createRow(r++);
				for (int i = 0; i < REFUND_HEADERS.length; i++) {
					cell(rHeader, i, REFUND_HEADERS[i], bold);
					sheet.setColumnWidth(i, Math.max(WIDTHS[i], REFUND_WIDTHS[i]) * 256);
				}
				for (SettlementData.RefundLine rl : data.refunds()) {
					Row row = sheet.createRow(r++);
					row.createCell(0).setCellValue(rl.dateLabel());
					row.createCell(1).setCellValue(rl.name());
					row.createCell(2).setCellValue(-rl.amount());
				}
			}

			try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
				wb.write(out);
				return out.toByteArray();
			}
		}
	}

	private static String summaryLine(SettlementData d) {
		return "총 결제 " + d.gross() + "원  ·  환불 " + d.refund() + "원  ·  순 결제액 " + d.net()
				+ "원  ·  플랫폼 수수료(" + d.commissionRatePercent() + "%) " + d.commission()
				+ "원  ·  정산 예정액 " + d.payout() + "원";
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

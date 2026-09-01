package net.dsa.girigiri.util;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * 정산 지급용 은행 대량이체 이체 목록 Excel (문창호, 2026-09-01).
 * 이 파일을 은행 기업뱅킹의 대량이체에 업로드해서 한 번에 송금한다.
 * 실제 은행 양식은 은행마다 달라서, 여기선 "은행 / 계좌번호 / 예금주 / 금액 / 적요" 표준 컬럼만 낸다.
 *
 * ⚠️ 호출부(다운로드 컨트롤러)는 슈퍼어드민(송보미) 영역이라 아직 없다. 송보미가 /superadmin 정산
 *    화면에서 SettlementRepository.findByStatusOrderByScheduledPayoutDateAsc("PENDING") + StoreEntity.bank*
 *    로 Line 리스트를 만들어 generate()에 넘기면 된다.
 */
public final class SettlementTransferExcelGenerator {

	private static final String[] HEADERS = {"은행", "계좌번호", "예금주", "금액", "적요", "매장", "정산주간"};
	private static final int[] WIDTHS = {12, 24, 16, 14, 24, 22, 24};

	/** 이체 1건. bank* 필드가 비면 Excel에 "미등록"으로 채워 눈에 띄게 한다. */
	public record Line(String bankName, String bankAccount, String accountHolder,
	                   long amount, String memo, String storeName, String periodLabel) {}

	private SettlementTransferExcelGenerator() {
	}

	public static byte[] generate(List<Line> lines) throws IOException {
		try (Workbook wb = new XSSFWorkbook()) {
			Sheet sheet = wb.createSheet("이체 목록");
			CellStyle bold = boldStyle(wb);

			Row header = sheet.createRow(0);
			for (int i = 0; i < HEADERS.length; i++) {
				cell(header, i, HEADERS[i], bold);
				sheet.setColumnWidth(i, WIDTHS[i] * 256);
			}

			int r = 1;
			long total = 0;
			for (Line line : lines) {
				Row x = sheet.createRow(r++);
				x.createCell(0).setCellValue(blankToMark(line.bankName()));
				x.createCell(1).setCellValue(blankToMark(line.bankAccount()));
				x.createCell(2).setCellValue(blankToMark(line.accountHolder()));
				x.createCell(3).setCellValue(line.amount());
				x.createCell(4).setCellValue(line.memo() == null ? "" : line.memo());
				x.createCell(5).setCellValue(line.storeName() == null ? "" : line.storeName());
				x.createCell(6).setCellValue(line.periodLabel() == null ? "" : line.periodLabel());
				total += line.amount();
			}

			Row totalRow = sheet.createRow(r);
			cell(totalRow, 2, "합계", bold);
			cell(totalRow, 3, String.valueOf(total), bold);

			try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
				wb.write(out);
				return out.toByteArray();
			}
		}
	}

	private static String blankToMark(String s) {
		return (s == null || s.isBlank()) ? "미등록" : s.trim();
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

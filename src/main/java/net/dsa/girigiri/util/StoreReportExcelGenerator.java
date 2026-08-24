package net.dsa.girigiri.util;

import net.dsa.girigiri.domain.entity.ProductEntity;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

/**
 * 추가됨 (2026-08-21) — 왜: 일간 판매·폐기 리포트 Excel 생성 (WBS 3.0, 문창호 담당).
 * 영수증 PDF(ReceiptPdfGenerator, openhtmltopdf 기반)와 짝을 이루는 Excel 버전.
 *
 * autoSizeColumn()은 헤드리스 서버 환경에서 폰트 메트릭 문제로 실패할 수 있어(POI의 알려진 이슈)
 * 일부러 안 쓰고, setColumnWidth로 고정폭만 지정한다 — AWT 폰트 렌더링에 안 기대는 게 더 안전하다.
 */
public final class StoreReportExcelGenerator {

	private static final String[] HEADERS = {"상품명", "원가", "할인가", "할인율", "등록수량", "판매수량", "상태"};
	private static final int[] COLUMN_WIDTHS_CHARS = {20, 10, 10, 8, 10, 10, 10};

	private StoreReportExcelGenerator() {
	}

	public static byte[] generate(String storeName, List<ProductEntity> todayProducts) throws IOException {
		try (Workbook workbook = new XSSFWorkbook()) {
			Sheet sheet = workbook.createSheet("오늘의 리포트");

			CellStyle boldStyle = boldStyle(workbook);

			int rowIdx = 0;
			Row titleRow = sheet.createRow(rowIdx++);
			Cell titleCell = titleRow.createCell(0);
			titleCell.setCellValue(storeName + " 판매·폐기 리포트 (" + LocalDate.now() + ")");
			titleCell.setCellStyle(boldStyle);

			rowIdx++; // 빈 줄

			Row headerRow = sheet.createRow(rowIdx++);
			for (int i = 0; i < HEADERS.length; i++) {
				Cell cell = headerRow.createCell(i);
				cell.setCellValue(HEADERS[i]);
				cell.setCellStyle(boldStyle);
				sheet.setColumnWidth(i, COLUMN_WIDTHS_CHARS[i] * 256);
			}

			for (ProductEntity product : todayProducts) {
				Row row = sheet.createRow(rowIdx++);
				int soldQuantity = product.getQuantity() - product.getRemainingQuantity();
				int discountRate = product.getOriginalPrice() == 0 ? 0
						: (int) Math.round(100.0 * (product.getOriginalPrice() - product.getDiscountedPrice()) / product.getOriginalPrice());

				row.createCell(0).setCellValue(product.getName());
				row.createCell(1).setCellValue(product.getOriginalPrice());
				row.createCell(2).setCellValue(product.getDiscountedPrice());
				row.createCell(3).setCellValue(discountRate + "%");
				row.createCell(4).setCellValue(product.getQuantity());
				row.createCell(5).setCellValue(soldQuantity);
				row.createCell(6).setCellValue(product.getStatus());
			}

			if (todayProducts.isEmpty()) {
				sheet.createRow(rowIdx).createCell(0).setCellValue("오늘 등록된 상품이 없습니다.");
			}

			try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
				workbook.write(out);
				return out.toByteArray();
			}
		}
	}

	private static CellStyle boldStyle(Workbook workbook) {
		Font boldFont = workbook.createFont();
		boldFont.setBold(true);
		CellStyle style = workbook.createCellStyle();
		style.setFont(boldFont);
		return style;
	}
}

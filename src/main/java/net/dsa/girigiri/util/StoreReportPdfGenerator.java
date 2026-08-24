package net.dsa.girigiri.util;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import net.dsa.girigiri.domain.entity.ProductEntity;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

/**
 * 추가됨 — 왜: 일간 판매·폐기 리포트 PDF 생성 (WBS 3.0, 문창호 담당). StoreReportExcelGenerator와
 * 데이터 소스(오늘 등록된 상품 목록)는 완전히 동일하고, 출력 포맷만 PDF로 다르다.
 * ReceiptPdfGenerator(openhtmltopdf 기반)와 같은 패턴 — HTML 문자열을 만들어서 그대로 PDF로 렌더링한다.
 */
public final class StoreReportPdfGenerator {

	private static final String[] HEADERS = {"상품명", "원가", "할인가", "할인율", "등록수량", "판매수량", "상태"};

	// ReceiptPdfGenerator와 동일한 한글 폰트 서브셋 재사용 — 왜: openhtmltopdf 기본 폰트엔 한글
	// 글리프가 없어서 매장명/상품명이 "#"로 깨지는 문제가 있었고, 이미 팀에서 검증된 폰트(서브셋 1.7MB)가
	// 있는데 별도로 폰트를 또 등록하면 리소스만 중복된다.
	private static final String FONT_FAMILY = "NotoSansKR";
	private static final String FONT_RESOURCE_PATH = "/fonts/NotoSansKR-Regular.ttf";

	private StoreReportPdfGenerator() {
	}

	public static byte[] generate(String storeName, List<ProductEntity> todayProducts) throws IOException {
		String html = buildHtml(storeName, todayProducts);

		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			PdfRendererBuilder builder = new PdfRendererBuilder();
			builder.useFastMode();
			builder.useFont(() -> StoreReportPdfGenerator.class.getResourceAsStream(FONT_RESOURCE_PATH), FONT_FAMILY);
			builder.withHtmlContent(html, null);
			builder.toStream(out);
			builder.run();   // openhtmltopdf가 내부적으로 checked Exception을 던지므로 아래에서 감싼다
			return out.toByteArray();
		} catch (IOException e) {
			throw e;
		} catch (Exception e) {
			throw new IOException("판매·폐기 리포트 PDF 생성에 실패했습니다.", e);
		}
	}

	private static String buildHtml(String storeName, List<ProductEntity> todayProducts) {
		StringBuilder rows = new StringBuilder();
		for (ProductEntity product : todayProducts) {
			int soldQuantity = product.getQuantity() - product.getRemainingQuantity();
			int discountRate = product.getOriginalPrice() == 0 ? 0
					: (int) Math.round(100.0 * (product.getOriginalPrice() - product.getDiscountedPrice()) / product.getOriginalPrice());

			rows.append("""
					<tr>
						<td>%s</td><td>%,d원</td><td>%,d원</td><td>%d%%</td><td>%d</td><td>%d</td><td>%s</td>
					</tr>
					""".formatted(
					product.getName(), product.getOriginalPrice(), product.getDiscountedPrice(),
					discountRate, product.getQuantity(), soldQuantity, product.getStatus()));
		}

		if (todayProducts.isEmpty()) {
			rows.append("<tr><td colspan=\"7\">오늘 등록된 상품이 없습니다.</td></tr>");
		}

		String headerCells = "";
		for (String header : HEADERS) {
			headerCells += "<th>" + header + "</th>";
		}

		return """
				<html>
				<head><style>
					body { font-family: '%s', sans-serif; padding: 24px; }
					h1 { font-size: 18px; }
					table { width: 100%%; border-collapse: collapse; margin-top: 16px; font-size: 12px; }
					th, td { border: 1px solid #ddd; padding: 6px 8px; text-align: left; }
					th { background: #f5f5f5; }
				</style></head>
				<body>
					<h1>%s 판매·폐기 리포트 (%s)</h1>
					<table>
						<tr>%s</tr>
						%s
					</table>
				</body>
				</html>
				""".formatted(FONT_FAMILY, storeName, LocalDate.now(), headerCells, rows);
	}
}

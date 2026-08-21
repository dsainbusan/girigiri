package net.dsa.girigiri.util;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * 결제 영수증 PDF 생성 유틸.
 * DB/로그인 없이 필요한 값(ReceiptData)만 넘기면 PDF 바이트를 돌려주므로 독립적으로 테스트 가능하다.
 * QR 코드는 QrCodeUtil로 만들어서 PDF 안에 이미지로 같이 넣는다.
 */
public class ReceiptPdfGenerator {

	private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

	private ReceiptPdfGenerator() {
	}

	public static byte[] generate(ReceiptData data) throws IOException {
		String qrBase64 = "";
		if (data.includeQr()) {
			try {
				byte[] qrBytes = QrCodeUtil.generateQrImage(data.pickupCode(), 200);
				qrBase64 = Base64.getEncoder().encodeToString(qrBytes);
			} catch (Exception e) {
				// QR 생성에 실패해도 영수증 자체는 만들어지도록 무시 (QR 없이 발급)
			}
		}

		String html = buildHtml(data, qrBase64);

		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			PdfRendererBuilder builder = new PdfRendererBuilder();
			builder.useFastMode();
			builder.withHtmlContent(html, null);
			builder.toStream(out);
			builder.run();   // openhtmltopdf가 내부적으로 checked Exception을 던지므로 아래에서 감싼다
			return out.toByteArray();
		} catch (IOException e) {
			throw e;
		} catch (Exception e) {
			throw new IOException("영수증 PDF 생성에 실패했습니다.", e);
		}
	}

	private static String buildHtml(ReceiptData d, String qrBase64) {
		// 취소/노쇼처럼 더 이상 픽업이 일어날 일이 없는 예약은 QR 이미지 자체를 아예 안 넣는다
		// (있어봤자 쓸 데 없는 QR이라 괜히 "아직 쓸 수 있나?" 헷갈리게 할 뿐이라서).
		String qrSection = d.includeQr()
				? """
					<div class="qr">
						<img src="data:image/png;base64,%s" width="150" height="150"/>
					</div>
					""".formatted(qrBase64)
				: "";

		// 취소/노쇼는 정상 영수증이랑 눈에 띄게 다르게 보여야 한다: 환불되는지 아닌지가 결제 상태를
		// 정확히 안 나타내면 손님이 헷갈릴 수 있어서, 상단에 색깔 있는 안내 배너를 따로 넣는다.
		//   - 환불(refund=true): 초록 톤 — "돈 돌려받는다"는 안심 메시지
		//   - 환불 안 됨(refund=false): 주황 톤 — 주의를 끌어야 하는 메시지
		String noticeSection = "";
		if (d.noticeMessage() != null && !d.noticeMessage().isBlank()) {
			String noticeClass = d.refundNotice() ? "notice-refund" : "notice-warning";
			noticeSection = """
					<div class="notice %s">%s</div>
					""".formatted(noticeClass, d.noticeMessage());
		}

		return """
				<html>
				<head><style>
					body { font-family: sans-serif; padding: 24px; }
					h1 { font-size: 20px; }
					table { width: 100%%; border-collapse: collapse; margin-top: 16px; }
					td { padding: 6px 0; }
					.label { color: #666; width: 120px; }
					.qr { margin-top: 24px; text-align: center; }
					.notice { margin-top: 16px; padding: 12px 14px; border-radius: 6px; font-size: 13px; }
					.notice-refund { background: #e6f6ec; color: #1f7a3f; border: 1px solid #b7e4c7; }
					.notice-warning { background: #fdecdf; color: #a54800; border: 1px solid #f5c6a5; }
				</style></head>
				<body>
					<h1>기리기리 결제 영수증</h1>
					%s
					<table>
						<tr><td class="label">매장</td><td>%s</td></tr>
						<tr><td class="label">상품</td><td>%s</td></tr>
						<tr><td class="label">수량</td><td>%d개</td></tr>
						<tr><td class="label">결제금액</td><td>%,d원</td></tr>
						<tr><td class="label">픽업 시간</td><td>%s</td></tr>
						<tr><td class="label">픽업 코드</td><td>%s</td></tr>
						<tr><td class="label">발급 일시</td><td>%s</td></tr>
					</table>
					%s
				</body>
				</html>
				""".formatted(
				noticeSection,
				d.storeName(), d.productName(), d.quantity(), d.totalPrice(),
				d.pickupTime().format(FORMAT), d.pickupCode(),
				LocalDateTime.now().format(FORMAT), qrSection
		);
	}

	public record ReceiptData(
			String storeName,
			String productName,
			int quantity,
			int totalPrice,
			LocalDateTime pickupTime,
			String pickupCode,
			boolean includeQr,
			String noticeMessage,   // null/빈 문자열이면 안내 배너 없음 (정상 영수증)
			boolean refundNotice    // noticeMessage가 있을 때만 의미 있음: true=환불(초록), false=환불안됨(주황)
	) {
	}
}

package net.dsa.girigiri.util;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * 결제 영수증 PDF 생성 유틸.
 * DB/로그인 없이 필요한 값(ReceiptData)만 넘기면 PDF 바이트를 돌려주므로 독립적으로 테스트 가능하다.
 * QR 코드는 QrCodeUtil로 만들어서 PDF 안에 이미지로 같이 넣는다.
 */
public class ReceiptPdfGenerator {

	private static final Logger log = LoggerFactory.getLogger(ReceiptPdfGenerator.class);

	private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

	// 추가됨 (2026-08-24) — 왜: PDF 기본 폰트(CSS의 sans-serif → PDF에 내장된 Helvetica 등)는 한글
	// 글리프가 아예 없어서, 매장명/상품명 같은 한글이 전부 "#"으로 깨져서 나오는 버그가 있었다
	// (openhtmltopdf/PDFBox가 폰트에 없는 글자를 "#"로 대체해서 렌더링함 — 스크린샷으로 확인됨).
	//
	// 수정됨 (2026-08-24) — 처음엔 Noto Sans KR을 한글 음절만 남기고 서브셋해서 썼는데, 그렇게 고친
	// 뒤에도 여전히 "#"으로 깨지는 게 재현됐다(새로 만든 예약으로도 확인함 — 캐싱 문제 아님). Noto Sans
	// CJK는 원래 PostScript 외곽선(CFF) 방식 폰트라서, 서브셋 과정에서 PDFBox가 까다로워하는 걸로
	// 의심된다. 그래서 훨씬 오래되고 검증된, 순수 TrueType(글자 하나하나가 실제 윤곽선 좌표로 된)
	// 무료 한글 폰트인 은돋움(UnDotum)으로 바꿨다 — PDF 임베딩용으로 굉장히 널리 쓰여온 폰트라 호환성
	// 문제가 훨씬 적을 것으로 기대. 서브셋 안 하고 원본 그대로 써서(3.6MB) 혹시 모를 서브셋발 손상
	// 가능성도 없앴다.
	private static final String FONT_FAMILY = "UnDotum";
	private static final String FONT_RESOURCE_PATH = "/fonts/UnDotum.ttf";

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

		// 추가됨 (2026-08-24) — 왜: 폰트를 등록해도 한글이 계속 깨지는 문제를 디버깅하려고 넣었다.
		// 이 로그가 "못 찾음"으로 찍히면 폰트 파일이 빌드에 아예 안 들어간 것(경로/빌드 문제)이고,
		// "찾음"인데도 PDF에서 여전히 깨지면 폰트 자체 호환성 문제라는 걸 구분할 수 있다.
		try (InputStream check = ReceiptPdfGenerator.class.getResourceAsStream(FONT_RESOURCE_PATH)) {
			if (check == null) {
				log.error("[영수증 PDF] 한글 폰트 리소스를 찾지 못했습니다: {} " +
								"(src/main/resources{}가 실제로 있는지, 빌드에 포함됐는지 확인하세요)",
						FONT_RESOURCE_PATH, FONT_RESOURCE_PATH);
			} else {
				log.info("[영수증 PDF] 한글 폰트 리소스 확인됨: {} ({}바이트)",
						FONT_RESOURCE_PATH, check.readAllBytes().length);
			}
		}

		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			PdfRendererBuilder builder = new PdfRendererBuilder();
			builder.useFastMode();
			// 클래스패스(jar 안)에서 폰트 파일을 바로 읽어오는 방식 — File 경로가 아니라
			// InputStream 공급자(supplier)로 넘기면, jar로 패키징된 뒤에도(파일시스템 경로가 없어도)
			// 똑같이 동작한다.
			builder.useFont(() -> ReceiptPdfGenerator.class.getResourceAsStream(FONT_RESOURCE_PATH), FONT_FAMILY);
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

		// 변경됨 (2026-08-24) — 왜: body에 지정한 폰트(font-family)는 자식 요소(h1, table, td, div)로
		// 전부 상속되니까, 여기 한 군데만 커스텀 한글 폰트(FONT_FAMILY)로 바꿔주면 문서 전체에 적용된다.
		// 그 폰트로도 혹시 못 찾는 글자가 있을 경우를 대비해 sans-serif를 폴백으로 남겨뒀다.
		return """
				<html>
				<head><style>
					body { font-family: '%s', sans-serif; padding: 24px; }
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
				FONT_FAMILY,
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

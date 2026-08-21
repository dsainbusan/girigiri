package net.dsa.girigiri.util;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;

/**
 * 픽업 확인용 QR 코드 생성 유틸.
 * DB/로그인 없이 문자열만 넣으면 QR 이미지가 나오므로 독립적으로 테스트 가능하다.
 *
 * 사용 예:
 *   String code = QrCodeUtil.generatePickupCode();       // "PICK-3F9A2B1C"
 *   byte[] png  = QrCodeUtil.generateQrImage(code, 200);  // 200x200 PNG 바이트
 */
public class QrCodeUtil {

	private QrCodeUtil() {
	}

	/** 예약마다 고유한 픽업 코드 문자열 생성 (예: PICK-3F9A2B1C) */
	public static String generatePickupCode() {
		return "PICK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
	}

	/** 문자열을 PNG QR 코드 이미지 바이트로 변환 */
	public static byte[] generateQrImage(String text, int size) throws WriterException, IOException {
		QRCodeWriter writer = new QRCodeWriter();
		BitMatrix matrix = writer.encode(text, BarcodeFormat.QR_CODE, size, size);

		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			MatrixToImageWriter.writeToStream(matrix, "PNG", out);
			return out.toByteArray();
		}
	}
}

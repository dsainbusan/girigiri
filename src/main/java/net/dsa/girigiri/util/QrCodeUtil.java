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
 *   String code = QrCodeUtil.generatePickupCode();       // "PICK-3F9A2B1C7D4E1A2F"
 *   byte[] png  = QrCodeUtil.generateQrImage(code, 200);  // 200x200 PNG 바이트
 */
public class QrCodeUtil {

	private QrCodeUtil() {
	}

	// 수정됨 (2026-09-08, 코드 감사) — 왜: 8자(2^32 공간)만 쓰면 생일 역설로 약 7~8만 건 근처에서
	// 충돌 확률이 50%에 이른다. 컬럼(pickup_code varchar(30))에 유니크 제약도 없어서 DB가 막아주지도
	// 않았다 — 충돌이 나면 findByPickupCode가 단건이 아닌 결과로 QR 스캔·매장취소가 전부 깨진다.
	// 16자(2^64 공간)로 늘리고 ReservationEntity.pickup_code에 유니크 제약을 같이 추가해서, 충돌
	// 가능성을 사실상 0으로 낮추고 혹시라도 발생하면(같은 밀리초에 같은 난수 생성 등) DB가 막아주게 한다.
	private static final int CODE_LENGTH = 16;

	/** 예약마다 고유한 픽업 코드 문자열 생성 (예: PICK-3F9A2B1C7D4E1A2F) */
	public static String generatePickupCode() {
		String hex = UUID.randomUUID().toString().replace("-", "").toUpperCase();
		return "PICK-" + hex.substring(0, CODE_LENGTH);
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

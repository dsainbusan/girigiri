package net.dsa.girigiri.util;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 추가됨 (2026-10-07, 계좌 보안) — DB에 저장하기 전 AES-256-GCM으로 암호화하는 공용 JPA 컨버터.
 * StoreEntity.bankAccount(계좌번호)처럼 "DB 저장은 암호화, 화면은 마스킹, 엑셀 다운로드 시에만
 * 복호화"가 필요한 필드에 @Convert(converter = AesStringConverter.class)로 붙여 쓴다.
 *
 * IV(12바이트)+태그(16바이트)가 암호문에 더해져 Base64로 저장되므로 평문보다 길어진다 — 적용하는
 * 컬럼은 VARCHAR(255) 이상으로 잡아야 한다(계좌번호 평문 20자 기준 대략 60자 안팎).
 *
 * 키(app.encryption.key, env: BANK_ENCRYPTION_KEY)는 길이와 무관하게 SHA-256으로 32바이트
 * AES-256 키로 변환해서 쓴다 — Base64/hex 인코딩 실수를 피하려는 목적. 기본값은 로컬 개발 편의용
 * 더미 키라, 실제 배포 전에는 반드시 .env에 BANK_ENCRYPTION_KEY를 별도로 채워야 한다(README.md
 * "로컬 실행" 섹션에 문서화).
 *
 * Hibernate는 보통 @Converter 클래스를 자기가 직접 new로 만들어서 Spring 빈 주입(@Value)이
 * 안 먹는데, Spring Boot는 HibernateJpaConfiguration에서 SpringBeanContainer를 자동 등록해줘서
 * @Component를 같이 붙이면 Hibernate가 이 클래스를 Spring 빈으로 가져다 쓴다(별도 설정 불필요).
 */
@Slf4j
@Converter
@Component
public class AesStringConverter implements AttributeConverter<String, String> {

	private static final String TRANSFORMATION = "AES/GCM/NoPadding";
	private static final int GCM_IV_LENGTH = 12;
	private static final int GCM_TAG_LENGTH_BITS = 128;

	private final SecretKeySpec key;

	public AesStringConverter(@Value("${app.encryption.key:girigiri-dev-default-encryption-key-change-me}") String rawKey) {
		this.key = deriveKey(rawKey);
	}

	private SecretKeySpec deriveKey(String rawKey) {
		try {
			MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
			byte[] keyBytes = sha256.digest(rawKey.getBytes(StandardCharsets.UTF_8));
			return new SecretKeySpec(keyBytes, "AES");
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("암호화 키 초기화에 실패했습니다.", e);
		}
	}

	@Override
	public String convertToDatabaseColumn(String plainText) {
		if (plainText == null) {
			return null;
		}
		try {
			byte[] iv = new byte[GCM_IV_LENGTH];
			new SecureRandom().nextBytes(iv);

			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
			byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

			byte[] combined = new byte[iv.length + cipherText.length];
			System.arraycopy(iv, 0, combined, 0, iv.length);
			System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
			return Base64.getEncoder().encodeToString(combined);
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("계좌 정보 암호화에 실패했습니다.", e);
		}
	}

	/**
	 * 복호화 실패는 예외를 던지지 않고 null을 돌려준다(쓰기는 반대로 항상 하드 throw — 실패할 이유가
	 * 없으니까). 이 컨버터를 붙이기 전에 sample-data.sql 등으로 평문으로 들어간 기존 계좌 데이터는
	 * Base64/GCM 태그가 안 맞아 복호화가 깨지는데, 그때마다 화면이 500으로 죽으면 안 된다 — null로
	 * 돌아오면 "계좌 미등록"과 동일하게 취급되어 지급 보류로만 이어지고(요구사항 6), 점주가 새로
	 * 등록하면 정상적으로 암호화되어 저장된다.
	 */
	@Override
	public String convertToEntityAttribute(String storedValue) {
		if (storedValue == null) {
			return null;
		}
		try {
			byte[] combined = Base64.getDecoder().decode(storedValue);
			if (combined.length <= GCM_IV_LENGTH) {
				throw new IllegalArgumentException("암호문 길이가 너무 짧습니다.");
			}
			byte[] iv = new byte[GCM_IV_LENGTH];
			System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
			byte[] cipherText = new byte[combined.length - GCM_IV_LENGTH];
			System.arraycopy(combined, GCM_IV_LENGTH, cipherText, 0, cipherText.length);

			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
			return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
		} catch (GeneralSecurityException | IllegalArgumentException e) {
			log.warn("계좌 정보 복호화 실패 — 미등록으로 취급합니다. ({})", e.getMessage());
			return null;
		}
	}
}

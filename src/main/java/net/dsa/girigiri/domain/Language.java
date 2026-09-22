package net.dsa.girigiri.domain;

import lombok.Getter;

import java.util.Arrays;
import java.util.Optional;

/**
 * 환경설정 "언어 설정" 화면에서 고를 수 있는 언어 (2026-09-22, 문창호, WBS 6.1 인수 범위).
 * 지금은 화면 껍데기만 있고 실제 다국어 번역은 없다 — 고른 값은 UserEntity.language에 저장만 되고,
 * 화면에 실제로 반영하는 처리는 다음 과제.
 */
@Getter
public enum Language {

	KO("ko", "한국어"),
	JA("ja", "日本語"),
	EN("en", "English");

	private final String code;
	private final String label;

	Language(String code, String label) {
		this.code = code;
		this.label = label;
	}

	public static Optional<Language> findByCode(String code) {
		return Arrays.stream(values()).filter(l -> l.code.equals(code)).findFirst();
	}
}

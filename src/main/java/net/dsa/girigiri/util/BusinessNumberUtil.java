package net.dsa.girigiri.util;

/**
 * 사업자 등록번호 형식 검사 (2026-09-21, UI/UX 감사 후속 — AuthService.isOwnerApplyValid가
 * isBlank()만 보고 있던 걸 메꾼다). 사업자등록번호는 항상 10자리(3-2-5) 고정이라 자릿수만 본다 —
 * 국세청 체크섬 검증까지는 이 프로젝트 범위를 벗어나 하지 않는다.
 */
public final class BusinessNumberUtil {

	private BusinessNumberUtil() {
	}

	public static boolean isValid(String businessNumber) {
		return PhoneUtil.digitsOnly(businessNumber).length() == 10;
	}
}

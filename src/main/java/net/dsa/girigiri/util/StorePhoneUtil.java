package net.dsa.girigiri.util;

/**
 * 매장 대표 전화번호 형식 검사 (2026-09-21, UI/UX 감사 후속 — AuthService.isOwnerApplyValid /
 * StoreService.isEditValid가 isBlank()만 보고 있던 걸 메꾼다).
 *
 * 개인 휴대폰(PhoneUtil)과 달리 매장 전화는 지역번호(02/031/051 등) 유선도 허용해야 해서 규칙이
 * 다르다 — authView/ownerApply.html·storeView/edit.html의 자동 하이픈 스크립트가 실제로 만들어낼
 * 수 있는 자릿수(02는 9~10자리, 그 외는 10~11자리)와 정확히 맞춰서, 화면에서 통과된 형태가 서버에서
 * 막히는 일이 없게 한다.
 */
public final class StorePhoneUtil {

	private StorePhoneUtil() {
	}

	public static boolean isValid(String phone) {
		String d = PhoneUtil.digitsOnly(phone);
		if (d.isEmpty() || d.charAt(0) != '0') {
			return false;
		}
		boolean seoul = d.startsWith("02");
		int len = d.length();
		return seoul ? (len == 9 || len == 10) : (len == 10 || len == 11);
	}
}

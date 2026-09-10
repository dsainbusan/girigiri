package net.dsa.girigiri.util;

import java.util.regex.Pattern;

/**
 * 휴대폰 번호 형식 검사·정규화 (2026-09-10, 문창호).
 * 회원가입(AuthService)·회원정보 수정(MypageService) 양쪽에서 같은 규칙을 쓰려고 util로 뺐다.
 * 본인확인(실명인증)은 하지 않는다 — 형식만 본다.
 */
public final class PhoneUtil {

	// 숫자만 남겼을 때 010/011/016/017/018/019 + 7~8자리
	private static final Pattern DIGITS_PATTERN = Pattern.compile("^01[016789]\\d{7,8}$");

	private PhoneUtil() {
	}

	public static String digitsOnly(String s) {
		return s == null ? "" : s.replaceAll("\\D", "");
	}

	public static boolean isValid(String phone) {
		return DIGITS_PATTERN.matcher(digitsOnly(phone)).matches();
	}

	/** "01012345678" → "010-1234-5678" (10자리면 3-3-4, 11자리면 3-4-4). 저장·표시는 이 형식으로 통일. */
	public static String format(String phone) {
		String d = digitsOnly(phone);
		if (d.length() == 11) {
			return d.substring(0, 3) + "-" + d.substring(3, 7) + "-" + d.substring(7);
		}
		if (d.length() == 10) {
			return d.substring(0, 3) + "-" + d.substring(3, 6) + "-" + d.substring(6);
		}
		return d;
	}
}

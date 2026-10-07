package net.dsa.girigiri.util;

/**
 * 추가됨 (2026-10-07, 계좌 보안) — 계좌번호를 화면에 보여줄 때 가운데를 가리는 공용 유틸.
 * 전체 번호는 슈퍼어드민 "이체 목록 Excel 다운로드"에서만 쓰고(SuperAdminSettlementService
 * #buildTransferExcel), 그 외 화면(정산 목록, 매장 상세, 입점 심사 등)은 전부 이 마스킹값만 보여준다.
 */
public final class BankAccountMaskUtil {

	private BankAccountMaskUtil() {
	}

	/**
	 * 앞 3자리 + 뒤 4자리만 보여주고 나머지는 '*'로 채운다. 계좌번호는 이 앱에서 "-" 없이 저장하므로
	 * (edit.html placeholder 참고) 구분자 없이 반환한다. 7자 이하는 짧아서 뒤 4자리를 보여주면 사실상
	 * 다 보이는 셈이라 전부 가린다.
	 */
	public static String mask(String account) {
		if (account == null || account.isBlank()) {
			return null;
		}
		String value = account.trim();
		int len = value.length();
		if (len <= 7) {
			return "*".repeat(len);
		}
		String head = value.substring(0, 3);
		String tail = value.substring(len - 4);
		return head + "*".repeat(len - 7) + tail;
	}
}

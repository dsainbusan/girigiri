package net.dsa.girigiri.util;

import net.dsa.girigiri.domain.dto.LedgerData;

import java.nio.charset.StandardCharsets;

/**
 * 절약 가계부 내보내기 — CSV (WBS 6.0 "절약 내역 내보내기(CSV/PDF)", 문창호).
 * PDF와 데이터 소스(LedgerData) 동일. 엑셀에서 열어도 한글이 안 깨지도록 UTF-8 BOM(﻿)을 앞에 붙인다.
 */
public final class LedgerCsvGenerator {

	private static final String BOM = "﻿";

	private LedgerCsvGenerator() {
	}

	public static byte[] generate(LedgerData data) {
		StringBuilder sb = new StringBuilder(BOM);

		sb.append(escape((data.nickname() != null ? data.nickname() : "회원") + "님의 절약 가계부")).append('\n');
		line(sb, "이번 달 절약", data.thisMonthSaved() + "원");
		line(sb, "누적 절약", data.totalSaved() + "원");
		line(sb, "절약률", data.rescueRatePercent() + "%");
		line(sb, "구제한 음식", data.rescuedCount() + "개");
		line(sb, "CO2 절감", String.format("%.1fkg", data.co2Kg()));
		line(sb, "등급", data.tier());
		line(sb, "대표 뱃지", data.representativeBadge() != null
				? data.representativeBadge().icon() + " " + data.representativeBadge().name()
				: "미설정");
		sb.append('\n');

		sb.append("날짜,매장,상품,수량,정상가 합,결제액,절약액\n");
		for (LedgerData.HistoryRow h : data.history()) {
			sb.append(escape(h.dateLabel())).append(',')
					.append(escape(h.storeName())).append(',')
					.append(escape(h.productName())).append(',')
					.append(h.quantity()).append(',')
					.append(h.originalTotal()).append(',')
					.append(h.paidTotal()).append(',')
					.append(h.saved()).append('\n');
		}
		if (data.history().isEmpty()) {
			sb.append(escape("아직 구제 내역이 없습니다.")).append('\n');
		}

		return sb.toString().getBytes(StandardCharsets.UTF_8);
	}

	private static void line(StringBuilder sb, String label, String value) {
		sb.append(escape(label)).append(',').append(escape(value)).append('\n');
	}

	/** 콤마·따옴표·줄바꿈이 섞인 값은 큰따옴표로 감싸고 내부 따옴표는 두 배로. */
	private static String escape(String s) {
		if (s == null) {
			return "";
		}
		if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
			return "\"" + s.replace("\"", "\"\"") + "\"";
		}
		return s;
	}
}

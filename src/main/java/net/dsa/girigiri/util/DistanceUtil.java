package net.dsa.girigiri.util;

/**
 * 강노은: 홈/검색/추천 세 곳에서 각자 들고 있던 Haversine 거리 계산이 리뷰에서 지적돼서
 * 하나로 합쳤다. 겸사겸사 반올림 버그도 고침 — 기존엔 "km < 1"만 보고 m/km를 나눠서
 * 0.9996km 같은 값이 "1000m"로 표시됐다(반올림 후 재확인 안 해서). 이제 미터로 반올림한
 * 값이 실제로 1000 넘는지를 본다.
 */
public final class DistanceUtil {

	private static final double EARTH_RADIUS_KM = 6371.0;

	private DistanceUtil() {
	}

	/** 좌표 중 하나라도 없으면 Double.MAX_VALUE — "계산 불가 = 정렬 시 맨 뒤로" 용도로 그대로 쓸 수 있다. */
	public static double km(Double lat1, Double lng1, Double lat2, Double lng2) {
		if (lat1 == null || lng1 == null || lat2 == null || lng2 == null) {
			return Double.MAX_VALUE;
		}
		double dLat = Math.toRadians(lat2 - lat1);
		double dLng = Math.toRadians(lng2 - lng1);
		double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
				+ Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
				* Math.sin(dLng / 2) * Math.sin(dLng / 2);
		double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
		return EARTH_RADIUS_KM * c;
	}

	/** "350m" | "1.2km" 형태 라벨. km(...)이 계산 불가(MAX_VALUE)였으면 빈 문자열. */
	public static String label(double km) {
		if (km == Double.MAX_VALUE) {
			return "";
		}
		long meters = Math.round(km * 1000);
		return meters < 1000 ? meters + "m" : String.format("%.1fkm", km);
	}
}

package net.dsa.girigiri.util;

/**
 * 추가됨 (2026-10-06) — 통계 대시보드 "지역별 현황" 판정 기준 + 처리 대기 SLA 임계값을 하드코딩하지
 * 말고 한 곳에 모아달라는 요청. 지금은(1단계) 상수 클래스로 두고, 2단계에서 dashboard_policy
 * 설정 테이블 + 변경 이력(감사 로그)으로 옮긴다(계획만, 이번 작업 범위 아님).
 */
public final class DashboardPolicy {

	/** 지역 소진율 판정 — 이 값 미만으로 등록된 지역은 "표본 부족"(퍼센트 자체를 신뢰하지 않음). */
	public static final int REGION_SAMPLE_SIZE_MIN = 5;

	/** 표본이 충분한 지역 중, 이 % 미만이면 "점검 필요". 그 외(=이상)는 "정상". */
	public static final int REGION_LOW_SELLTHROUGH_PERCENT = 20;

	/** 처리 대기 "신고 접수" 항목의 SLA(시간) — 넘기면 빨간색으로 표시. */
	public static final long SLA_REPORT_HOURS = 24;

	/** 처리 대기 나머지 항목(입점 신청/매장 문의/유저 문의)의 SLA(시간). */
	public static final long SLA_OTHER_HOURS = 72;

	private DashboardPolicy() {
	}
}

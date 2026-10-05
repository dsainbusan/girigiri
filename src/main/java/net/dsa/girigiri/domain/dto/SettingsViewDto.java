package net.dsa.girigiri.domain.dto;

/**
 * 환경설정 메인 화면(GET /user/settings)에 필요한 값을 한 번에 담아 넘긴다.
 * owner=false면 storeSettlementAlertEnabled/storeAutomationAlertEnabled는 null(매장 관리 섹션 자체를 숨김).
 *
 * 변경됨 (2026-10-05, 사용자 요청) — 언어 설정은 껍데기 기능이라 제거(Language.java 등 관련 코드 전부 삭제).
 * 알림함(NotificationController)의 "알림 설정" 화면으로 한 번 더 들어가야 했던 PUSH/찜한 가게 알림
 * 토글을, 한 뎁스 줄이려고 이 화면 안으로 끌어왔다(pushEnabled/likeAlertEnabled 추가).
 */
public record SettingsViewDto(
		boolean marketingAgreed,
		boolean pushEnabled,
		boolean likeAlertEnabled,
		boolean owner,
		Boolean storeSettlementAlertEnabled,
		Boolean storeAutomationAlertEnabled
) {
}

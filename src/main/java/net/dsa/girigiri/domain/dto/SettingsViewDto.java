package net.dsa.girigiri.domain.dto;

/**
 * 환경설정 메인 화면(GET /user/settings)에 필요한 값을 한 번에 담아 넘긴다.
 * owner=false면 storeSettlementAlertEnabled/storeAutomationAlertEnabled는 null(매장 관리 섹션 자체를 숨김).
 */
public record SettingsViewDto(
		boolean marketingAgreed,
		String languageCode,
		String languageLabel,
		boolean owner,
		Boolean storeSettlementAlertEnabled,
		Boolean storeAutomationAlertEnabled
) {
}

package net.dsa.girigiri.domain.dto;

/**
 * 챗봇의 "예약 조회" tool(GeminiClient.ReservationToolExecutor)이 Gemini에게 돌려주는 데이터 모양.
 * 실제로 취소를 처리하지는 않고, 지금 이 예약이 취소 가능한 상태인지와 그 이유만 알려준다 —
 * 취소 자체는 항상 화면(마이페이지) 버튼으로만 가능하다.
 */
public record ReservationCancelStatusDto(
		Long reservationId,
		String productName,
		String storeName,
		String status,               // ReservationEntity.status 원문 (pending/confirmed/ready)
		String reservedAtDisplay,
		boolean cancelEligible,
		String reasonIfNotEligible   // cancelEligible=true면 null
) {
}

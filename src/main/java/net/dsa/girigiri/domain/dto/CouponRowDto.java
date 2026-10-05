package net.dsa.girigiri.domain.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * "내 쿠폰함" 화면에 뿌리는 행 1개 — 2026-09-07 재설계 (공유 수량 풀 개념 제거, 회원 1인당 1장으로 변경).
 * 표시용 가공(만료일 포맷, 상태 라벨, 출처 한글 라벨)은 전부 CouponService에서 계산해서 채운다.
 */
@Getter
@Builder
public class CouponRowDto {

    private Long id;
    private String sourceLabel;
    private Integer discountRate;
    // "10% 할인" / "3,000원 할인" (정액 쿠폰이 "null%"로 보이던 것 정리, 2026-10-06) · areaLabel은 지역 지정 쿠폰만 "서울·경기 매장에서 사용".
    private String discountLabel;
    private String areaLabel;
    private String expiresAtLabel;
    private boolean used;
    private boolean expired;
    private String statusLabel;
}

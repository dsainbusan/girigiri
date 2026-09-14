-- 2026-09-12 (문창호) — 절약 가계부 뱃지(WBS 6.0) 영구 획득 기록 테이블.
-- Badge enum 조건 중 GOAL_HIT(이달 목표 달성률)처럼 시간이 지나며 다시 거짓이 될 수 있는 것도 있어서,
-- 매번 실시간 조건으로만 판정하면 이미 딴 뱃지가 다음 달에 다시 잠기는 문제가 있었다.
-- 조건을 처음 충족한 시점을 이 테이블에 기록해두고, 이후 해금 여부는 이 기록의 존재로 판정한다
-- (조건이 다시 거짓이 되어도 한 번 딴 뱃지는 영구 유지).
-- 로컬은 ddl-auto=update로 자동 생성되지만, 운영/통합 DB엔 이 스크립트로 반영한다.

CREATE TABLE user_badge (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT NOT NULL,
    badge_code VARCHAR(50) NOT NULL,
    earned_at  DATETIME NOT NULL,
    UNIQUE KEY uk_user_badge (user_id, badge_code)
);

-- 2026-10-01 (강노은) — 리뷰를 "매장당 1건"에서 "픽업완료 예약(구매)당 1건"으로 바꾼다.
-- 기존 리뷰는 특정 구매에 못 묶으니 reservation_id를 NULL로 둔다(레거시 리뷰로 계속 보여줌).
-- UNIQUE는 NULL끼리는 중복으로 안 치므로, 레거시 NULL 리뷰가 여러 개 있어도 제약에 안 걸리고
-- "값이 있는 reservation_id"만 예약당 리뷰 1건으로 막아준다.
-- 로컬은 ddl-auto=update로 자동 추가되지만, 운영/통합 DB엔 이 스크립트로 반영한다.

ALTER TABLE review
    ADD COLUMN reservation_id BIGINT NULL,
    ADD CONSTRAINT uk_review_reservation UNIQUE (reservation_id);

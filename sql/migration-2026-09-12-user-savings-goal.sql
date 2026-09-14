-- 2026-09-12 (문창호) — 마이페이지 절약 가계부(WBS 6.0)의 "이번 달 절약 목표" 및 "대표 뱃지" 저장용 컬럼.
-- 로컬은 ddl-auto=update로 자동 추가되지만, 운영/통합 DB엔 이 스크립트로 반영한다.

ALTER TABLE users
    ADD COLUMN savings_goal_amount INT NULL,
    ADD COLUMN representative_badge VARCHAR(50) NULL;

-- 2026-09-10 (문창호) — 회원가입 휴대폰 번호 컬럼.
-- 본인확인(실명인증) API는 붙이지 않았다 — 입력값 그대로 저장(미인증). "010-1234-5678" 형식.
-- 로컬은 ddl-auto=update 로 자동 추가되지만, 운영/통합 DB엔 이 스크립트로 반영한다.
-- 기존 회원은 값이 없으므로 NULL 허용. 신규 가입은 필수 입력.

ALTER TABLE users
    ADD COLUMN phone VARCHAR(20) NULL;

-- 번호 1개당 계정 1개 (이메일 찾기·비밀번호 재설정이 명확하도록). NULL은 여러 개 허용됨(기존 회원).
-- 주의: 이미 phone 중복 데이터가 있으면 이 줄이 실패한다 — 먼저 중복을 정리할 것.
ALTER TABLE users
    ADD CONSTRAINT uk_users_phone UNIQUE (phone);

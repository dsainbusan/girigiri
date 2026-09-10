-- 2026-09-10 (문창호) — 회원가입 약관 동의 저장용 컬럼.
-- 로컬은 spring.jpa.hibernate.ddl-auto=update 라 앱 재시작 시 자동 추가되지만,
-- 운영/통합 DB엔 이 스크립트로 명시 반영한다.
-- 컬럼 타입은 Hibernate가 boolean → BIT(1)로 만드는 것과 맞췄다(수동/자동 결과 일치용).
-- 기존 회원은 가입 시점에 (약관 단계가 없던 때) 이용약관·개인정보에 동의한 것으로 간주해 true 백필.

ALTER TABLE users
    ADD COLUMN terms_agreed     BIT(1)      NOT NULL DEFAULT b'0',
    ADD COLUMN privacy_agreed   BIT(1)      NOT NULL DEFAULT b'0',
    ADD COLUMN marketing_agreed BIT(1)      NOT NULL DEFAULT b'0',
    ADD COLUMN agreed_at        DATETIME(6) NULL;

UPDATE users
   SET terms_agreed = 1,
       privacy_agreed = 1,
       agreed_at = COALESCE(created_at, NOW())
 WHERE profile_completed = 1;

-- 2026-09-23 (문창호) — user_social_accounts에 행별 구분용 이메일 컬럼 추가.
-- 계정 하나에 같은 provider(예: 구글)로 서로 다른 실제 계정 두 개가 연동될 수 있는데(1:N 연동의
-- 정상 시나리오), 마이페이지 목록이 UserEntity.email(계정 전체에 하나뿐인 대표 이메일)을 그대로
-- 보여줘서 서로 다른 두 행이 같은 이메일로 뜨는 문제가 있었다. 이 컬럼에 그 소셜 계정 고유의
-- 이메일을 저장해서 행마다 구분되게 한다. 카카오/라인처럼 이메일 동의항목이 없으면 null로 남는다.
-- 로컬은 ddl-auto=update로 자동 추가되지만, 운영/통합 DB엔 이 스크립트로 반영한다.

ALTER TABLE user_social_accounts
    ADD COLUMN connected_email VARCHAR(100) NULL;

-- 기존 행은 당장은 null로 두되, users.email이 있고 users.oauth_provider가 해당 행과 같은
-- provider인 경우(=그 계정의 "대표" 소셜과 일치하는 행)에 한해 best-effort로 한 번 채워준다.
UPDATE user_social_accounts usa
    JOIN users u ON u.id = usa.user_id
SET usa.connected_email = u.email
WHERE usa.connected_email IS NULL
  AND u.email IS NOT NULL
  AND u.oauth_provider = usa.provider
  AND u.oauth_id = usa.provider_id;

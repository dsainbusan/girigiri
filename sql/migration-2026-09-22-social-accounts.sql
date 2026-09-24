-- 2026-09-22: 1:N 멀티 소셜 계정 연동(Account Linking) 테이블 신설 및 기존 회원 마이그레이션.
-- 한 명의 회원(users)이 카카오, 네이버, 구글, 라인 등 여러 로그인 수단을 연결하여 사용할 수 있다.

CREATE TABLE IF NOT EXISTS user_social_accounts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    provider VARCHAR(20) NOT NULL,
    provider_id VARCHAR(100) NOT NULL,
    connected_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_social_accounts_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uk_social_provider_id UNIQUE (provider, provider_id)
);

-- 기존 users에 있는 (oauth_provider, oauth_id) 데이터를 새 테이블로 1회 안전 마이그레이션
INSERT IGNORE INTO user_social_accounts (user_id, provider, provider_id, connected_at)
SELECT id, oauth_provider, oauth_id, NOW()
FROM users
WHERE oauth_provider IS NOT NULL AND oauth_id IS NOT NULL;

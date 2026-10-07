-- 2026-10-07 (계좌 보안) — 정산 계좌를 암호화하고, 입점 신청 필수화 + 변경 승인제로 바꾸면서 생긴
-- 스키마 변경 전부. 로컬은 ddl-auto=update가 대부분 자동 반영하지만(신규 컬럼/테이블), bank_account
-- 길이 확장(40→255)은 기존 컬럼을 줄이는 쪽이 아니라 늘리는 거라 ddl-auto가 알아서 해줄 때도 있고
-- 안 해줄 때도 있어 명시적으로 ALTER한다(coupon/refund 쪽 nullable 마이그레이션과 동일한 이유).
--
-- 주의: bank_account 컬럼은 이 마이그레이션 이전엔 평문으로 저장돼 있었다. AesStringConverter 적용
-- 이후 기존 평문 값은 복호화에 실패해 NULL로 읽힌다(AesStringConverter#convertToEntityAttribute가
-- 의도적으로 예외 대신 null을 돌려줌 — 화면이 500으로 안 죽게). 즉 이 마이그레이션 적용 전에 이미
-- bank_account가 채워져 있던 매장은 전부 "계좌 미등록" 취급되며, 정산 지급도 자동으로 보류된다
-- (account_status 기본값이 UNREGISTERED라 별도 처리 불필요 — 요구사항 6 "기존 데이터가 안 깨지게").
-- 해당 매장은 /store/bank-account에서 계좌를 다시 등록하고 슈퍼어드민 승인을 받아야 정상화된다.

ALTER TABLE store
    MODIFY COLUMN bank_account VARCHAR(255) NULL,
    ADD COLUMN passbook_image_url VARCHAR(255) NULL,
    ADD COLUMN account_status VARCHAR(20) NOT NULL DEFAULT 'UNREGISTERED';

ALTER TABLE settlement
    ADD COLUMN transfer_receipt_url VARCHAR(255) NULL,
    ADD COLUMN bank_name VARCHAR(30) NULL,
    ADD COLUMN bank_account VARCHAR(255) NULL,
    ADD COLUMN account_holder VARCHAR(40) NULL;

CREATE TABLE bank_account_change_request (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_id                BIGINT NOT NULL,
    requested_by            BIGINT NOT NULL,
    requested_at            DATETIME NOT NULL,
    status                  VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reviewed_by             BIGINT NULL,
    reviewed_at             DATETIME NULL,
    reject_reason           VARCHAR(255) NULL,
    old_bank_name           VARCHAR(30) NULL,
    old_bank_account_masked VARCHAR(40) NULL,
    old_account_holder      VARCHAR(40) NULL,
    new_bank_name           VARCHAR(30) NULL,
    new_bank_account        VARCHAR(255) NULL,
    new_bank_account_masked VARCHAR(40) NULL,
    new_account_holder      VARCHAR(40) NULL,
    new_passbook_image_url  VARCHAR(255) NULL,
    INDEX idx_bank_account_change_request_store_id (store_id),
    INDEX idx_bank_account_change_request_status (status),
    FOREIGN KEY (store_id) REFERENCES store(id),
    FOREIGN KEY (requested_by) REFERENCES users(id),
    FOREIGN KEY (reviewed_by) REFERENCES users(id)
);

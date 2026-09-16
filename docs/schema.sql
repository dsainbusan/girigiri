-- =====================================================================
-- 기리기리 ERD용 DDL (2026-09-16 갱신 — 코드 기준 24개 테이블 전체 반영)
-- =====================================================================
-- 용도: 이 파일 자체를 실행하려는 게 아니라(테이블은 ddl-auto=update로 자동 생성됨),
--       ERD Editor(vuerd) 플러그인의 "SQL DDL Import" 기능에 붙여넣어서
--       도표를 자동으로 그리기 위한 용도다.
--
-- 사용법 (IntelliJ ERD Editor 플러그인):
--   1. docs/ 폴더에 기리기리.vuerd.json 파일이 이미 있으면 그대로 열고, 없으면 빈 파일로 새로 생성
--   2. 그 파일을 열면 캔버스가 뜬다 (기존 도표가 있으면 지우고 새로 그리는 걸 권장 — 테이블이 12개 → 24개로 늘어서
--      자동 배치가 훨씬 깔끔하다)
--   3. 캔버스 우클릭 → SQL → SQL DDL Import (또는 상단 메뉴의 SQL 아이콘)
--   4. 이 파일 내용을 통째로 붙여넣고 Run/Create
--   5. 테이블이 자동 배치되면, 화면에 안 보이는 FK 관계선은 컬럼을 드래그해서 수동으로 연결
--      (버전에 따라 FOREIGN KEY 제약을 보고 자동으로 관계선을 그려주기도 함)
--   6. 다 그렸으면 저장 → 그 결과가 기리기리.vuerd.json에 담긴다 → git commit
--
-- 주의: domain/entity/*.java는 아직 FK를 plain Long 컬럼으로만 두고 있고
--       (JPA @ManyToOne 매핑 없음), 실제 DB에는 FOREIGN KEY 제약이 없다.
--       아래 FOREIGN KEY는 "설계 의도"를 ERD에 시각화하기 위한 것이지,
--       실제 스키마에 반영하라는 뜻이 아니다. ddl-auto=update는 이 파일을 보지 않는다.
--       (같은 이유로, "스냅샷/감사 로그" 성격이라 실제 회원·매장이 없어도 값이 남아야 하는 컬럼
--        — complaint.reporter_id/target_store_id 등 — 은 일부러 FK를 걸지 않았다.)
--
-- store.owner_id 관계 모델: 안B(User가 storeId로 Store를 소유) 확정 — 2026-08-21, StoreEntity
-- 코드 주석 참고. 안A(Store 독립 로그인 계정)용 레거시 컬럼(login_id/password)은 이미 제거됨.
--
-- 갱신 이력:
--   2026-08-21  최초 작성 (users/store/product/reservation/review/likes/receipt/report, 7테이블)
--   2026-08-25  테이블 정의서 추가 (docs/table-definition.md)
--   2026-08-26~27  listing_template/menu_item 추가, store에 POS·정산계좌 컬럼 추가
--   2026-09-01  notice/complaint/settlement 추가
--   2026-09-16  코드 전수 조사 후 payment/payment_cancel/coupon/coupon_policy/coupon_campaign/
--               review_summary/notification/notification_setting/inquiry/inquiry_comment/user_badge
--               11개 테이블 신규 반영 + reservation(coupon_id/cancelled_by/cancel_reason/pickup_code UK),
--               review(image_url/edited) 등 기존 테이블 누락 컬럼 보강 → 총 24개 테이블
--   2026-09-16  store에 매장 신뢰도(취소율) 자동 정지 컬럼 3개 추가(reliability_suspended_until/
--               reliability_suspension_count/reliability_banned) — StoreReliabilityService 신규
-- =====================================================================

-- ── 1. 회원 / 매장 / 상품 ───────────────────────────────────────────

CREATE TABLE users (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    oauth_provider        VARCHAR(20) NOT NULL COMMENT 'kakao / naver / google / email',
    oauth_id              VARCHAR(100) NOT NULL COMMENT 'provider=email이면 이메일 값 자체',
    password              VARCHAR(100) COMMENT '이메일 가입자만 값 존재(BCrypt). 소셜 계정은 NULL',
    role                  VARCHAR(20) NOT NULL COMMENT 'USER / OWNER / ADMIN',
    status                VARCHAR(20) DEFAULT 'ACTIVE' COMMENT 'ACTIVE / SUSPENDED (슈퍼어드민 회원 정지)',
    nickname              VARCHAR(30),
    email                 VARCHAR(100) COMMENT '표시(마스킹)용, 로그인 식별자 아님',
    phone                 VARCHAR(20) COMMENT '본인인증 미연동, 사용자가 입력한 값 그대로 (2026-09-10 추가)',
    savings_goal_amount   INT COMMENT '절약 가계부 이번 달 목표 금액 (2026-09-12 추가)',
    representative_badge  VARCHAR(50) COMMENT '가계부 대표 뱃지 코드 (2026-09-12 추가)',
    region                VARCHAR(100) COMMENT '주로 이용할 동네, 자유 텍스트',
    profile_completed     TINYINT(1) NOT NULL DEFAULT 0,
    latitude              DOUBLE,
    longitude             DOUBLE,
    terms_agreed          TINYINT(1) NOT NULL DEFAULT 0 COMMENT '이용약관 동의 (2026-09-10 추가)',
    privacy_agreed        TINYINT(1) NOT NULL DEFAULT 0,
    marketing_agreed      TINYINT(1) NOT NULL DEFAULT 0,
    agreed_at             DATETIME,
    created_at            DATETIME,
    updated_at            DATETIME,
    UNIQUE KEY uk_users_oauth (oauth_provider, oauth_id),
    UNIQUE KEY uk_users_phone (phone)
);

-- store.owner_id 관계 모델: 안B 확정(2026-08-21). login_id/password(안A용 레거시)는 제거됨 —
-- 로그인은 전부 users 쪽 OAuth2/이메일로만 이뤄지고, store는 로그인 주체가 아니라 User(role=OWNER)가
-- owner_id로 소유하는 데이터다.
CREATE TABLE store (
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_name             VARCHAR(50) NOT NULL,
    category               VARCHAR(30),
    address                VARCHAR(200),
    latitude               DOUBLE,
    longitude              DOUBLE,
    operating_hours        VARCHAR(100),
    prep_time_minutes      INT DEFAULT 20 COMMENT 'NULL이면 매장이 아직 설정 전(항상 주문 가능 취급)',
    last_pickup_time       TIME COMMENT '이 시간 이후 당일 주문/픽업 마감',
    rescue_goal_percent    INT DEFAULT 70 COMMENT '대시보드 구제율 목표치(%), 점주가 직접 수정 가능',
    role                   VARCHAR(20) NOT NULL COMMENT '= OWNER',
    business_number        VARCHAR(30),
    phone                  VARCHAR(30),
    approval_status        VARCHAR(20) DEFAULT 'PENDING' COMMENT 'PENDING / APPROVED / REJECTED',
    status                 VARCHAR(20) COMMENT 'ACTIVE / SUSPENDED (NULL=ACTIVE와 동일 취급, 슈퍼어드민 매장 정지)',
    pos_provider           VARCHAR(30) COMMENT 'okpos/posbank/unionpos/etc, NULL=미연동',
    pos_store_code         VARCHAR(50),
    pos_connected_at       DATETIME,
    pos_last_sync_at       DATETIME,
    pos_draft_prompt_time  TIME COMMENT '매일 이 시각에 POS 재고 스냅샷으로 "오늘의 구제" 초안 자동 생성(B안)',
    bank_name              VARCHAR(30) COMMENT '정산 입금 계좌 (WBS 2.0 매장 정산)',
    bank_account           VARCHAR(40),
    account_holder         VARCHAR(40),
    owner_id               BIGINT COMMENT 'users.id 참조 — 이 매장을 소유한 점주(role=OWNER) 계정',
    reliability_suspended_until  DATETIME COMMENT '신뢰도(취소율) 자동 정지 해제 시각. NULL/과거면 정지 아님 (2026-09-16 추가)',
    reliability_suspension_count INT NOT NULL DEFAULT 0 COMMENT '신뢰도 위반 누적 정지 횟수(0~3), 해제돼도 리셋 안 됨',
    reliability_banned           TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'true면 영구정지(4회차 위반)',
    created_at             DATETIME,
    updated_at             DATETIME,
    INDEX idx_store_category (category),
    INDEX idx_store_approval_status (approval_status),
    FOREIGN KEY (owner_id) REFERENCES users(id)
);

-- POS 카탈로그 메뉴 (2026-08-27 문창호, "POS json 카탈로그 연동 (가정)")
-- 매장의 영속 메뉴 목록 + 현재 재고. POS가 재고를 push하면(B안) 마감 무렵 이 재고로 초안 자동 생성.
CREATE TABLE menu_item (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_id           BIGINT NOT NULL,
    pos_sku            VARCHAR(50) COMMENT 'POS 원본 식별자, upsert 기준',
    name               VARCHAR(100) NOT NULL,
    original_price     INT NOT NULL,
    image_url          VARCHAR(255),
    stock_quantity     INT COMMENT 'POS가 push한 현재 재고. NULL이면 재고 정보 없음',
    app_sale_enabled   TINYINT(1) NOT NULL DEFAULT 0,
    discount_rate      INT COMMENT 'NULL이면 마감시간 기준 자동(20/30/50). 값이 있어도 자동값보다 낮으면 자동값 적용(덜 깎기 금지)',
    app_sale_quantity  INT COMMENT 'NULL이면 POS 재고 전량. 초안 수량 = MIN(재고, 이 값)',
    created_at         DATETIME,
    updated_at         DATETIME,
    FOREIGN KEY (store_id) REFERENCES store(id)
);

-- "오늘의 구제 자동 등록" 템플릿 (2026-08-26 문창호, WBS엔 없는 신규 기능)
-- 사장님이 한 번 등록 → ListingDraftScheduler가 매일 prompt_time에 product(status='draft') 초안 생성 + 알림
CREATE TABLE listing_template (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_id          BIGINT NOT NULL,
    name              VARCHAR(100) NOT NULL,
    original_price    INT NOT NULL,
    image_url         VARCHAR(255),
    description       VARCHAR(500),
    default_quantity  INT NOT NULL,
    weekdays          VARCHAR(20) NOT NULL COMMENT 'ISO 요일 CSV, 1=월…7=일. 예 "1,2,3,4,5"',
    prompt_time       TIME NOT NULL,
    active            TINYINT(1) NOT NULL DEFAULT 1,
    created_at        DATETIME,
    updated_at        DATETIME,
    FOREIGN KEY (store_id) REFERENCES store(id)
);

CREATE TABLE product (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_id            BIGINT NOT NULL,
    name                VARCHAR(100) NOT NULL,
    original_price      INT NOT NULL,
    discounted_price    INT NOT NULL,
    quantity            INT NOT NULL,
    remaining_quantity  INT NOT NULL,
    image_url           VARCHAR(255),
    description         VARCHAR(500),
    status              VARCHAR(20) NOT NULL COMMENT 'draft/active/sold/expired/skipped (draft=자동초안, skipped="오늘 안 함")',
    template_id         BIGINT COMMENT '템플릿 방식 초안이면 listing_template.id',
    menu_item_id        BIGINT COMMENT 'POS 재고 스냅샷 방식 초안이면 menu_item.id',
    registered_at       DATETIME,
    updated_at          DATETIME COMMENT '상품 수정 + 재고 변동(예약/취소)에도 갱신됨',
    INDEX idx_product_store_id (store_id),
    INDEX idx_product_status (status),
    FOREIGN KEY (store_id) REFERENCES store(id),
    FOREIGN KEY (template_id) REFERENCES listing_template(id),
    FOREIGN KEY (menu_item_id) REFERENCES menu_item(id)
);

-- ── 2. 예약 / 결제 / 쿠폰 / 영수증 ──────────────────────────────────

CREATE TABLE reservation (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id            BIGINT NOT NULL,
    product_id         BIGINT NOT NULL,
    product_name       VARCHAR(100) COMMENT '주문 당시 상품명 스냅샷',
    store_id           BIGINT NOT NULL,
    reserved_quantity  INT NOT NULL,
    total_price        INT NOT NULL,
    coupon_id          BIGINT COMMENT '이 예약에 쓴 쿠폰. 안 썼으면 NULL (2026-09-07 추가)',
    pickup_time        DATETIME,
    pickup_code        VARCHAR(30) COMMENT 'QR/현장 확인 코드',
    status             VARCHAR(20) NOT NULL COMMENT 'pending/confirmed/ready/picked/cancelled/noshowed',
    reserved_at        DATETIME,
    accepted_at        DATETIME COMMENT '매장이 수락한 시각(confirmed→ready 전환 시점)',
    picked_at          DATETIME,
    cancelled_by       VARCHAR(10) COMMENT 'USER/STORE/ADMIN, status=cancelled일 때만 값 존재',
    cancel_reason      VARCHAR(255) COMMENT 'STORE/ADMIN 취소 시 사유',
    INDEX idx_reservation_user_id (user_id),
    INDEX idx_reservation_product_id (product_id),
    INDEX idx_reservation_store_id (store_id),
    UNIQUE KEY uk_reservation_pickup_code (pickup_code),
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (product_id) REFERENCES product(id),
    FOREIGN KEY (store_id) REFERENCES store(id)
);

-- 결제 1건 (PortOne/아임포트). 담당: 송채현.
CREATE TABLE payment (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    reservation_id  BIGINT NOT NULL,
    merchant_uid    VARCHAR(50) NOT NULL COMMENT '가맹점 주문번호, 우리 서버가 생성',
    imp_uid         VARCHAR(50) COMMENT 'PortOne 결제 고유번호, 승인 후 채워짐',
    amount          INT NOT NULL,
    pay_method      VARCHAR(20) COMMENT 'card/kakaopay/naverpay 등',
    pay_status      VARCHAR(20) NOT NULL COMMENT 'READY/PAID/FAILED/CANCELLED (enum, 대문자 저장)',
    fail_reason     VARCHAR(255),
    paid_at         DATETIME,
    requested_at    DATETIME NOT NULL COMMENT '결제 요청(레코드 생성) 시각',
    updated_at      DATETIME,
    UNIQUE KEY uk_payment_reservation_id (reservation_id),
    UNIQUE KEY uk_payment_merchant_uid (merchant_uid),
    UNIQUE KEY uk_payment_imp_uid (imp_uid),
    FOREIGN KEY (reservation_id) REFERENCES reservation(id)
);

-- 결제 취소/환불 "시도" 이력 (감사 로그, 불변). 담당: 송채현, 송보미 제안 (2026-08-25).
CREATE TABLE payment_cancel (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_id    BIGINT NOT NULL,
    amount        INT NOT NULL,
    reason        VARCHAR(255),
    succeeded     TINYINT(1) NOT NULL COMMENT 'true=환불 성공(또는 결제 전 취소), false=PortOne 환불 API 실패',
    requested_at  DATETIME NOT NULL COMMENT '취소/환불 요청 시각',
    INDEX idx_payment_cancel_payment_id (payment_id),
    FOREIGN KEY (payment_id) REFERENCES payment(id)
);

CREATE TABLE receipt (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    reservation_id  BIGINT NOT NULL,
    pdf_url         VARCHAR(255),
    generated_at    DATETIME,
    FOREIGN KEY (reservation_id) REFERENCES reservation(id)
);

-- 회원 1명에게 발급된 쿠폰 1장 (2026-09-07 신규/재설계, 송채현). 웰컴/매장귀책보상/프로모션 3경로,
-- 전부 "회원 1명당 1장" 방식이라 공유 수량 풀(quantityLimit/usedCount) 개념 없음.
CREATE TABLE coupon (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    source                  VARCHAR(30) NOT NULL COMMENT 'WELCOME / STORE_COMPENSATION / PROMOTION',
    issued_to_user_id       BIGINT NOT NULL,
    campaign_id             BIGINT COMMENT 'source=PROMOTION일 때만 값 존재',
    source_reservation_id   BIGINT COMMENT 'source=STORE_COMPENSATION일 때만 값 존재',
    discount_rate           INT NOT NULL COMMENT '정률 할인(%)',
    expires_at              DATETIME NOT NULL,
    used                    TINYINT(1) NOT NULL DEFAULT 0 COMMENT '체크아웃에서 쓰면 true. 매장귀책/일반 취소 시 복구(false), 회원 노쇼는 복구 안 함',
    created_at              DATETIME,
    INDEX idx_coupon_issued_to_user_id (issued_to_user_id),
    FOREIGN KEY (issued_to_user_id) REFERENCES users(id),
    FOREIGN KEY (campaign_id) REFERENCES coupon_campaign(id),
    FOREIGN KEY (source_reservation_id) REFERENCES reservation(id)
);

-- 쿠폰 정책값 — 웰컴/매장귀책보상 할인율. 항상 1행만 존재(id=1), 없으면 서비스가 기본값으로 생성.
CREATE TABLE coupon_policy (
    id                          BIGINT AUTO_INCREMENT PRIMARY KEY,
    welcome_discount_rate       INT NOT NULL,
    compensation_discount_rate  INT NOT NULL,
    updated_at                  DATETIME
);

-- 프로모션 이벤트 쿠폰 캠페인 — 슈퍼어드민이 이벤트마다 생성, 회원이 code를 입력해 발급받음.
CREATE TABLE coupon_campaign (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    name           VARCHAR(100) NOT NULL COMMENT '관리용 이름, 회원 비노출',
    code           VARCHAR(30) NOT NULL COMMENT '회원이 입력하는 코드(대문자 정규화)',
    discount_rate  INT NOT NULL,
    expires_at     DATETIME NOT NULL,
    active         TINYINT(1) NOT NULL DEFAULT 1 COMMENT 'false면 새 발급만 막힘, 기발급 쿠폰은 유효',
    created_at     DATETIME,
    UNIQUE KEY uk_coupon_campaign_code (code)
);

-- ── 3. 리뷰 / 찜 / 알림 ─────────────────────────────────────────────

CREATE TABLE review (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    store_id    BIGINT NOT NULL,
    rating      INT NOT NULL,
    content     VARCHAR(500),
    image_url   VARCHAR(500) COMMENT '사진 리뷰 (2026-08 강노은 추가)',
    edited      TINYINT(1) NOT NULL DEFAULT 0 COMMENT '재작성(덮어쓰기) 시 true',
    created_at  DATETIME,
    INDEX idx_review_user_id (user_id),
    INDEX idx_review_store_id (store_id),
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (store_id) REFERENCES store(id)
);

-- 가게별 AI 리뷰 요약 캐시 (가게당 최대 1행). 담당: 강노은.
CREATE TABLE review_summary (
    id                         BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_id                   BIGINT NOT NULL,
    summary                    VARCHAR(1000) NOT NULL,
    review_count_at_summary    INT NOT NULL COMMENT '마지막 요약 생성 당시 리뷰 개수 — 달라지면 재생성',
    generated_at               DATETIME,
    UNIQUE KEY uk_review_summary_store_id (store_id),
    FOREIGN KEY (store_id) REFERENCES store(id)
);

CREATE TABLE likes (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    store_id    BIGINT NOT NULL,
    created_at  DATETIME,
    UNIQUE KEY uk_likes_user_store (user_id, store_id),
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (store_id) REFERENCES store(id)
);

-- 인앱 알림함 1건. 담당: 강노은. 생성 트리거는 NotificationTriggerScheduler가 주기 스캔.
CREATE TABLE notification (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    type        VARCHAR(30) NOT NULL COMMENT 'LIKE_STORE_OPEN/RESERVATION_*/ADMIN_*/WELCOME_COUPON 등',
    message     VARCHAR(200) NOT NULL,
    link_url    VARCHAR(200) COMMENT '클릭 시 이동할 곳. 없으면 알림함에 머묾',
    source_key  VARCHAR(100) COMMENT '중복 생성 방지용 고유키, 예 "like_open:7:42"',
    is_read     TINYINT(1) NOT NULL DEFAULT 0,
    created_at  DATETIME,
    INDEX idx_notification_user_id (user_id),
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- 사용자 1명당 1행. UserEntity에 컬럼을 얹지 않고 분리(강노은).
CREATE TABLE notification_setting (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id               BIGINT NOT NULL,
    push_enabled          TINYINT(1) NOT NULL DEFAULT 1,
    like_alert_enabled    TINYINT(1) NOT NULL DEFAULT 1,
    updated_at            DATETIME,
    UNIQUE KEY uk_notification_setting_user_id (user_id),
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- ── 4. 게시판 / 문의 / 신고 ──────────────────────────────────────────

-- 슈퍼어드민 공지사항 (WBS 7.0, 송보미). 게시 기간(선택) 지원.
CREATE TABLE notice (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    title             VARCHAR(100) NOT NULL,
    content           VARCHAR(2000) NOT NULL,
    published         TINYINT(1) NOT NULL COMMENT '수동 on/off 스위치',
    publish_start_at  DATE COMMENT 'NULL이면 등록/게시 즉시부터',
    publish_end_at    DATE COMMENT 'NULL이면 무기한',
    created_at        DATETIME,
    updated_at        DATETIME
);

-- 문의 게시판 글. store_id가 있으면 특정 가게 문의, NULL이면 서비스 전체 문의.
CREATE TABLE inquiry (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    store_id        BIGINT COMMENT 'NULL=일반 문의(특정 가게 무관)',
    reservation_id  BIGINT COMMENT '예약 상세 "문의하기"로 접수된 경우 그 예약 (2026-09-08 추가)',
    title           VARCHAR(100) NOT NULL,
    content         VARCHAR(1000) NOT NULL,
    image_url       VARCHAR(500) COMMENT '사진 첨부 (강노은 추가)',
    created_at      DATETIME,
    INDEX idx_inquiry_user_id (user_id),
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (store_id) REFERENCES store(id),
    FOREIGN KEY (reservation_id) REFERENCES reservation(id)
);

-- 문의 글 댓글. 작성자 본인 추가 댓글인지 매장/운영자 답변인지 구분 컬럼 없음(미정, 엔티티 주석 참고).
CREATE TABLE inquiry_comment (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    inquiry_id  BIGINT NOT NULL,
    user_id     BIGINT NOT NULL,
    content     VARCHAR(500) NOT NULL,
    created_at  DATETIME,
    INDEX idx_inquiry_comment_inquiry_id (inquiry_id),
    FOREIGN KEY (inquiry_id) REFERENCES inquiry(id),
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- 신고 접수 (슈퍼어드민 "신고·문의" 화면). 신고 제출 화면(소비자용)이 아직 없어 대상/신고자는
-- FK 대신 스냅샷 문자열 + 선택적 id(있으면 상세로 링크)로 저장한다 — 그래서 일부러 FK를 걸지 않았다.
CREATE TABLE complaint (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    target_name             VARCHAR(100) NOT NULL COMMENT '신고 대상(매장/유저) 이름 스냅샷',
    target_store_id         BIGINT COMMENT '대상이 매장이면 채움. FK 미설정(감사 로그 성격)',
    target_reservation_id   BIGINT COMMENT '예약 상세 "신고하기"로 접수된 경우 그 예약 (2026-09-08 추가). FK 미설정',
    reason                  VARCHAR(100) NOT NULL,
    content                 VARCHAR(1000) NOT NULL,
    reporter_name           VARCHAR(50) NOT NULL,
    reporter_id             BIGINT COMMENT '신고자가 회원이면 채움. FK 미설정',
    status                  VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / RESOLVED',
    admin_reply             VARCHAR(1000),
    created_at              DATETIME,
    resolved_at             DATETIME
);

-- ── 5. 정산 / 리포트 / 뱃지 ──────────────────────────────────────────

CREATE TABLE report (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_id           BIGINT NOT NULL,
    report_date        DATE NOT NULL,
    registered_count   INT,
    sold_count         INT,
    expired_count      INT,
    total_sales        INT,
    total_discount     INT,
    saved_co2          DOUBLE,
    excel_url          VARCHAR(255),
    pdf_url            VARCHAR(255),
    generated_at       DATETIME,
    INDEX idx_report_store_id (store_id),
    INDEX idx_report_date (report_date),
    FOREIGN KEY (store_id) REFERENCES store(id)
);

-- 주간 정산 (2026-09-01 문창호, WBS 2.0 매장 정산). "정산 확정"과 "지급"을 분리한 구조.
CREATE TABLE settlement (
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_id               BIGINT NOT NULL,
    period_start           DATE NOT NULL COMMENT '정산 대상 주간 시작(월요일)',
    period_end             DATE NOT NULL COMMENT '정산 대상 주간 끝(일요일)',
    gross                  BIGINT NOT NULL COMMENT '총 결제액(PAID + 결제 후 취소분)',
    refund                 BIGINT NOT NULL,
    net_amount             BIGINT NOT NULL COMMENT '= gross - refund',
    commission_rate        INT NOT NULL COMMENT '적용 수수료율%(스냅샷)',
    commission             BIGINT NOT NULL,
    week_amount            BIGINT NOT NULL COMMENT '= net_amount - commission',
    carried_in             BIGINT NOT NULL COMMENT '이전 이월분 합산액',
    payout                 BIGINT NOT NULL COMMENT '이번에 실제 지급되는 금액(CARRIED/ROLLED면 0)',
    status                 VARCHAR(20) NOT NULL COMMENT 'PENDING/PAID/CARRIED/ROLLED',
    merged_into_id         BIGINT COMMENT 'status=ROLLED일 때 합산된 정산 id (자기 참조)',
    confirmed_at           DATETIME NOT NULL,
    scheduled_payout_date  DATE NOT NULL COMMENT '확정일 + 영업일 2일',
    paid_at                DATETIME,
    transfer_memo          VARCHAR(200),
    created_at             DATETIME,
    UNIQUE KEY uk_settlement_store_period (store_id, period_start),
    FOREIGN KEY (store_id) REFERENCES store(id),
    FOREIGN KEY (merged_into_id) REFERENCES settlement(id)
);

-- 사용자가 실제로 획득(해금)한 뱃지. 한 번 딴 뱃지는 조건이 다시 거짓이 돼도 영구 유지.
CREATE TABLE user_badge (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    badge_code  VARCHAR(50) NOT NULL,
    earned_at   DATETIME NOT NULL,
    notified    TINYINT(1) NOT NULL COMMENT '"새 뱃지 획득" 토스트를 이미 보여줬는지',
    UNIQUE KEY uk_user_badge_user_code (user_id, badge_code),
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- =====================================================================
-- 기리기리 ERD 초안용 DDL
-- =====================================================================
-- 용도: 이 파일 자체를 실행하려는 게 아니라(테이블은 ddl-auto=update로 자동 생성됨),
--       ERD Editor(vuerd) 플러그인의 "SQL DDL Import" 기능에 붙여넣어서
--       도표를 자동으로 그리기 위한 용도다.
--
-- 사용법 (IntelliJ ERD Editor 플러그인):
--   1. docs/ 폴더에 새 파일 생성: 기리기리.vuerd.json (빈 파일)
--   2. 그 파일을 열면 캔버스가 뜬다
--   3. 캔버스 우클릭 → SQL → SQL DDL Import (또는 상단 메뉴의 SQL 아이콘)
--   4. 이 파일 내용을 통째로 붙여넣고 Run/Create
--   5. 테이블이 자동 배치되면, 화면에 안 보이는 FK 관계선은 컬럼을 드래그해서 수동으로 연결
--      (버전에 따라 FOREIGN KEY 제약을 보고 자동으로 관계선을 그려주기도 함)
--   6. 다 그렸으면 Cmd+S로 저장 → 그 결과가 기리기리.vuerd.json에 담긴다 → git commit
--
-- 주의: domain/entity/*.java는 아직 FK를 plain Long 컬럼으로만 두고 있고
--       (JPA @ManyToOne 매핑 없음), 실제 DB에는 FOREIGN KEY 제약이 없다.
--       아래 FOREIGN KEY는 "설계 의도"를 ERD에 시각화하기 위한 것이지,
--       실제 스키마에 반영하라는 뜻이 아니다. ddl-auto=update는 이 파일을 보지 않는다.
--
-- 미확정 지점: STORE.owner_id → USERS.id 관계 (안A: Store 독립 계정 / 안B: User가 Store 소유)
--             결정 전까지 이 컬럼은 참고용으로만 남겨둠.
--
-- 일정 참고 (2026-08-21 WBS 갱신): DB 설계·ERD 확정은 08/19~08/21 (송보미, 송채현) —
-- CLAUDE.md "1.0 기획 & 설계" 참고. 공지사항/문의/알림함/절약목표/챗봇 등 WBS에 새로 등장한
-- 기능(3.0/4.0/6.0/6.5/7.0 단계)은 아직 테이블 스펙이 안 나와서 이 파일엔 반영하지 않았다 —
-- 각 기능 착수 시 담당자가 엔티티를 추가하면서 이 파일도 함께 갱신할 것.
-- =====================================================================

CREATE TABLE users (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    login_id        VARCHAR(50) UNIQUE,
    password        VARCHAR(100),
    oauth_provider  VARCHAR(20)  COMMENT 'kakao / naver / null(일반가입)',
    oauth_id        VARCHAR(100),
    role            VARCHAR(20) NOT NULL COMMENT 'USER / ADMIN',
    nickname        VARCHAR(30),
    latitude        DOUBLE,
    longitude       DOUBLE,
    created_at      DATETIME,
    updated_at      DATETIME
);

-- 변경됨 — 왜: Store 자체 로그인 계정 모델(안A, login_id/password)과 "User가 storeId로 Store를
-- 소유"하는 모델(안B, owner_id)이 섞여있다는 지적으로 안B 확정. login_id/password는 실제로 어디서도
-- 참조 안 되던 죽은 컬럼이라 제거(로그인은 전부 users 쪽 OAuth2/이메일로만 이뤄짐). owner_id가 진짜
-- 관계(users.id 참조)로 확정.
CREATE TABLE store (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_name       VARCHAR(50) NOT NULL,
    category         VARCHAR(30),
    address          VARCHAR(200),
    latitude         DOUBLE,
    longitude        DOUBLE,
    operating_hours  VARCHAR(100),
    prep_time_minutes INT COMMENT '채현 기본 준비시간(분). NULL이면 매장이 아직 설정 전 (2026-08-21 추가)',
    last_pickup_time TIME COMMENT '채현 마지막 픽업시간. NULL이면 매장이 아직 설정 전 (2026-08-21 추가)',
    rescue_goal_percent INT DEFAULT 70 COMMENT '대시보드 구제율 목표치(%). 연필 아이콘으로 점주가 직접 수정 (문창호 추가)',
    pos_provider     VARCHAR(30) COMMENT 'POS 연동사(okpos/posbank/unionpos/etc). NULL이면 미연동 (2026-08-27 문창호 추가)',
    pos_store_code   VARCHAR(50) COMMENT 'POS 매장 코드 (2026-08-27 문창호 추가)',
    pos_connected_at DATETIME COMMENT 'POS 연동 시각 (2026-08-27 문창호 추가)',
    pos_last_sync_at DATETIME COMMENT 'POS 마지막 동기화 시각 (2026-08-27 문창호 추가)',
    pos_draft_prompt_time TIME COMMENT '매일 이 시각에 POS 재고 스냅샷으로 "오늘의 구제" 초안 자동 생성. NULL이면 안 함 (B안, 2026-08-27 문창호)',
    bank_name        VARCHAR(30) COMMENT '정산 입금 계좌 은행 (2026-09-01 문창호, WBS 2.0 매장 정산)',
    bank_account     VARCHAR(40) COMMENT '정산 입금 계좌번호',
    account_holder   VARCHAR(40) COMMENT '정산 입금 계좌 예금주',
    role             VARCHAR(20) NOT NULL COMMENT '= OWNER. 보류: users.role과 중복 정보라 실사용 여부 확인 필요',
    owner_id         BIGINT NOT NULL COMMENT 'users.id 참조 (안B 확정) — 이 매장을 소유한 점주(role=OWNER) 계정',
    created_at       DATETIME,
    updated_at       DATETIME,
    FOREIGN KEY (owner_id) REFERENCES users(id)
);

-- "오늘의 구제 자동 등록" 템플릿 (2026-08-26 문창호 추가, WBS엔 없는 신규 기능)
-- 사장님이 한 번 등록 → ListingDraftScheduler가 매일 promptTime에 product(status='draft') 초안 생성 + 알림
CREATE TABLE listing_template (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_id         BIGINT NOT NULL,
    name             VARCHAR(100) NOT NULL,
    original_price   INT NOT NULL,
    image_url        VARCHAR(255),
    description      VARCHAR(500),
    default_quantity INT NOT NULL,
    weekdays         VARCHAR(20) NOT NULL COMMENT 'ISO 요일 CSV, 1=월 … 7=일. 예 "1,2,3,4,5"',
    prompt_time      TIME NOT NULL COMMENT '매일 이 시각에 초안 생성 + 알림',
    active           TINYINT(1) NOT NULL DEFAULT 1,
    created_at       DATETIME,
    updated_at       DATETIME,
    FOREIGN KEY (store_id) REFERENCES store(id)
);

-- POS 카탈로그 메뉴 (2026-08-27 문창호 추가, "POS json 카탈로그 연동 (가정)")
-- 매장의 영속 메뉴 목록 + 현재 재고. POS가 재고를 push하면(B안) 마감 무렵 이 재고로 초안 자동 생성.
CREATE TABLE menu_item (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_id         BIGINT NOT NULL,
    pos_sku          VARCHAR(50) COMMENT 'POS 원본 식별자. upsert 기준',
    name             VARCHAR(100) NOT NULL,
    original_price   INT NOT NULL,
    image_url        VARCHAR(255),
    stock_quantity   INT COMMENT 'POS가 push한 현재 재고. NULL이면 재고 정보 없음 (2026-08-27 문창호)',
    app_sale_enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '마감 무렵 이 메뉴를 초안으로 자동 생성할지 (2026-08-27 문창호)',
    discount_rate    INT COMMENT '앱 판매 시 할인율(%). NULL이면 마감시간 기준 자동(20/30/50). 값이 있어도 자동값보다 낮으면 자동값으로 올려서 적용(덜 깎기 금지) (2026-08-27 문창호)',
    app_sale_quantity INT COMMENT '앱에 올릴 최대 수량. NULL이면 POS 재고 전량. 초안 수량 = MIN(재고, 이 값) (2026-08-27 문창호)',
    created_at       DATETIME,
    updated_at       DATETIME,
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
    status              VARCHAR(20) NOT NULL COMMENT 'draft / active / sold / expired / skipped (draft=자동 초안, skipped="오늘 안 함" 처리해 재생성 방지 — 2026-08-26~27 문창호)',
    template_id         BIGINT COMMENT '템플릿 방식 초안이면 listing_template.id (2026-08-26 문창호)',
    menu_item_id        BIGINT COMMENT 'POS 재고 스냅샷 방식 초안이면 menu_item.id (2026-08-27 문창호)',
    registered_at       DATETIME,
    updated_at          DATETIME COMMENT '상품 수정 + 재고 변동(예약/취소)에도 갱신됨 (2026-08-25 추가)',
    FOREIGN KEY (store_id) REFERENCES store(id),
    FOREIGN KEY (template_id) REFERENCES listing_template(id),
    FOREIGN KEY (menu_item_id) REFERENCES menu_item(id)
);

CREATE TABLE reservation (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id            BIGINT NOT NULL,
    product_id         BIGINT NOT NULL,
    product_name       VARCHAR(100) COMMENT '주문 당시 상품명 스냅샷, 문창호 추가',
    store_id           BIGINT NOT NULL,
    reserved_quantity  INT NOT NULL,
    total_price        INT NOT NULL,
    pickup_time        DATETIME,
    pickup_code        VARCHAR(30),
    status             VARCHAR(20) NOT NULL COMMENT 'pending / confirmed / ready / picked / cancelled / noshowed (ready 채현 2026-08-21 추가: 매장 수락 완료)',
    reserved_at        DATETIME,
    accepted_at        DATETIME COMMENT '채현 매장이 수락한 시각 (2026-08-21 추가)',
    picked_at          DATETIME,
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (product_id) REFERENCES product(id),
    FOREIGN KEY (store_id) REFERENCES store(id)
);

CREATE TABLE review (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    store_id    BIGINT NOT NULL,
    rating      INT NOT NULL,
    content     VARCHAR(500),
    created_at  DATETIME,
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (store_id) REFERENCES store(id)
);

CREATE TABLE likes (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    store_id    BIGINT NOT NULL,
    created_at  DATETIME,
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (store_id) REFERENCES store(id)
);

CREATE TABLE receipt (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    reservation_id  BIGINT NOT NULL,
    pdf_url         VARCHAR(255),
    generated_at    DATETIME,
    FOREIGN KEY (reservation_id) REFERENCES reservation(id)
);

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
    FOREIGN KEY (store_id) REFERENCES store(id)
);

-- 추가됨 (2026-08-27, 송보미) — 슈퍼어드민 공지사항 관리 (WBS 7.0).
-- 변경됨 (2026-09-01, 송보미) — "게시 기간을 정하고 싶다"는 요청으로 publish_start_at/publish_end_at
-- (둘 다 nullable, null이면 즉시·무제한) 추가. published는 그대로 수동 on/off("게시글 내리기") 스위치.
CREATE TABLE notice (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    title             VARCHAR(100) NOT NULL,
    content           VARCHAR(2000) NOT NULL,
    published         BOOLEAN NOT NULL,
    publish_start_at  DATE,
    publish_end_at    DATE,
    created_at        DATETIME,
    updated_at        DATETIME
);

-- 추가됨 (2026-09-01, 송보미) — 슈퍼어드민 "신고 · 문의" 화면의 신고 접수 탭(WBS 7.0). 원래 정적
-- 데모였는데 매장 문의/유저 문의처럼 클릭 → 상세 → 답변이 되게 해달라는 요청으로 테이블을 만들었다.
-- "report"는 이미 매장 판매/폐기 리포트가 쓰고 있어 이름을 complaint로 지었다. 신고 제출 화면(소비자용)이
-- 아직 없어 신고자는 FK 대신 스냅샷 문자열로 저장.
CREATE TABLE complaint (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    target_name      VARCHAR(100) NOT NULL,
    target_store_id  BIGINT,
    reason           VARCHAR(100) NOT NULL,
    content          VARCHAR(1000) NOT NULL,
    reporter_name    VARCHAR(50) NOT NULL,
    reporter_id      BIGINT,
    status           VARCHAR(20) NOT NULL,
    admin_reply      VARCHAR(1000),
    created_at       DATETIME,
    resolved_at      DATETIME
);

-- 주간 정산 (2026-09-01 문창호, WBS 2.0 "매장 정산")
-- 매주 월 00:00 SettlementScheduler가 지난 주(월~일) 매장별 정산액을 계산해 1건씩 생성(status=PENDING).
-- 슈퍼어드민(/superadmin/settlements)이 이체 목록(Excel) 받아 은행 대량이체 후 지급 완료(PAID) 처리.
-- 정산액이 10,000원 미만이면 CARRIED로 두고 다음 주 정산에 합산(합산되면 ROLLED).
CREATE TABLE settlement (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_id              BIGINT NOT NULL,
    period_start          DATE NOT NULL COMMENT '정산 대상 주간 시작 (월요일)',
    period_end            DATE NOT NULL COMMENT '정산 대상 주간 끝 (일요일)',
    gross                 BIGINT NOT NULL COMMENT '총 결제액 (PAID + 결제 후 취소분)',
    refund                BIGINT NOT NULL COMMENT '환불 차감',
    net_amount            BIGINT NOT NULL COMMENT '순 결제액 = gross - refund',
    commission_rate       INT NOT NULL COMMENT '적용 수수료율 % (스냅샷)',
    commission            BIGINT NOT NULL COMMENT '플랫폼 수수료',
    week_amount           BIGINT NOT NULL COMMENT '이번 주 순수 정산분 = net - commission',
    carried_in            BIGINT NOT NULL COMMENT '이전 이월분 합산액',
    payout                BIGINT NOT NULL COMMENT '이번에 실제 지급되는 금액 (CARRIED/ROLLED면 0)',
    status                VARCHAR(20) NOT NULL COMMENT 'PENDING(지급 대기) / PAID(지급 완료) / CARRIED(이월) / ROLLED(이월분 합산됨)',
    merged_into_id        BIGINT COMMENT 'status=ROLLED일 때 어느 정산에 합산됐는지',
    confirmed_at          DATETIME NOT NULL COMMENT '정산 확정 시각',
    scheduled_payout_date DATE NOT NULL COMMENT '지급 예정일 (확정일 + 영업일 2일)',
    paid_at               DATETIME COMMENT '실제 지급 완료 시각',
    transfer_memo         VARCHAR(200) COMMENT '이체 확인 메모 (슈퍼어드민 입력)',
    created_at            DATETIME,
    UNIQUE KEY uk_settlement_store_period (store_id, period_start),
    FOREIGN KEY (store_id) REFERENCES store(id)
);

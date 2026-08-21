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

CREATE TABLE store (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    login_id         VARCHAR(50) UNIQUE,
    password         VARCHAR(100),
    store_name       VARCHAR(50) NOT NULL,
    category         VARCHAR(30),
    address          VARCHAR(200),
    latitude         DOUBLE,
    longitude        DOUBLE,
    operating_hours  VARCHAR(100),
    role             VARCHAR(20) NOT NULL COMMENT '= ADMIN',
    owner_id         BIGINT COMMENT '미확정: users.id 참조 (안A/안B 결정 전)',
    created_at       DATETIME,
    updated_at       DATETIME,
    FOREIGN KEY (owner_id) REFERENCES users(id)
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
    status              VARCHAR(20) NOT NULL COMMENT 'active / sold / expired',
    registered_at       DATETIME,
    FOREIGN KEY (store_id) REFERENCES store(id)
);

CREATE TABLE reservation (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id            BIGINT NOT NULL,
    product_id         BIGINT NOT NULL,
    store_id           BIGINT NOT NULL,
    reserved_quantity  INT NOT NULL,
    total_price        INT NOT NULL,
    pickup_time        DATETIME,
    pickup_code        VARCHAR(30),
    status             VARCHAR(20) NOT NULL COMMENT 'pending / confirmed / picked / cancelled / noshowed',
    reserved_at        DATETIME,
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

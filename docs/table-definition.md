# 테이블 정의서 (テーブル定義書) — 기리기리(끼리끼리)

- 작성 기준: `src/main/java/net/dsa/girigiri/domain/entity/*.java` (2026-08-27 갱신, 12개 엔티티 — `notice` 추가)
- `docs/schema.sql`(ERD 초안 DDL)에는 아직 `payment` / `inquiry` / `inquiry_comment` 3개 테이블이 반영되어 있지 않다(`notice`는 반영됨). 이 문서 확정 후 `docs/schema.sql`도 함께 갱신할 것.
- CLAUDE.md 엔티티 설계(예상)에 있던 `Category`, 신고 등은 아직 코드에 테이블 스펙이 나오지 않아 이 문서에서 제외했다. 해당 기능 착수 시(WBS 3.0/6.0/7.0) 담당자가 엔티티를 추가하면서 이 문서도 함께 갱신.
- NN=NOT NULL / PK=기본키 / FK=외래키 / UK=고유키(UNIQUE) / IDX=인덱스

---

## 1. users — 会員

서비스 이용자. `role`로 일반회원(USER) / 점주(OWNER) / 운영자(ADMIN)를 구분한다. 자체 회원가입 없이 OAuth2(카카오/네이버/구글) + 이메일·비밀번호 가입만 지원하며, 이메일 가입도 `oauth_provider="email"`, `oauth_id=이메일값`으로 저장해 조회 로직을 통일한다.

| No | 컬럼명 (물리) | 논리명 | 데이터 타입 | NN | PK | FK | UK | IDX | 기본값 | 설명 · 제약조건 |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | id | 회원번호 | BIGINT | O | O | | | | AUTO_INCREMENT | 主キー |
| 2 | oauth_provider | 인증 제공자 | VARCHAR(20) | O | | | O* | O* | | kakao / naver / google / email |
| 3 | oauth_id | 인증 식별자 | VARCHAR(100) | O | | | O* | O* | | provider가 email이면 이메일 값 자체가 들어감 |
| 4 | password | 비밀번호 | VARCHAR(100) | | | | | | | 이메일 가입자만 값 존재(BCrypt). 소셜 계정은 NULL |
| 5 | role | 권한 | VARCHAR(20) | O | | | | | 'USER' | USER / OWNER / ADMIN |
| 6 | status | 계정 상태 | VARCHAR(20) | | | | | | 'ACTIVE' | ACTIVE / SUSPENDED (슈퍼어드민 회원 정지용) |
| 7 | nickname | 닉네임 | VARCHAR(30) | | | | | | | 화면 표시용 |
| 8 | email | 이메일 | VARCHAR(100) | | | | | | | 표시(마스킹)용. 로그인 식별자는 oauth_id이며 이 컬럼 자체엔 UK 없음 |
| 9 | region | 활동 지역 | VARCHAR(100) | | | | | | | 자유 텍스트. 위경도 좌표 연동 전 임시 항목 |
| 10 | profile_completed | 프로필 완성 여부 | BOOLEAN | O | | | | | false | 최초 소셜 로그인 직후 false → 회원가입 완료 화면 진입 여부 판단 |
| 11 | latitude | 위도 | DOUBLE | | | | | | | |
| 12 | longitude | 경도 | DOUBLE | | | | | | | |
| 13 | created_at | 등록일시 | DATETIME | O | | | | | | 공통 컬럼 |
| 14 | updated_at | 수정일시 | DATETIME | | | | | | | 공통 컬럼 |

\* UK/IDX는 `(oauth_provider, oauth_id)` **복합** 유니크·인덱스 — `findByOauthProviderAndOauthId` 조회에 사용.

---

## 2. store — 店舗

점주(OWNER) 계정 겸 매장 정보. `owner_id`로 `users.id`를 참조하도록 되어 있으나 **관계 모델이 미확정**이다.

> ⚠️ **미확정 사항 (ERD 확정 전 결정 필요)**: CLAUDE.md 스펙상 Store가 자체 `login_id`/`password`/`role=ADMIN`을 갖는 "독립 계정" 모델과, 세션 설계(`{ userId, role, viewMode, storeId }`)가 암시하는 "User가 storeId로 Store를 소유"하는 모델이 서로 어긋난다(엔티티 코드 TODO 주석 참고). 현재 코드는 두 모델이 섞여 있다(`login_id`/`password`는 레거시로 남아있고, 실제로는 `owner_id`로 users를 참조). **안A(Store 독립 계정) vs 안B(User가 Store 소유) 중 하나로 정리하고 이 문서를 갱신할 것.**

| No | 컬럼명 (물리) | 논리명 | 데이터 타입 | NN | PK | FK | UK | IDX | 기본값 | 설명 · 제약조건 |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | id | 매장번호 | BIGINT | O | O | | | | AUTO_INCREMENT | 主キー |
| 2 | login_id | 로그인ID | VARCHAR(50) | | | | O | O | | 레거시 컬럼(안A용). 안B 채택 시 제거 검토 |
| 3 | password | 비밀번호 | VARCHAR(100) | | | | | | | 레거시 컬럼(안A용). 안B 채택 시 제거 검토 |
| 4 | store_name | 매장명 | VARCHAR(50) | O | | | | | | |
| 5 | category | 카테고리 | VARCHAR(30) | | | | | O | | 베이커리/반찬/도시락/카페 등, 탐색 화면 필터 조건 |
| 6 | address | 주소 | VARCHAR(200) | | | | | | | |
| 7 | latitude | 위도 | DOUBLE | | | | | | | |
| 8 | longitude | 경도 | DOUBLE | | | | | | | |
| 9 | operating_hours | 영업시간 | VARCHAR(100) | | | | | | | |
| 10 | prep_time_minutes | 준비시간(분) | INT | | | | | | 20 | NULL이면 매장 설정 전(항상 주문 가능으로 취급) |
| 11 | last_pickup_time | 마지막 픽업시간 | TIME | | | | | | | 이 시간 이후 당일 주문/픽업 마감 |
| 12 | role | 권한 | VARCHAR(20) | O | | | | | | = 'OWNER' |
| 13 | business_number | 사업자번호 | VARCHAR(30) | | | | | | | 입점 신청 시 검증용 |
| 14 | phone | 연락처 | VARCHAR(30) | | | | | | | |
| 15 | approval_status | 입점 승인 상태 | VARCHAR(20) | | | | | O | 'PENDING' | PENDING / APPROVED / REJECTED (`findByApprovalStatus`) |
| 16 | owner_id | 소유 회원번호 | BIGINT | | | O | | O | | users.id 참조 (**관계 모델 미확정**, `findByOwnerId`) |
| 17 | created_at | 등록일시 | DATETIME | O | | | | | | 공통 컬럼 |
| 18 | updated_at | 수정일시 | DATETIME | | | | | | | 공통 컬럼 |

---

## 3. product — 商品

매장이 등록한 마감세일 상품(재고).

| No | 컬럼명 (물리) | 논리명 | 데이터 타입 | NN | PK | FK | UK | IDX | 기본값 | 설명 · 제약조건 |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | id | 상품번호 | BIGINT | O | O | | | | AUTO_INCREMENT | 主キー |
| 2 | store_id | 매장번호 | BIGINT | O | | O | | O | | store.id 참조 (`findByStoreId`, 점주 대시보드) |
| 3 | name | 상품명 | VARCHAR(100) | O | | | | | | |
| 4 | original_price | 원가 | INT | O | | | | | | 원(KRW) 정수 단위. ※ 소수점 필요 시 DECIMAL 재검토 |
| 5 | discounted_price | 할인가 | INT | O | | | | | | |
| 6 | quantity | 등록 수량 | INT | O | | | | | | |
| 7 | remaining_quantity | 잔여 수량 | INT | O | | | | | | 0 이상, quantity 이하 |
| 8 | image_url | 이미지 URL | VARCHAR(255) | | | | | | | |
| 9 | description | 설명 | VARCHAR(500) | | | | | | | |
| 10 | status | 상태 | VARCHAR(20) | O | | | | O | | active / sold / expired |
| 11 | registered_at | 등록일시 | DATETIME | O | | | | | | 공통 컬럼(created_at 역할) |
| 12 | updated_at | 수정일시 | DATETIME | | | | | | | 공통 컬럼 (2026-08-25 추가) |

> 비고: `updated_at`은 상품 정보 수정뿐 아니라 예약/취소로 인한 재고 변동(`remaining_quantity`)에도 갱신된다 — "사장님이 상품 정보를 마지막으로 수정한 시각" 전용이 아니라 목록 정렬·장애 추적용 범용 컬럼이다. 상품 수정 이력을 별도로 추적하려면(WBS 3.0 김태훈) 전용 컬럼/이력 테이블을 따로 검토할 것.

---

## 4. reservation — 予約履歴

회원이 상품을 예약(선결제)한 이력. 1행 = 1예약, 상태를 컬럼으로 관리(이력 삭제 없음).

| No | 컬럼명 (물리) | 논리명 | 데이터 타입 | NN | PK | FK | UK | IDX | 기본값 | 설명 · 제약조건 |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | id | 예약번호 | BIGINT | O | O | | | | AUTO_INCREMENT | 主キー |
| 2 | user_id | 회원번호 | BIGINT | O | | O | | O | | users.id 참조 |
| 3 | product_id | 상품번호 | BIGINT | O | | O | | O | | product.id 참조 |
| 4 | product_name | 상품명(스냅샷) | VARCHAR(100) | | | | | | | 의도된 비정규화 — 주문 이후 상품명이 바뀌거나 상품이 삭제돼도 과거 거래 기록이 깨지지 않도록 주문 시점 스냅샷 저장 |
| 5 | store_id | 매장번호 | BIGINT | O | | O | | O | | store.id 참조 |
| 6 | reserved_quantity | 예약 수량 | INT | O | | | | | | |
| 7 | total_price | 결제 총액 | INT | O | | | | | | |
| 8 | pickup_time | 픽업 시각 | DATETIME | | | | | O | | 노쇼 판정·오늘 픽업 목록 조회에 사용 |
| 9 | pickup_code | 픽업 코드 | VARCHAR(30) | | | | O | O | | QR/현장 확인용 (`findByPickupCode`). 발급 시 고유값 보장 필요 |
| 10 | status | 상태 | VARCHAR(20) | O | | | | O | | pending → confirmed → ready → picked / cancelled / noshowed |
| 11 | reserved_at | 예약일시 | DATETIME | O | | | | | | 공통 컬럼(created_at 역할) |
| 12 | accepted_at | 매장 수락일시 | DATETIME | | | | | | | confirmed→ready 전환 시점 |
| 13 | picked_at | 픽업완료일시 | DATETIME | | | | | | | |
| 14 | cancelled_by | 취소 주체 | VARCHAR(10) | | | | | | | USER / STORE. status=cancelled일 때만 값 존재 |
| 15 | cancel_reason | 취소 사유 | VARCHAR(255) | | | | | | | STORE 취소 시 사유 텍스트 |

---

## 5. review — レビュー

| No | 컬럼명 (물리) | 논리명 | 데이터 타입 | NN | PK | FK | UK | IDX | 기본값 | 설명 · 제약조건 |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | id | 리뷰번호 | BIGINT | O | O | | | | AUTO_INCREMENT | 主キー |
| 2 | user_id | 회원번호 | BIGINT | O | | O | | O | | users.id 참조 |
| 3 | store_id | 매장번호 | BIGINT | O | | O | | O | | store.id 참조 |
| 4 | rating | 별점 | INT | O | | | | | | |
| 5 | content | 내용 | VARCHAR(500) | | | | | | | |
| 6 | edited | 수정 여부 | BOOLEAN | O | | | | | false | 최초 작성 후 재작성(덮어쓰기) 시 true — 목록에 "수정됨" 표시용 |
| 7 | created_at | 작성일시 | DATETIME | O | | | | | | 공통 컬럼 |

> ⚠️ **검토 필요**: 서비스 로직상 "회원 1명당 매장 1곳에 리뷰 1건"으로 덮어쓰기 하는 것으로 보이는데(`edited` 플래그 존재), DB 레벨에 `(user_id, store_id)` 복합 UK가 없다. 정책이 맞다면 UK 추가 권장.

---

## 6. likes — 찜하기

| No | 컬럼명 (물리) | 논리명 | 데이터 타입 | NN | PK | FK | UK | IDX | 기본값 | 설명 · 제약조건 |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | id | 찜번호 | BIGINT | O | O | | | | AUTO_INCREMENT | 主キー |
| 2 | user_id | 회원번호 | BIGINT | O | | O | O* | O | | users.id 참조 |
| 3 | store_id | 매장번호 | BIGINT | O | | O | O* | O | | store.id 참조 |
| 4 | created_at | 찜일시 | DATETIME | O | | | | | | 공통 컬럼 |

\* `(user_id, store_id)` 복합 UK 권장 — 현재는 앱 레벨(LikeService)에서만 중복을 걸러내고 있어(기존 찜 삭제 후 재삽입) DB 제약이 없다. 동시 요청 시 중복 행이 생길 수 있으니 UK 추가 필요.

---

## 7. receipt — 領収書

| No | 컬럼명 (물리) | 논리명 | 데이터 타입 | NN | PK | FK | UK | IDX | 기본값 | 설명 · 제약조건 |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | id | 영수증번호 | BIGINT | O | O | | | | AUTO_INCREMENT | 主キー |
| 2 | reservation_id | 예약번호 | BIGINT | O | | O | O | O | | reservation.id 참조. 1예약당 1영수증(`findByReservationId`) |
| 3 | pdf_url | PDF 경로 | VARCHAR(255) | | | | | | | |
| 4 | generated_at | 생성일시 | DATETIME | O | | | | | | 공통 컬럼(created_at 역할) |

---

## 8. payment — 決済

PortOne(아임포트) 결제 처리 이력. `docs/schema.sql`에는 아직 미반영.

| No | 컬럼명 (물리) | 논리명 | 데이터 타입 | NN | PK | FK | UK | IDX | 기본값 | 설명 · 제약조건 |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | id | 결제번호 | BIGINT | O | O | | | | AUTO_INCREMENT | 主キー |
| 2 | reservation_id | 예약번호 | BIGINT | O | | O | O | O | | reservation.id 참조. 1예약당 1결제(`findByReservationId`) |
| 3 | merchant_uid | 가맹점 주문번호 | VARCHAR(50) | O | | | O | O | | 우리 서버가 생성해 PortOne에 전달(`findByMerchantUid`) |
| 4 | imp_uid | PortOne 결제고유번호 | VARCHAR(50) | | | | | | | 결제 승인 후 채워짐 |
| 5 | amount | 결제금액 | INT | O | | | | | | |
| 6 | pay_method | 결제수단 | VARCHAR(20) | | | | | | | card / kakaopay / naverpay 등 |
| 7 | pay_status | 결제상태 | VARCHAR(20) | O | | | | | | ready / paid / failed / cancelled |
| 8 | fail_reason | 실패사유 | VARCHAR(255) | | | | | | | |
| 9 | paid_at | 결제완료일시 | DATETIME | | | | | | | |
| 10 | requested_at | 결제요청일시 | DATETIME | O | | | | | | 공통 컬럼(created_at 역할) |

---

## 9. report — レポート

매장별 일간 판매·폐기 리포트.

| No | 컬럼명 (물리) | 논리명 | 데이터 타입 | NN | PK | FK | UK | IDX | 기본값 | 설명 · 제약조건 |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | id | 리포트번호 | BIGINT | O | O | | | | AUTO_INCREMENT | 主キー |
| 2 | store_id | 매장번호 | BIGINT | O | | O | | O* | | store.id 참조 |
| 3 | report_date | 리포트 일자 | DATE | O | | | | O* | | |
| 4 | registered_count | 등록 수량 | INT | | | | | | | |
| 5 | sold_count | 판매 수량 | INT | | | | | | | |
| 6 | expired_count | 폐기 수량 | INT | | | | | | | |
| 7 | total_sales | 매출액 | INT | | | | | | | |
| 8 | total_discount | 할인액 | INT | | | | | | | |
| 9 | saved_co2 | CO₂ 절감량 | DOUBLE | | | | | | | |
| 10 | excel_url | Excel 경로 | VARCHAR(255) | | | | | | | |
| 11 | pdf_url | PDF 경로 | VARCHAR(255) | | | | | | | |
| 12 | generated_at | 생성일시 | DATETIME | O | | | | | | 공통 컬럼(created_at 역할) |

\* `(store_id, report_date)` 복합 UK/IDX 권장 — 매장당 일자별 리포트는 1건이어야 함.

---

## 10. inquiry — 問い合わせ

문의 게시판 글. `store_id`가 있으면 특정 매장 문의, NULL이면 서비스 전체 문의.

| No | 컬럼명 (물리) | 논리명 | 데이터 타입 | NN | PK | FK | UK | IDX | 기본값 | 설명 · 제약조건 |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | id | 문의번호 | BIGINT | O | O | | | | AUTO_INCREMENT | 主キー |
| 2 | user_id | 작성자 회원번호 | BIGINT | O | | O | | O | | users.id 참조 |
| 3 | store_id | 매장번호 | BIGINT | | | O | | O | | store.id 참조. NULL = 서비스 전체 문의 |
| 4 | title | 제목 | VARCHAR(100) | O | | | | | | |
| 5 | content | 내용 | VARCHAR(1000) | O | | | | | | |
| 6 | created_at | 작성일시 | DATETIME | O | | | | | | 공통 컬럼 |

---

## 11. inquiry_comment — 問い合わせコメント

문의 글에 달리는 댓글(작성자 추가 설명 또는 매장/운영자 답변).

| No | 컬럼명 (물리) | 논리명 | 데이터 타입 | NN | PK | FK | UK | IDX | 기본값 | 설명 · 제약조건 |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | id | 댓글번호 | BIGINT | O | O | | | | AUTO_INCREMENT | 主キー |
| 2 | inquiry_id | 문의번호 | BIGINT | O | | O | | O | | inquiry.id 참조 |
| 3 | user_id | 작성자 회원번호 | BIGINT | O | | O | | | | users.id 참조. 문의 작성자 본인/매장/운영자 구분 컬럼 없음(미정) |
| 4 | content | 내용 | VARCHAR(500) | O | | | | | | |
| 5 | created_at | 작성일시 | DATETIME | O | | | | | | 공통 컬럼 |

---

## 12. notice — お知らせ

슈퍼어드민이 작성하는 공지사항 (WBS 7.0, 송보미 담당). 수정/삭제 기능은 아직 없음.

| No | 컬럼명 (물리) | 논리명 | 데이터 타입 | NN | PK | FK | UK | IDX | 기본값 | 설명 · 제약조건 |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | id | 공지번호 | BIGINT | O | O | | | | AUTO_INCREMENT | 主キー |
| 2 | title | 제목 | VARCHAR(100) | O | | | | | | |
| 3 | content | 내용 | VARCHAR(2000) | O | | | | | | |
| 4 | published | 게시 여부 | BOOLEAN | O | | | | | | 현재는 등록=즉시 게시(true)만 지원 |
| 5 | created_at | 등록일시 | DATETIME | O | | | | | | 공통 컬럼 |
| 6 | updated_at | 수정일시 | DATETIME | | | | | | | 공통 컬럼 (수정 기능 생기면 사용) |

---

## 테이블 목록 (요약)

| No | 테이블명 (물리) | 논리명 | 설명 | 주요 관계 | 담당(WBS) |
|---|---|---|---|---|---|
| 1 | users | 会員 | 일반회원/점주/운영자 통합 계정 (role로 구분) | 1:N → store(owner), reservation, review, likes, inquiry, inquiry_comment | 문창호(인증) / 송보미(DB) |
| 2 | store | 店舗 | 점주 매장 정보, 입점 승인 상태 관리 | N:1 → users(owner, 미확정) · 1:N → product, reservation, review, likes, report, inquiry | 김태훈(정보관리) / 문창호(라우팅) |
| 3 | product | 商品 | 매장이 등록한 마감세일 상품(재고) | N:1 → store · 1:N → reservation | 김태훈 |
| 4 | reservation | 予約履歴 | 상품 예약·픽업 이력 (1건=1행, 상태 관리) | N:1 → users, product, store · 1:1 → receipt, payment | 송채현 |
| 5 | review | レビュー | 매장 리뷰·별점 | N:1 → users, store | 강노은 |
| 6 | likes | お気に入り | 관심 매장 찜하기 | N:1 → users, store | 강노은 |
| 7 | receipt | 領収書 | 결제 영수증 PDF 기록 | 1:1 → reservation | 송채현 |
| 8 | payment | 決済 | PortOne 결제 요청·승인 이력 | 1:1 → reservation | 송채현 |
| 9 | report | レポート | 매장별 일간 판매·폐기 리포트 | N:1 → store | 문창호 |
| 10 | inquiry | 問い合わせ | 문의 게시판 글 | N:1 → users, store(nullable) · 1:N → inquiry_comment | 강노은(작성) / 김태훈(답변) |
| 11 | inquiry_comment | 問い合わせコメント | 문의 댓글/답변 | N:1 → inquiry, users | 강노은 / 김태훈 |
| 12 | notice | お知らせ | 슈퍼어드민 공지사항 | (관계 없음, 독립 테이블) | 송보미 |

---

## 확정 전 검토 필요 사항 (요약)

1. **store.owner_id 관계 모델** — 안A(Store 독립 계정, `login_id`/`password`/`role=ADMIN` 사용) vs 안B(User가 storeId로 Store 소유). 세션 설계 문서(`{ userId, role, viewMode, storeId }`)는 안B를 전제하는데 엔티티는 두 모델이 혼재되어 있음. **ERD 확정 전 결정 필수** — 결정에 따라 `store.login_id`/`password` 컬럼 존치 여부, FK 방향이 바뀜.
2. **likes.(user_id, store_id) 복합 UK** — 현재 앱 레벨에서만 중복 방지. DB 제약 추가 권장.
3. **review.(user_id, store_id) 복합 UK** — "1인 1매장 1리뷰(덮어쓰기)" 정책이 맞다면 UK 추가 권장.
4. **report.(store_id, report_date) 복합 UK** — 일자별 리포트 중복 생성 방지.
5. **reservation.pickup_code UK** — 코드 생성 로직에서 고유성을 보장하는지 확인, DB UK로 이중 방어 권장.
6. **inquiry_comment 작성자 구분** — 문의 작성자 본인 댓글인지, 매장/운영자 답변인지 구분하는 컬럼이 없음(엔티티 주석에 "role/viewMode 세션 로직 확정 후 추가 검토"로 명시). 고객센터 화면에서 "답변" 배지를 표시하려면 컬럼 추가 필요.
7. **product.updated_at 없음** — 상품 정보 수정 이력 미추적. 상품 수정 기능(WBS 3.0) 구현 시 필요 여부 재확인.
8. **CLAUDE.md 대비 미착수 테이블** — 카테고리 마스터, 신고, 절약 목표/랭킹·뱃지, 알림(WebSocket/SSE 대상) 등은 아직 테이블 스펙 없음(공지사항은 `notice` 테이블로 착수됨). 해당 기능 착수 시(WBS 3.0/6.0/6.5/7.0) 엔티티 추가와 함께 이 문서 갱신.

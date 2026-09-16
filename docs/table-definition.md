# 테이블 정의서 (テーブル定義書) — 기리기리(끼리끼리)

- 작성 기준: `src/main/java/net/dsa/girigiri/domain/entity/*.java` (2026-09-16 갱신 — 코드 전수 조사, 12개 → **24개 테이블**)
- `docs/schema.sql`(ERD용 DDL)도 이 문서와 함께 24개 테이블 전체로 동기화했다. 실제 ERD 다이어그램(`docs/girigiri.vuerd.json`)은 IntelliJ ERD Editor 플러그인에서 `docs/schema.sql`을 "SQL DDL Import"로 다시 불러와 재생성해야 한다 (`docs/schema.sql` 상단 사용법 참고) — 이 문서/DDL 갱신만으로는 `.vuerd.json` 파일 자체는 자동으로 안 바뀐다.
- **store.owner_id 관계 모델 결정 완료**: 안B(User가 storeId로 Store를 소유) 확정 (2026-08-21, `StoreEntity` 코드 주석). 안A(Store 독립 로그인 계정)용 레거시 컬럼(`login_id`/`password`)은 이미 코드에서 제거됨 — 이전 버전 문서에 남아있던 "미확정" 표시는 이번 갱신으로 정리했다.
- CLAUDE.md 엔티티 설계(예상)에 있던 `Category`(카테고리 마스터)는 아직 별도 테이블 없이 `store.category` 자유 텍스트 컬럼으로만 존재한다. 절약 랭킹은 별도 테이블 없이 `users`/`user_badge` 집계로 처리.
- NN=NOT NULL / PK=기본키 / FK=외래키(설계 의도 — 실제 DB엔 FK 제약 없음, `docs/schema.sql` 상단 참고) / UK=고유키(UNIQUE) / IDX=인덱스

---

## 1. users — 会員

서비스 이용자. `role`로 일반회원(USER) / 점주(OWNER) / 운영자(ADMIN)를 구분한다. 자체 회원가입 없이 OAuth2(카카오/네이버/구글) + 이메일·비밀번호 가입만 지원하며, 이메일 가입도 `oauth_provider="email"`, `oauth_id=이메일값`으로 저장해 조회 로직을 통일한다.

| No | 컬럼명 (물리) | 논리명 | 데이터 타입 | NN | PK | FK | UK | IDX | 기본값 | 설명 · 제약조건 |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | id | 회원번호 | BIGINT | O | O | | | | AUTO_INCREMENT | 主キー |
| 2 | oauth_provider | 인증 제공자 | VARCHAR(20) | O | | | O* | O* | | kakao / naver / google / email |
| 3 | oauth_id | 인증 식별자 | VARCHAR(100) | O | | | O* | O* | | provider가 email이면 이메일 값 자체가 들어감 |
| 4 | password | 비밀번호 | VARCHAR(100) | | | | | | | 이메일 가입자만 값 존재(BCrypt). 소셜 계정은 NULL |
| 5 | role | 권한 | VARCHAR(20) | O | | | | | | USER / OWNER / ADMIN |
| 6 | status | 계정 상태 | VARCHAR(20) | | | | | | 'ACTIVE' | ACTIVE / SUSPENDED (슈퍼어드민 회원 정지) |
| 7 | nickname | 닉네임 | VARCHAR(30) | | | | | | | |
| 8 | email | 이메일 | VARCHAR(100) | | | | | | | 표시(마스킹)용. 로그인 식별자는 oauth_id이며 이 컬럼 자체엔 UK 없음 |
| 9 | phone | 휴대폰 번호 | VARCHAR(20) | | | | O | | | 본인인증 미연동, 입력값 그대로 저장. 이메일/비번 찾기·매장 노쇼 연락용 (2026-09-10 추가) |
| 10 | savings_goal_amount | 절약 목표 금액 | INT | | | | | | | 마이페이지 가계부 "이번 달 목표" (2026-09-12 추가) |
| 11 | representative_badge | 대표 뱃지 코드 | VARCHAR(50) | | | | | | | NULL이면 기본 등급 티어로 폴백 (2026-09-12 추가) |
| 12 | region | 활동 지역 | VARCHAR(100) | | | | | | | 자유 텍스트. 위경도 좌표 연동 전 임시 항목 |
| 13 | profile_completed | 프로필 완성 여부 | BOOLEAN | O | | | | | false | 최초 소셜 로그인 직후 false → 회원가입 완료 화면 진입 여부 판단 |
| 14 | latitude | 위도 | DOUBLE | | | | | | | |
| 15 | longitude | 경도 | DOUBLE | | | | | | | |
| 16 | terms_agreed | 이용약관 동의 | BOOLEAN | O | | | | | false | 가입 완료 시 항상 true (2026-09-10 추가) |
| 17 | privacy_agreed | 개인정보 동의 | BOOLEAN | O | | | | | false | 가입 완료 시 항상 true |
| 18 | marketing_agreed | 마케팅 수신 동의 | BOOLEAN | O | | | | | false | 선택 항목, 알림 설정에서 on/off |
| 19 | agreed_at | 약관 동의 일시 | DATETIME | | | | | | | |
| 20 | created_at | 등록일시 | DATETIME | O | | | | | | 공통 컬럼 |
| 21 | updated_at | 수정일시 | DATETIME | | | | | | | 공통 컬럼 |

\* UK/IDX는 `(oauth_provider, oauth_id)` **복합** 유니크·인덱스(`uk_users_oauth`) — `findByOauthProviderAndOauthId` 조회에 사용.

---

## 2. store — 店舗

점주(OWNER) 계정이 아니라, **점주 회원(`users`, role=OWNER)이 `owner_id`로 소유하는 매장 데이터**다 (안B 확정 — 위 상단 안내 참고).

매장 신뢰도(취소율) 자동 정지 — 2026-09-16 추가(`StoreReliabilityService`): 최근 예약 기준 신뢰도가 70% 미만이 되면(예약 10건 이상 매장만 대상) 1회차 7일 → 2회차 14일 → 3회차 30일 → 4회차 영구정지로 단계 상승한다. 관련 컬럼은 아래 25~27번.

| No | 컬럼명 (물리) | 논리명 | 데이터 타입 | NN | PK | FK | UK | IDX | 기본값 | 설명 · 제약조건 |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | id | 매장번호 | BIGINT | O | O | | | | AUTO_INCREMENT | 主キー |
| 2 | store_name | 매장명 | VARCHAR(50) | O | | | | | | |
| 3 | category | 카테고리 | VARCHAR(30) | | | | | O | | 베이커리/반찬/도시락/카페 등, 탐색 화면 필터 조건 |
| 4 | address | 주소 | VARCHAR(200) | | | | | | | |
| 5 | latitude | 위도 | DOUBLE | | | | | | | |
| 6 | longitude | 경도 | DOUBLE | | | | | | | |
| 7 | operating_hours | 영업시간 | VARCHAR(100) | | | | | | | |
| 8 | prep_time_minutes | 준비시간(분) | INT | | | | | | 20 | NULL이면 매장 설정 전(항상 주문 가능 취급) |
| 9 | last_pickup_time | 마지막 픽업시간 | TIME | | | | | | | 이 시간 이후 당일 주문/픽업 마감 |
| 10 | rescue_goal_percent | 구제율 목표(%) | INT | | | | | | 70 | 대시보드 카드 목표치, 점주가 연필 아이콘으로 직접 수정 |
| 11 | role | 권한 | VARCHAR(20) | O | | | | | | = 'OWNER' |
| 12 | business_number | 사업자번호 | VARCHAR(30) | | | | | | | 입점 신청 시 검증용 |
| 13 | phone | 연락처 | VARCHAR(30) | | | | | | | |
| 14 | approval_status | 입점 승인 상태 | VARCHAR(20) | | | | | O | 'PENDING' | PENDING / APPROVED / REJECTED (`findByApprovalStatus`) |
| 15 | status | 매장 상태 | VARCHAR(20) | | | | | | | ACTIVE / SUSPENDED. NULL=ACTIVE와 동일 취급(슈퍼어드민 매장 정지, 2026-09-08 추가) |
| 16 | pos_provider | POS 연동사 | VARCHAR(30) | | | | | | | okpos/posbank/unionpos/etc. NULL=미연동 (2026-08-27 추가) |
| 17 | pos_store_code | POS 매장 코드 | VARCHAR(50) | | | | | | | |
| 18 | pos_connected_at | POS 연동 시각 | DATETIME | | | | | | | |
| 19 | pos_last_sync_at | POS 마지막 동기화 시각 | DATETIME | | | | | | | |
| 20 | pos_draft_prompt_time | 자동 초안 생성 시각 | TIME | | | | | | | 매일 이 시각에 POS 재고 스냅샷으로 "오늘의 구제" 초안 자동 생성(B안). NULL이면 자동 생성 안 함 |
| 21 | bank_name | 정산 계좌 은행 | VARCHAR(30) | | | | | | | 주간 정산 지급용 (2026-09-01 추가) |
| 22 | bank_account | 정산 계좌번호 | VARCHAR(40) | | | | | | | |
| 23 | account_holder | 정산 계좌 예금주 | VARCHAR(40) | | | | | | | |
| 24 | owner_id | 소유 회원번호 | BIGINT | | | O | | O | | users.id 참조 (안B 확정, `findByOwnerId`) |
| 25 | reliability_suspended_until | 신뢰도 정지 해제 시각 | DATETIME | | | | | | | 신뢰도(취소율) 자동 정지 해제 예정 시각. NULL/과거면 정지 아님 — 별도 스케줄러 없이 조회 시점마다 동적 판정 (2026-09-16 추가) |
| 26 | reliability_suspension_count | 신뢰도 위반 누적 횟수 | INT | O | | | | | 0 | 0~3. 7일→14일→30일 순서로 참조. 정지가 풀려도 리셋 안 됨 |
| 27 | reliability_banned | 신뢰도 영구정지 여부 | BOOLEAN | O | | | | | false | true면 영구정지(4회차 위반). `store.status`(슈퍼어드민 수동 정지)와는 별도 컬럼 — 혼용 시 수동 정지 매장이 자동 해제될 위험이 있어 분리 |
| 28 | created_at | 등록일시 | DATETIME | O | | | | | | 공통 컬럼 |
| 29 | updated_at | 수정일시 | DATETIME | | | | | | | 공통 컬럼 |

---

## 3. menu_item — POSメニュー

POS 카탈로그에서 불러온 매장의 **영속 메뉴**(그날그날 파는 마감 상품인 `product`와는 다름). 점주가 상품/템플릿 등록 시 여기서 품목명·원가·사진을 자동완성으로 끌어다 쓴다. POS가 재고를 push하면(B안) 마감 무렵 이 재고 스냅샷으로 "오늘의 구제" 초안이 자동 생성된다.

| No | 컬럼명 (물리) | 논리명 | 데이터 타입 | NN | PK | FK | UK | IDX | 기본값 | 설명 · 제약조건 |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | id | 메뉴번호 | BIGINT | O | O | | | | AUTO_INCREMENT | 主キー |
| 2 | store_id | 매장번호 | BIGINT | O | | O | | | | store.id 참조 |
| 3 | pos_sku | POS 원본 식별자 | VARCHAR(50) | | | | | | | SKU/바코드, mock 카탈로그 upsert 기준 |
| 4 | name | 메뉴명 | VARCHAR(100) | O | | | | | | |
| 5 | original_price | 원가 | INT | O | | | | | | |
| 6 | image_url | 이미지 URL | VARCHAR(255) | | | | | | | |
| 7 | stock_quantity | 현재 재고 | INT | | | | | | | POS가 push. NULL이면 재고 정보 없음(수동 등록만 가능) |
| 8 | app_sale_enabled | 앱 판매 자동생성 여부 | BOOLEAN | O | | | | | false | 점주가 연동 화면에서 켬 |
| 9 | discount_rate | 앱 판매 할인율(%) | INT | | | | | | | NULL이면 마감까지 남은 시간 기준 자동(20/30/50). 값이 있어도 자동값보다 낮으면 자동값 적용("더 깎기"만 허용) |
| 10 | app_sale_quantity | 앱 판매 최대 수량 | INT | | | | | | | NULL이면 POS 재고 전량. 값 있으면 MIN(재고, 이 값) |
| 11 | created_at | 등록일시 | DATETIME | O | | | | | | 공통 컬럼 |
| 12 | updated_at | 수정일시 | DATETIME | | | | | | | 공통 컬럼 |

---

## 4. listing_template — 自動出品テンプレート

"오늘의 구제 자동 등록" 템플릿. 사장님이 한 번 등록해두면 `ListingDraftScheduler`가 매일 정해진 요일·시각에 이 템플릿으로 `product`(status='draft') 초안을 만들고 알림을 보낸다. WBS/CLAUDE.md에는 없는, 팀 논의로 추가된 신규 기능.

| No | 컬럼명 (물리) | 논리명 | 데이터 타입 | NN | PK | FK | UK | IDX | 기본값 | 설명 · 제약조건 |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | id | 템플릿번호 | BIGINT | O | O | | | | AUTO_INCREMENT | 主キー |
| 2 | store_id | 매장번호 | BIGINT | O | | O | | | | store.id 참조 |
| 3 | name | 상품명 | VARCHAR(100) | O | | | | | | |
| 4 | original_price | 원가 | INT | O | | | | | | |
| 5 | image_url | 이미지 URL | VARCHAR(255) | | | | | | | |
| 6 | description | 설명 | VARCHAR(500) | | | | | | | |
| 7 | default_quantity | 기본 수량 | INT | O | | | | | | |
| 8 | weekdays | 실행 요일 | VARCHAR(20) | O | | | | | | ISO 요일 숫자 CSV(1=월…7=일), 예 "1,2,3,4,5" |
| 9 | prompt_time | 초안 생성 시각 | TIME | O | | | | | | 마감 시각보다 1~2시간 앞을 권장 |
| 10 | active | 활성 여부 | BOOLEAN | O | | | | | true | |
| 11 | created_at | 등록일시 | DATETIME | O | | | | | | 공통 컬럼 |
| 12 | updated_at | 수정일시 | DATETIME | | | | | | | 공통 컬럼 |

---

## 5. product — 商品

매장이 등록한 마감세일 상품(재고).

| No | 컬럼명 (물리) | 논리명 | 데이터 타입 | NN | PK | FK | UK | IDX | 기본값 | 설명 · 제약조건 |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | id | 상품번호 | BIGINT | O | O | | | | AUTO_INCREMENT | 主キー |
| 2 | store_id | 매장번호 | BIGINT | O | | O | | O | | store.id 참조 (`findByStoreId`, 점주 대시보드) |
| 3 | name | 상품명 | VARCHAR(100) | O | | | | | | |
| 4 | original_price | 원가 | INT | O | | | | | | 원(KRW) 정수 단위 |
| 5 | discounted_price | 할인가 | INT | O | | | | | | |
| 6 | quantity | 등록 수량 | INT | O | | | | | | |
| 7 | remaining_quantity | 잔여 수량 | INT | O | | | | | | 0 이상, quantity 이하 |
| 8 | image_url | 이미지 URL | VARCHAR(255) | | | | | | | |
| 9 | description | 설명 | VARCHAR(500) | | | | | | | |
| 10 | status | 상태 | VARCHAR(20) | O | | | | O | | draft(자동 초안) / active / sold / expired / skipped("오늘 안 함" 처리). draft/skipped는 홈·검색·대시보드 집계 제외 |
| 11 | template_id | 출처 템플릿 | BIGINT | | | O | | | | 템플릿 방식 초안이면 listing_template.id (2026-08-26 추가) |
| 12 | menu_item_id | 출처 메뉴 | BIGINT | | | O | | | | POS 재고 스냅샷 방식 초안이면 menu_item.id (2026-08-27 추가) |
| 13 | registered_at | 등록일시 | DATETIME | O | | | | | | 공통 컬럼(created_at 역할) |
| 14 | updated_at | 수정일시 | DATETIME | | | | | | | 상품 수정뿐 아니라 예약/취소로 인한 재고 변동에도 갱신됨(범용 컬럼, 수정 이력 전용 아님) |

---

## 6. reservation — 予約履歴

회원이 상품을 예약(선결제)한 이력. 1행 = 1예약, 상태를 컬럼으로 관리(이력 삭제 없음).

| No | 컬럼명 (물리) | 논리명 | 데이터 타입 | NN | PK | FK | UK | IDX | 기본값 | 설명 · 제약조건 |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | id | 예약번호 | BIGINT | O | O | | | | AUTO_INCREMENT | 主キー |
| 2 | user_id | 회원번호 | BIGINT | O | | O | | O | | users.id 참조 |
| 3 | product_id | 상품번호 | BIGINT | O | | O | | O | | product.id 참조 |
| 4 | product_name | 상품명(스냅샷) | VARCHAR(100) | | | | | | | 주문 시점 스냅샷 — 이후 상품명 변경/삭제가 과거 기록에 영향 없게 |
| 5 | store_id | 매장번호 | BIGINT | O | | O | | O | | store.id 참조 |
| 6 | reserved_quantity | 예약 수량 | INT | O | | | | | | |
| 7 | total_price | 결제 총액 | INT | O | | | | | | |
| 8 | coupon_id | 사용 쿠폰 | BIGINT | | | O | | | | 이 예약에 쓴 coupon.id. 안 썼으면 NULL (2026-09-07 추가) |
| 9 | pickup_time | 픽업 시각 | DATETIME | | | | | O | | 노쇼 판정·오늘 픽업 목록 조회에 사용 |
| 10 | pickup_code | 픽업 코드 | VARCHAR(30) | | | | O | O | | QR/현장 확인용 (`findByPickupCode`). DB UK로 중복 방지 (2026-09-08 추가) |
| 11 | status | 상태 | VARCHAR(20) | O | | | | O | | pending → confirmed → ready → picked / cancelled / noshowed |
| 12 | reserved_at | 예약일시 | DATETIME | O | | | | | | 공통 컬럼(created_at 역할) |
| 13 | accepted_at | 매장 수락일시 | DATETIME | | | | | | | confirmed→ready 전환 시점 |
| 14 | picked_at | 픽업완료일시 | DATETIME | | | | | | | |
| 15 | cancelled_by | 취소 주체 | VARCHAR(10) | | | | | | | USER / STORE / ADMIN. status=cancelled일 때만 값 존재 — 매장 신뢰도(취소율) 계산에도 사용 |
| 16 | cancel_reason | 취소 사유 | VARCHAR(255) | | | | | | | STORE/ADMIN 취소 시 사유 텍스트 |

---

## 7. payment — 決済

PortOne(아임포트) 결제 1건. 담당: 송채현. `ready()`/`approve()`/`fail()`/`cancel()`/`applyCancel()` 상태 전이 메서드로만 변경 가능(불법 상태 조합 방지).

| No | 컬럼명 (물리) | 논리명 | 데이터 타입 | NN | PK | FK | UK | IDX | 기본값 | 설명 · 제약조건 |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | id | 결제번호 | BIGINT | O | O | | | | AUTO_INCREMENT | 主キー |
| 2 | reservation_id | 예약번호 | BIGINT | O | | O | O | | | reservation.id 참조. 1예약당 1결제 |
| 3 | merchant_uid | 가맹점 주문번호 | VARCHAR(50) | O | | | O | | | 우리 서버가 생성해 PortOne에 전달 |
| 4 | imp_uid | PortOne 결제고유번호 | VARCHAR(50) | | | | O | | | 결제 승인 후 채워짐 |
| 5 | amount | 결제금액 | INT | O | | | | | | |
| 6 | pay_method | 결제수단 | VARCHAR(20) | | | | | | | card / kakaopay / naverpay 등(PortOne 응답의 method.type) |
| 7 | pay_status | 결제상태 | VARCHAR(20) | O | | | | | | enum(PayStatus): READY / PAID / FAILED / CANCELLED, 대문자로 저장 |
| 8 | fail_reason | 실패사유 | VARCHAR(255) | | | | | | | 255자 초과 시 서버에서 자름(DB 저장 실패로 취소 트랜잭션 전체가 롤백되는 걸 방지, 2026-09-08 대응) |
| 9 | paid_at | 결제완료일시 | DATETIME | | | | | | | |
| 10 | requested_at | 결제요청일시 | DATETIME | O | | | | | | 공통 컬럼(createdAt을 `@AttributeOverride`로 이름만 변경) |
| 11 | updated_at | 수정일시 | DATETIME | | | | | | | 공통 컬럼 |

---

## 8. payment_cancel — 決済取消履歴

결제 취소/환불 **시도** 1건을 남기는 감사(audit) 로그. 같은 결제를 여러 번 취소 시도(예: 1차 환불 API 실패 → 재시도)해도 이력이 각각 남는다. 담당: 송채현, 송보미 제안(2026-08-25).

| No | 컬럼명 (물리) | 논리명 | 데이터 타입 | NN | PK | FK | UK | IDX | 기본값 | 설명 · 제약조건 |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | id | 취소이력번호 | BIGINT | O | O | | | | AUTO_INCREMENT | 主キー |
| 2 | payment_id | 결제번호 | BIGINT | O | | O | | O | | payment.id 참조 |
| 3 | amount | 취소 금액 | INT | O | | | | | | |
| 4 | reason | 사유 | VARCHAR(255) | | | | | | | |
| 5 | succeeded | 성공 여부 | BOOLEAN | O | | | | | | true=환불 성공(또는 결제 전 취소), false=PortOne 환불 API 실패 |
| 6 | requested_at | 요청일시 | DATETIME | O | | | | | | 공통 컬럼(createdAt을 `@AttributeOverride`로 이름만 변경) — 불변 로그 |

---

## 9. receipt — 領収書

| No | 컬럼명 (물리) | 논리명 | 데이터 타입 | NN | PK | FK | UK | IDX | 기본값 | 설명 · 제약조건 |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | id | 영수증번호 | BIGINT | O | O | | | | AUTO_INCREMENT | 主キー |
| 2 | reservation_id | 예약번호 | BIGINT | O | | O | | | | reservation.id 참조. 1예약당 1영수증(`findByReservationId`) |
| 3 | pdf_url | PDF 경로 | VARCHAR(255) | | | | | | | |
| 4 | generated_at | 생성일시 | DATETIME | O | | | | | | 공통 컬럼(created_at 역할) |

---

## 10. coupon — クーポン

회원 1명에게 발급된 쿠폰 1장. 담당: 송채현(2026-09-07 신규/재설계). 발급 경로 3가지 모두 **회원 1명당 1장 지급** 방식이라 공유 수량 풀 개념이 없다.

| No | 컬럼명 (물리) | 논리명 | 데이터 타입 | NN | PK | FK | UK | IDX | 기본값 | 설명 · 제약조건 |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | id | 쿠폰번호 | BIGINT | O | O | | | | AUTO_INCREMENT | 主キー |
| 2 | source | 발급 경로 | VARCHAR(30) | O | | | | | | WELCOME(가입 시 자동) / STORE_COMPENSATION(매장귀책 취소 보상) / PROMOTION(캠페인 코드 입력) |
| 3 | issued_to_user_id | 수령 회원번호 | BIGINT | O | | O | | O | | users.id 참조 |
| 4 | campaign_id | 발급 캠페인 | BIGINT | | | O | | | | source=PROMOTION일 때만 값 존재. coupon_campaign.id 참조 |
| 5 | source_reservation_id | 발급 사유 예약 | BIGINT | | | O | | | | source=STORE_COMPENSATION일 때만 값 존재. reservation.id 참조(감사/문의 대응용) |
| 6 | discount_rate | 할인율(%) | INT | O | | | | | | 정률 할인 |
| 7 | expires_at | 만료일시 | DATETIME | O | | | | | | |
| 8 | used | 사용 여부 | BOOLEAN | O | | | | | false | 체크아웃에서 사용 시 true. 매장귀책/일반 취소 시 복구(false), 회원 노쇼로 인한 취소는 복구 안 함 |
| 9 | created_at | 발급일시 | DATETIME | O | | | | | | 공통 컬럼 |

---

## 11. coupon_policy — クーポン方針

웰컴/매장귀책보상 쿠폰의 할인율 정책값. **항상 1행만 존재**(id=1) — 없으면 서비스가 기본값(웰컴 10%/보상 15%)으로 자동 생성.

| No | 컬럼명 (물리) | 논리명 | 데이터 타입 | NN | PK | FK | UK | IDX | 기본값 | 설명 · 제약조건 |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | id | 번호 | BIGINT | O | O | | | | AUTO_INCREMENT | 主キー(사실상 항상 1행) |
| 2 | welcome_discount_rate | 웰컴 쿠폰 할인율(%) | INT | O | | | | | | |
| 3 | compensation_discount_rate | 보상 쿠폰 할인율(%) | INT | O | | | | | | |
| 4 | updated_at | 수정일시 | DATETIME | | | | | | | |

---

## 12. coupon_campaign — 프로모션 캠페인

슈퍼어드민이 이벤트마다 만드는 쿠폰 캠페인. 회원이 `code`를 앱에 입력해 발급받으며, 캠페인 1개당 회원 1명에게 1장만 발급된다.

| No | 컬럼명 (물리) | 논리명 | 데이터 타입 | NN | PK | FK | UK | IDX | 기본값 | 설명 · 제약조건 |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | id | 캠페인번호 | BIGINT | O | O | | | | AUTO_INCREMENT | 主キー |
| 2 | name | 캠페인명 | VARCHAR(100) | O | | | | | | 관리용, 회원에게는 비노출 |
| 3 | code | 발급 코드 | VARCHAR(30) | O | | | O | | | 회원이 "쿠폰 받기" 화면에 입력하는 값. 대문자로 정규화해 저장 |
| 4 | discount_rate | 할인율(%) | INT | O | | | | | | 발급 시점 값을 coupon에 복사 저장(이후 캠페인 값 변경은 기발급 쿠폰에 영향 없음) |
| 5 | expires_at | 만료일시 | DATETIME | O | | | | | | |
| 6 | active | 활성 여부 | BOOLEAN | O | | | | | true | false면 새 발급만 막힘(기발급 쿠폰은 유효) |
| 7 | created_at | 등록일시 | DATETIME | O | | | | | | 공통 컬럼 |

---

## 13. review — レビュー

| No | 컬럼명 (물리) | 논리명 | 데이터 타입 | NN | PK | FK | UK | IDX | 기본값 | 설명 · 제약조건 |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | id | 리뷰번호 | BIGINT | O | O | | | | AUTO_INCREMENT | 主キー |
| 2 | user_id | 회원번호 | BIGINT | O | | O | | O | | users.id 참조 |
| 3 | store_id | 매장번호 | BIGINT | O | | O | | O | | store.id 참조 |
| 4 | rating | 별점 | INT | O | | | | | | |
| 5 | content | 내용 | VARCHAR(500) | | | | | | | |
| 6 | image_url | 사진 URL | VARCHAR(500) | | | | | | | 사진 리뷰(강노은 추가). 파일 업로드 인프라가 없어 URL 문자열로만 저장 |
| 7 | edited | 수정 여부 | BOOLEAN | O | | | | | false | 최초 작성 후 재작성(덮어쓰기) 시 true — 목록에 "수정됨" 표시용 |
| 8 | created_at | 작성일시 | DATETIME | O | | | | | | 공통 컬럼 |

> ⚠️ **검토 필요(유지)**: 서비스 로직상 "회원 1명당 매장 1곳에 리뷰 1건"으로 덮어쓰기 하는 것으로 보이는데(`edited` 플래그 존재), DB 레벨에 `(user_id, store_id)` 복합 UK가 없다. 정책이 맞다면 UK 추가 권장.

---

## 14. review_summary — レビューAI要約

가게별 "AI 리뷰 요약" 캐시(가게당 최대 1행). 담당: 강노은. 매 요청마다 Gemini를 다시 부르지 않도록, 마지막 요약 생성 시점의 리뷰 개수를 저장해두고 리뷰 개수가 달라졌을 때만 재생성한다.

| No | 컬럼명 (물리) | 논리명 | 데이터 타입 | NN | PK | FK | UK | IDX | 기본값 | 설명 · 제약조건 |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | id | 번호 | BIGINT | O | O | | | | AUTO_INCREMENT | 主キー |
| 2 | store_id | 매장번호 | BIGINT | O | | O | O | | | store.id 참조, 가게당 1행 |
| 3 | summary | 요약 내용 | VARCHAR(1000) | O | | | | | | |
| 4 | review_count_at_summary | 요약 시점 리뷰 수 | INT | O | | | | | | 현재 리뷰 개수와 다르면 재생성 트리거 |
| 5 | generated_at | 생성일시 | DATETIME | | | | | | | INSERT/UPDATE마다 서비스 코드가 직접 채움(`@CreatedDate` 미사용) |

---

## 15. likes — お気に入り

| No | 컬럼명 (물리) | 논리명 | 데이터 타입 | NN | PK | FK | UK | IDX | 기본값 | 설명 · 제약조건 |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | id | 찜번호 | BIGINT | O | O | | | | AUTO_INCREMENT | 主キー |
| 2 | user_id | 회원번호 | BIGINT | O | | O | O* | O | | users.id 참조 |
| 3 | store_id | 매장번호 | BIGINT | O | | O | O* | O | | store.id 참조 |
| 4 | created_at | 찜일시 | DATETIME | O | | | | | | 공통 컬럼 |

\* `(user_id, store_id)` 복합 UK(`uk_likes_user_store`, 2026-09-08 추가) — 동시 찜 요청으로 인한 중복 행 방지.

---

## 16. notification — 通知

인앱 알림함 1건. 담당: 강노은. `NotificationTriggerScheduler`가 주기적으로 스캔해서 생성하며, `source_key`로 같은 사건에 대한 중복 생성을 막는다(idempotent).

| No | 컬럼명 (물리) | 논리명 | 데이터 타입 | NN | PK | FK | UK | IDX | 기본값 | 설명 · 제약조건 |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | id | 알림번호 | BIGINT | O | O | | | | AUTO_INCREMENT | 主キー |
| 2 | user_id | 수신 회원번호 | BIGINT | O | | O | | O | | users.id 참조 |
| 3 | type | 알림 유형 | VARCHAR(30) | O | | | | | | LIKE_STORE_OPEN / RESERVATION_CONFIRMED / RESERVATION_PICKUP_SOON / RESERVATION_NOSHOW / INQUIRY_COMMENT / ADMIN_NEW_MEMBER / ADMIN_NEW_INQUIRY / ADMIN_NEW_RESERVATION / WELCOME_COUPON / STORE_CANCEL_COUPON |
| 4 | message | 메시지 | VARCHAR(200) | O | | | | | | |
| 5 | link_url | 이동 링크 | VARCHAR(200) | | | | | | | 클릭 시 이동할 곳. 없으면 알림함에 그대로 머묾 |
| 6 | source_key | 중복 방지 키 | VARCHAR(100) | | | | | | | "사건 종류:대상id" 형태, 예 "like_open:7:42" — `existsBySourceKey`로 중복 생성 방지 |
| 7 | is_read | 읽음 여부 | BOOLEAN | O | | | | | false | |
| 8 | created_at | 생성일시 | DATETIME | O | | | | | | 공통 컬럼 |

---

## 17. notification_setting — 通知設定

사용자 1명당 1행. `UserEntity`(문창호 담당)에 컬럼을 얹지 않고 강노은이 별도 엔티티로 분리.

| No | 컬럼명 (물리) | 논리명 | 데이터 타입 | NN | PK | FK | UK | IDX | 기본값 | 설명 · 제약조건 |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | id | 번호 | BIGINT | O | O | | | | AUTO_INCREMENT | 主キー |
| 2 | user_id | 회원번호 | BIGINT | O | | O | O | | | users.id 참조, 회원당 1행 |
| 3 | push_enabled | PUSH 전체 스위치 | BOOLEAN | O | | | | | true | |
| 4 | like_alert_enabled | 찜 알림 스위치 | BOOLEAN | O | | | | | true | 찜한 가게 마감세일 시작 알림 |
| 5 | updated_at | 수정일시 | DATETIME | | | | | | | |

---

## 18. notice — お知らせ

슈퍼어드민이 작성하는 공지사항 (WBS 7.0, 송보미 담당). `published` 수동 on/off + 선택적 게시 기간(`publish_start_at`/`publish_end_at`) 조합으로 노출 여부가 결정된다.

| No | 컬럼명 (물리) | 논리명 | 데이터 타입 | NN | PK | FK | UK | IDX | 기본값 | 설명 · 제약조건 |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | id | 공지번호 | BIGINT | O | O | | | | AUTO_INCREMENT | 主キー |
| 2 | title | 제목 | VARCHAR(100) | O | | | | | | |
| 3 | content | 내용 | VARCHAR(2000) | O | | | | | | |
| 4 | published | 게시 여부 | BOOLEAN | O | | | | | | 수동 on/off("게시글 내리기") |
| 5 | publish_start_at | 게시 시작일 | DATE | | | | | | | NULL이면 등록/게시 즉시부터 (2026-09-01 추가) |
| 6 | publish_end_at | 게시 종료일 | DATE | | | | | | | NULL이면 무기한 |
| 7 | created_at | 등록일시 | DATETIME | O | | | | | | 공통 컬럼 |
| 8 | updated_at | 수정일시 | DATETIME | | | | | | | 공통 컬럼 |

---

## 19. inquiry — 問い合わせ

문의 게시판 글. `store_id`가 있으면 특정 매장 문의, NULL이면 서비스 전체 문의.

| No | 컬럼명 (물리) | 논리명 | 데이터 타입 | NN | PK | FK | UK | IDX | 기본값 | 설명 · 제약조건 |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | id | 문의번호 | BIGINT | O | O | | | | AUTO_INCREMENT | 主キー |
| 2 | user_id | 작성자 회원번호 | BIGINT | O | | O | | O | | users.id 참조 |
| 3 | store_id | 매장번호 | BIGINT | | | O | | | | store.id 참조. NULL = 서비스 전체 문의 |
| 4 | reservation_id | 관련 예약번호 | BIGINT | | | O | | | | 예약 상세 "문의하기"로 접수된 경우 그 예약(2026-09-08 추가). store_id는 이 값이 있으면 예약에서 자동 채워짐 |
| 5 | title | 제목 | VARCHAR(100) | O | | | | | | |
| 6 | content | 내용 | VARCHAR(1000) | O | | | | | | |
| 7 | image_url | 사진 URL | VARCHAR(500) | | | | | | | 문의 첨부 사진(강노은 추가). 수정 기능 없음(등록만 가능) |
| 8 | created_at | 작성일시 | DATETIME | O | | | | | | 공통 컬럼 |

---

## 20. inquiry_comment — 問い合わせコメント

문의 글에 달리는 댓글(작성자 추가 설명 또는 매장/운영자 답변).

| No | 컬럼명 (물리) | 논리명 | 데이터 타입 | NN | PK | FK | UK | IDX | 기본값 | 설명 · 제약조건 |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | id | 댓글번호 | BIGINT | O | O | | | | AUTO_INCREMENT | 主キー |
| 2 | inquiry_id | 문의번호 | BIGINT | O | | O | | O | | inquiry.id 참조 |
| 3 | user_id | 작성자 회원번호 | BIGINT | O | | O | | | | users.id 참조. 문의 작성자 본인/매장/운영자 구분 컬럼 없음(미정) |
| 4 | content | 내용 | VARCHAR(500) | O | | | | | | |
| 5 | created_at | 작성일시 | DATETIME | O | | | | | | 공통 컬럼 |

---

## 21. complaint — 通報

신고 접수(슈퍼어드민 "신고·문의" 화면). 신고 제출 화면(소비자용)이 아직 없고 비회원 신고도 있을 수 있어, 대상/신고자는 FK 강제 대신 표시용 스냅샷 문자열 + 선택적 id(있으면 상세 링크) 조합으로 저장한다. "report"라는 이름은 매장 판매/폐기 리포트(`report` 테이블)가 이미 쓰고 있어 `complaint`로 지었다.

| No | 컬럼명 (물리) | 논리명 | 데이터 타입 | NN | PK | FK | UK | IDX | 기본값 | 설명 · 제약조건 |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | id | 신고번호 | BIGINT | O | O | | | | AUTO_INCREMENT | 主キー |
| 2 | target_name | 신고 대상 이름 | VARCHAR(100) | O | | | | | | 매장/유저 이름 스냅샷 |
| 3 | target_store_id | 대상 매장번호 | BIGINT | | | | | | | 대상이 매장이면 채움(있으면 매장 상세 링크). FK 미설정(감사 로그 성격) |
| 4 | target_reservation_id | 대상 예약번호 | BIGINT | | | | | | | 예약 상세 "신고하기"로 접수된 경우(2026-09-08 추가). FK 미설정 |
| 5 | reason | 신고 사유(요약) | VARCHAR(100) | O | | | | | | 목록 제목으로 사용 |
| 6 | content | 상세 내용 | VARCHAR(1000) | O | | | | | | |
| 7 | reporter_name | 신고자 이름 | VARCHAR(50) | O | | | | | | |
| 8 | reporter_id | 신고자 회원번호 | BIGINT | | | | | | | 실제 회원이면 채움(있으면 회원 상세 링크). FK 미설정 |
| 9 | status | 처리 상태 | VARCHAR(20) | O | | | | | 'PENDING' | PENDING / RESOLVED |
| 10 | admin_reply | 운영자 답변 | VARCHAR(1000) | | | | | | | |
| 11 | created_at | 접수일시 | DATETIME | O | | | | | | 공통 컬럼 |
| 12 | resolved_at | 처리완료일시 | DATETIME | | | | | | | |

---

## 22. report — レポート

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

\* `(store_id, report_date)` 복합 UK/IDX 권장(유지) — 매장당 일자별 리포트는 1건이어야 함, 아직 미적용.

---

## 23. settlement — 週次精算

주간 정산 1건. 담당: 문창호(2026-09-01, WBS 2.0 "매장 정산 페이지"). "정산 확정"(계산, `SettlementScheduler` 매주 월 00:00)과 "지급"(슈퍼어드민 실제 송금 확인)을 분리한 구조.

| No | 컬럼명 (물리) | 논리명 | 데이터 타입 | NN | PK | FK | UK | IDX | 기본값 | 설명 · 제약조건 |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | id | 정산번호 | BIGINT | O | O | | | | AUTO_INCREMENT | 主キー |
| 2 | store_id | 매장번호 | BIGINT | O | | O | O* | | | store.id 참조 |
| 3 | period_start | 정산 대상 시작일 | DATE | O | | | O* | | | 대상 주간 월요일 |
| 4 | period_end | 정산 대상 종료일 | DATE | O | | | | | | 대상 주간 일요일 |
| 5 | gross | 총 결제액 | BIGINT | O | | | | | | PAID + 결제 후 취소분 |
| 6 | refund | 환불 차감액 | BIGINT | O | | | | | | |
| 7 | net_amount | 순 결제액 | BIGINT | O | | | | | | = gross - refund |
| 8 | commission_rate | 적용 수수료율(%) | INT | O | | | | | | 스냅샷(정책 변경돼도 과거 기록 유지) |
| 9 | commission | 플랫폼 수수료 | BIGINT | O | | | | | | |
| 10 | week_amount | 이번 주 정산분 | BIGINT | O | | | | | | = net_amount - commission |
| 11 | carried_in | 이월 합산액 | BIGINT | O | | | | | | 이전 CARRIED 건들의 week_amount 합 |
| 12 | payout | 실 지급액 | BIGINT | O | | | | | | CARRIED/ROLLED면 0 |
| 13 | status | 상태 | VARCHAR(20) | O | | | | | | PENDING(지급대기) / PAID(지급완료) / CARRIED(이월) / ROLLED(이월분 합산됨) |
| 14 | merged_into_id | 합산 대상 정산번호 | BIGINT | | | O | | | | status=ROLLED일 때 어느 정산에 합산됐는지, settlement.id 자기참조 |
| 15 | confirmed_at | 정산 확정일시 | DATETIME | O | | | | | | 스케줄러 실행 시각 |
| 16 | scheduled_payout_date | 지급 예정일 | DATE | O | | | | | | 확정일 + 영업일 2일 |
| 17 | paid_at | 실 지급완료일시 | DATETIME | | | | | | | |
| 18 | transfer_memo | 이체 확인 메모 | VARCHAR(200) | | | | | | | 슈퍼어드민 입력 |
| 19 | created_at | 생성일시 | DATETIME | O | | | | | | 공통 컬럼 |

\* `(store_id, period_start)` 복합 UK(`uk_settlement_store_period`) 적용됨.

---

## 24. user_badge — バッジ獲得記録

사용자가 실제로 획득(해금)한 뱃지 기록. 뱃지 조건은 매번 현재 통계로 재계산되지만(예: 이달 목표 달성률처럼 나중에 다시 거짓이 될 수 있음), "한 번 딴 뱃지는 영구 유지"되도록 조건을 처음 충족한 시점에 여기 기록해두고 이후로는 이 기록의 존재 여부로 해금 상태를 판정한다.

| No | 컬럼명 (물리) | 논리명 | 데이터 타입 | NN | PK | FK | UK | IDX | 기본값 | 설명 · 제약조건 |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | id | 번호 | BIGINT | O | O | | | | AUTO_INCREMENT | 主キー |
| 2 | user_id | 회원번호 | BIGINT | O | | O | O* | | | users.id 참조 |
| 3 | badge_code | 뱃지 코드 | VARCHAR(50) | O | | | O* | | | 예: "RESCUE_5", "BAKERY_LOVER" |
| 4 | earned_at | 획득일시 | DATETIME | O | | | | | | |
| 5 | notified | 토스트 노출 여부 | BOOLEAN | O | | | | | | "새 뱃지 획득!" 토스트를 이미 보여줬는지. 기존 행은 마이그레이션 시 TRUE로 일괄 처리 |

\* `(user_id, badge_code)` 복합 UK — 같은 뱃지 중복 획득 방지.

---

## 테이블 목록 (요약)

| No | 테이블명 (물리) | 논리명 | 설명 | 주요 관계 | 담당(WBS) |
|---|---|---|---|---|---|
| 1 | users | 会員 | 일반회원/점주/운영자 통합 계정 (role로 구분) | 1:N → store(owner), reservation, review, likes, coupon, notification, inquiry, complaint, user_badge 등 | 문창호(인증) / 송보미(DB) |
| 2 | store | 店舗 | 점주가 소유하는 매장 정보, 입점 승인 상태·정산 계좌 관리 | N:1 → users(owner) · 1:N → product, menu_item, listing_template, reservation, review, likes, report, settlement, inquiry | 김태훈(정보관리) / 문창호(라우팅·POS·정산) |
| 3 | menu_item | POSメニュー | POS 연동 매장 영속 메뉴 카탈로그 | N:1 → store · 1:N → product(menu_item_id) | 문창호 |
| 4 | listing_template | 自動出品テンプレート | "오늘의 구제" 자동 등록 템플릿 | N:1 → store · 1:N → product(template_id) | 문창호 |
| 5 | product | 商品 | 매장이 등록한 마감세일 상품(재고) | N:1 → store, listing_template, menu_item · 1:N → reservation | 김태훈 |
| 6 | reservation | 予約履歴 | 상품 예약·픽업 이력 (1건=1행, 상태 관리) | N:1 → users, product, store, coupon · 1:1 → receipt, payment | 송채현 |
| 7 | payment | 決済 | PortOne 결제 요청·승인 이력 | 1:1 → reservation | 송채현 |
| 8 | payment_cancel | 決済取消履歴 | 결제 취소/환불 시도 감사 로그 | N:1 → payment | 송채현 |
| 9 | receipt | 領収書 | 결제 영수증 PDF 기록 | 1:1 → reservation | 송채현 |
| 10 | coupon | クーポン | 회원에게 발급된 쿠폰 1장 | N:1 → users, coupon_campaign, reservation | 송채현 |
| 11 | coupon_policy | クーポン方針 | 웰컴/보상 쿠폰 할인율 정책값(1행) | (관계 없음, 독립 테이블) | 송채현 |
| 12 | coupon_campaign | プロモーションキャンペーン | 슈퍼어드민 발급 프로모션 코드 | 1:N → coupon | 송채현 |
| 13 | review | レビュー | 매장 리뷰·별점·사진 | N:1 → users, store | 강노은 |
| 14 | review_summary | レビューAI要約 | 가게별 AI 리뷰 요약 캐시(가게당 1행) | N:1 → store | 강노은 |
| 15 | likes | お気に入り | 관심 매장 찜하기 | N:1 → users, store | 강노은 |
| 16 | notification | 通知 | 인앱 알림함 | N:1 → users | 강노은 |
| 17 | notification_setting | 通知設定 | 회원별 알림 on/off 설정(회원당 1행) | N:1 → users | 강노은 |
| 18 | notice | お知らせ | 슈퍼어드민 공지사항 | (관계 없음, 독립 테이블) | 송보미 |
| 19 | inquiry | 問い合わせ | 문의 게시판 글 | N:1 → users, store(nullable), reservation(nullable) · 1:N → inquiry_comment | 강노은(작성) / 김태훈(답변) |
| 20 | inquiry_comment | 問い合わせコメント | 문의 댓글/답변 | N:1 → inquiry, users | 강노은 / 김태훈 |
| 21 | complaint | 通報 | 슈퍼어드민 신고 접수·처리 | (스냅샷 위주, FK 미설정) | 송보미 |
| 22 | report | レポート | 매장별 일간 판매·폐기 리포트 | N:1 → store | 문창호 |
| 23 | settlement | 週次精算 | 매장별 주간 정산 | N:1 → store · 자기참조(merged_into_id) | 문창호 |
| 24 | user_badge | バッジ獲得記録 | 가계부 뱃지 획득 기록 | N:1 → users | 문창호 |

---

## 확정 전 검토 필요 사항 (요약)

1. ~~**store.owner_id 관계 모델**~~ — **해결됨.** 안B(User가 storeId로 Store 소유) 확정(2026-08-21), `login_id`/`password` 레거시 컬럼 제거 완료.
2. **likes.(user_id, store_id) 복합 UK** — ~~미적용~~ **적용됨**(`uk_likes_user_store`, 2026-09-08).
3. **review.(user_id, store_id) 복합 UK** — "1인 1매장 1리뷰(덮어쓰기)" 정책이 맞다면 UK 추가 권장. 아직 미적용.
4. **report.(store_id, report_date) 복합 UK** — 일자별 리포트 중복 생성 방지. 아직 미적용.
5. **reservation.pickup_code UK** — ~~미적용~~ **적용됨**(`uk_reservation_pickup_code`, 2026-09-08).
6. **inquiry_comment 작성자 구분** — 문의 작성자 본인 댓글인지, 매장/운영자 답변인지 구분하는 컬럼이 없음(role/viewMode 세션 로직 확정 후 추가 검토로 유지 중).
7. **product.updated_at** — 상품 수정 이력을 별도로 추적하려면(WBS 3.0 김태훈) 전용 컬럼/이력 테이블 검토 필요(현재는 재고 변동에도 같이 갱신되는 범용 컬럼).
8. **CLAUDE.md 대비 미착수 테이블** — 카테고리 마스터(현재 `store.category` 자유 텍스트로만 존재)는 아직 별도 테이블 없음. 그 외 WBS 3.0~7.0 범위 기능은 이번 갱신 시점(2026-09-16) 기준 모두 테이블까지 반영 완료.

---

## 이번 갱신(2026-09-16)에서 새로 반영된 테이블

이전 버전(2026-08-27, 12개 엔티티) 대비 신규 반영: `menu_item`, `listing_template`, `payment`, `payment_cancel`, `coupon`, `coupon_policy`, `coupon_campaign`, `review_summary`, `notification`, `notification_setting`, `inquiry`, `inquiry_comment`, `complaint`, `settlement`, `user_badge` (기존 문서 미반영분 + 신규 기능 전부 포함, 총 24개 테이블).

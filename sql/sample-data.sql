-- =====================================================================
-- girigiri 로컬 테스트용 샘플 데이터
-- =====================================================================
-- 사용법:
--   1. 각자 로컬 .env로 앱을 한 번 실행 (./gradlew bootRun) 해서
--      ddl-auto=update로 테이블이 먼저 생성되게 한다.
--   2. 앱을 끄고 아래 스크립트를 자기 로컬 DB에 실행한다.
--        mysql -u root -p girigiri < sql/sample-data.sql
--   3. 테이블/컬럼명은 domain/entity/*.java의 @Table, @Column 값과 동일하게 맞춰뒀다.
--      엔티티가 바뀌면 이 파일도 함께 업데이트할 것.
--
-- 주의: password 컬럼은 실제 해시가 아니라 테스트용 평문 문자열이다.
--       Spring Security 연동 전 스캐폴딩 단계라 로그인 테스트에는 쓸 수 없다.
-- =====================================================================

-- 기존 데이터 초기화 (재실행 대비, FK 매핑이 아직 없어 순서 상관없이 삭제 가능)
DELETE FROM report;
DELETE FROM receipt;
DELETE FROM likes;
DELETE FROM review;
DELETE FROM reservation;
DELETE FROM product;
DELETE FROM store;
DELETE FROM users;

-- ---------------------------------------------------------------------
-- users (일반 소비자 2명 + 점주 계정 소유주 1명)
-- 변경됨 (2026-08-21) — 왜: dev 브랜치에 OAuth2 소셜 로그인을 포팅하면서 UserEntity가
-- login_id/password 없이 oauth_provider/oauth_id 필수 + email/region/profile_completed
-- 컬럼을 갖게 됐고, role도 USER/ADMIN 2종에서 USER(일반)/OWNER(점주)/ADMIN(운영자) 3종으로 바뀌었다.
-- 샘플 유저는 이미 온보딩을 마쳤다고 가정해 profile_completed=1로 넣는다.
-- ---------------------------------------------------------------------
INSERT INTO users (id, oauth_provider, oauth_id, role, nickname, email, region, profile_completed, latitude, longitude, created_at, updated_at) VALUES
(1, 'google', 'google_1001', 'USER', '구제왕나은', 'noeun@example.com', '서울 중구', 1, 37.566826, 126.978656, NOW(), NOW()),
(2, 'kakao', 'kakao_1001', 'USER', '알뜰소비자김태훈', NULL, '서울 중구', 1, 37.550000, 126.990000, NOW(), NOW()),
(3, 'google', 'google_1002', 'OWNER', '사장님송채현', 'songchaehyeon@example.com', '서울 중구', 1, 37.560000, 126.985000, NOW(), NOW());

-- ---------------------------------------------------------------------
-- store (users.id=3 이 소유한 매장 1곳)
-- ---------------------------------------------------------------------
INSERT INTO store (id, login_id, password, store_name, category, address, latitude, longitude, operating_hours, role, owner_id, created_at, updated_at) VALUES
(1, 'store01', 'test1234', '다이스키 베이커리', '베이커리', '서울시 중구 을지로 100', 37.560000, 126.985000, '09:00 ~ 22:00', 'ADMIN', 3, NOW(), NOW());

-- ---------------------------------------------------------------------
-- product (store.id=1의 마감세일 상품)
-- ---------------------------------------------------------------------
INSERT INTO product (id, store_id, name, original_price, discounted_price, quantity, remaining_quantity, image_url, description, status, registered_at) VALUES
(1, 1, '식빵 마감세트', 6000, 3000, 10, 4, '/images/product1.jpg', '오늘 구운 식빵, 마감 할인 50%', 'active', NOW()),
(2, 1, '크루아상 3개입', 9000, 4500, 5, 0, '/images/product2.jpg', '버터 크루아상 3개 세트', 'sold', NOW()),
(3, 1, '어제 만든 케이크', 15000, 6000, 3, 3, '/images/product3.jpg', '유통기한 임박 조각 케이크', 'expired', NOW());

-- ---------------------------------------------------------------------
-- reservation (users.id=1,2 의 예약 내역)
-- ---------------------------------------------------------------------
INSERT INTO reservation (id, user_id, product_id, product_name, store_id, reserved_quantity, total_price, pickup_time, pickup_code, status, reserved_at, picked_at) VALUES
(1, 1, 1, '식빵 마감세트', 1, 2, 6000, DATE_ADD(NOW(), INTERVAL 2 HOUR), 'PICK-1001', 'confirmed', NOW(), NULL),
(2, 2, 2, '크루아상 3개입', 1, 1, 4500, NOW(), 'PICK-1002', 'picked', NOW(), NOW()),
(3, 1, 3, '어제 만든 케이크', 1, 1, 6000, DATE_ADD(NOW(), INTERVAL 1 DAY), 'PICK-1003', 'pending', NOW(), NULL);

-- ---------------------------------------------------------------------
-- review (픽업 완료 건에 대한 리뷰)
-- ---------------------------------------------------------------------
INSERT INTO review (id, user_id, store_id, rating, content, created_at) VALUES
(1, 2, 1, 5, '빵이 신선하고 마감할인이라 정말 저렴했어요!', NOW());

-- ---------------------------------------------------------------------
-- likes (찜한 매장)
-- ---------------------------------------------------------------------
INSERT INTO likes (id, user_id, store_id, created_at) VALUES
(1, 1, 1, NOW()),
(2, 2, 1, NOW());

-- ---------------------------------------------------------------------
-- receipt (픽업 완료된 예약의 영수증)
-- ---------------------------------------------------------------------
INSERT INTO receipt (id, reservation_id, pdf_url, generated_at) VALUES
(1, 2, '/receipts/reservation-2.pdf', NOW());

-- ---------------------------------------------------------------------
-- report (매장의 오늘자 판매/폐기 리포트)
-- ---------------------------------------------------------------------
INSERT INTO report (id, store_id, report_date, registered_count, sold_count, expired_count, total_sales, total_discount, saved_co2, excel_url, pdf_url, generated_at) VALUES
(1, 1, CURDATE(), 3, 1, 1, 4500, 4500, 1.2, '/reports/store1-today.xlsx', '/reports/store1-today.pdf', NOW());

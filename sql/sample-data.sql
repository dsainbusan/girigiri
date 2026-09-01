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
-- 추가됨 (2026-08-27) — 왜: payment/payment_cancel/inquiry/inquiry_comment/notice 테이블이 엔티티에는
-- 이미 추가됐는데 이 초기화 목록엔 빠져 있었다. 그대로 두면 재실행 시 이 테이블들에 남은 이전 실행분과
-- 아래 새 INSERT의 고정 id가 충돌(중복 PK)한다.
DELETE FROM notice;
DELETE FROM inquiry_comment;
DELETE FROM inquiry;
DELETE FROM payment_cancel;
DELETE FROM payment;
DELETE FROM report;
DELETE FROM receipt;
DELETE FROM likes;
DELETE FROM review;
DELETE FROM reservation;
DELETE FROM product;
DELETE FROM store;
DELETE FROM users;

-- ---------------------------------------------------------------------
-- users (일반 소비자 2명 + 점주 계정 소유주 1명 + 운영자 1명 + 정지 회원 1명)
-- 변경됨 (2026-08-21) — 왜: dev 브랜치에 OAuth2 소셜 로그인을 포팅하면서 UserEntity가
-- login_id/password 없이 oauth_provider/oauth_id 필수 + email/region/profile_completed
-- 컬럼을 갖게 됐고, role도 USER/ADMIN 2종에서 USER(일반)/OWNER(점주)/ADMIN(운영자) 3종으로 바뀌었다.
-- 샘플 유저는 이미 온보딩을 마쳤다고 가정해 profile_completed=1로 넣는다.
-- 변경됨 — 왜: 슈퍼어드민 회원 관리 화면(/superadmin/members)을 실 데이터로 연동하면서
-- status(ACTIVE/SUSPENDED) 컬럼과, role/상태 다양성을 보여줄 ADMIN·정지 계정 샘플을 추가했다.
-- ---------------------------------------------------------------------
INSERT INTO users (id, oauth_provider, oauth_id, role, status, nickname, email, region, profile_completed, latitude, longitude, created_at, updated_at) VALUES
(1, 'google', 'google_1001', 'USER', 'ACTIVE', '구제왕나은', 'noeun@example.com', '서울 중구', 1, 37.566826, 126.978656, NOW(), NOW()),
(2, 'kakao', 'kakao_1001', 'USER', 'ACTIVE', '알뜰소비자김태훈', NULL, '서울 중구', 1, 37.550000, 126.990000, NOW(), NOW()),
(3, 'google', 'google_1002', 'OWNER', 'ACTIVE', '사장님송채현', 'songchaehyeon@example.com', '서울 중구', 1, 37.560000, 126.985000, NOW(), NOW()),
(4, 'email', 'admin@girigiri.com', 'ADMIN', 'ACTIVE', '운영자', 'admin@girigiri.com', '서울 중구', 1, 37.560000, 126.985000, NOW(), NOW()),
(5, 'kakao', 'kakao_1002', 'USER', 'SUSPENDED', '노쇼왕문창호', NULL, '서울 중구', 1, 37.552000, 126.988000, NOW(), NOW());

-- ---------------------------------------------------------------------
-- store (users.id=3 이 소유한 매장 1곳 + 입점 승인 대기 중인 신청 1건)
-- ---------------------------------------------------------------------
-- 변경됨 — 왜: login_id/password는 안A(Store 독립 계정) 흔적으로 제거 확정(안B로 정리, StoreEntity.java 참고).
-- role='ADMIN'도 CLAUDE.md 원안 표기가 남아있던 것 — 실제 점주 role은 users.role='OWNER'가 기준이라
-- 여기 role은 그대로 두되(제거 보류 중) 값만 'OWNER'로 맞춘다.
-- 변경됨 (2026-08-27) — 왜: phone/business_number/approval_status가 여태 비어있어서(NULL) 슈퍼어드민
-- 매장 상세(/superadmin/stores/1)의 긴급연락처·승인상태가 전부 빈 값으로 보였다. 값을 채우고
-- approval_status='APPROVED'로 명시(안 그러면 "입점 승인 대기" 필터링과 뒤섞여 애매해짐).
-- 추가됨 — 왜: "입점 승인 대기" 목록이 항상 비어 보이던 문제 — 슈퍼어드민 승인 버튼 데모용으로
-- PENDING 상태 매장 신청 1건을 users.id=2(알뜰소비자김태훈)의 신청으로 추가한다.
INSERT INTO store (id, store_name, category, address, latitude, longitude, operating_hours, phone, business_number, role, owner_id, approval_status, created_at, updated_at) VALUES
(1, '다이스키 베이커리', '베이커리', '서울시 중구 을지로 100', 37.560000, 126.985000, '09:00 ~ 22:00', '02-1234-5678', '123-45-67890', 'OWNER', 3, 'APPROVED', NOW(), NOW()),
(2, '동네빵집 청파점', '베이커리', '서울시 용산구 청파로 10', 37.541000, 126.965000, '08:00 ~ 20:00', '02-111-2222', '222-11-22222', 'OWNER', 2, 'PENDING', '2026-08-20 09:00:00', '2026-08-20 09:00:00');

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
INSERT INTO review (id, user_id, store_id, rating, content, edited, created_at) VALUES
(1, 2, 1, 5, '빵이 신선하고 마감할인이라 정말 저렴했어요!', 0, NOW());

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

-- ---------------------------------------------------------------------
-- inquiry / inquiry_comment (슈퍼어드민 "매장 문의"/"유저 문의" 답변 데모용, 2026-08-27 추가)
-- store_id가 있으면 매장 문의(store.id=1 대상, 작성자는 그 매장 점주 users.id=3),
-- 없으면 서비스 전체 문의(작성자는 일반 소비자 users.id=1,2).
-- 각 구분마다 1건은 미답변(대기), 1건은 답변완료로 둬서 두 상태가 다 보이게 한다.
-- ---------------------------------------------------------------------
INSERT INTO inquiry (id, user_id, store_id, title, content, created_at) VALUES
(1, 3, 1, '이번 달 정산 내역이 안 맞아요', '이번 달 정산 금액이 실제 판매액과 다르게 집계되는 것 같아요. 확인 부탁드립니다.', '2026-08-21 10:00:00'),
(2, 3, 1, '상품 등록이 자꾸 실패해요', '상품 등록 버튼을 누르면 오류가 나요.', '2026-08-18 14:00:00'),
(3, 2, NULL, '환불이 안돼요', '결제 취소했는데 환불이 아직 안 됐어요.', '2026-08-21 11:00:00'),
(4, 1, NULL, '픽업 QR이 인식이 안돼요', '매장에서 QR 스캔이 안 된다고 해요.', '2026-08-18 09:00:00');

INSERT INTO inquiry_comment (id, inquiry_id, user_id, content, created_at) VALUES
(1, 2, 4, '확인해보니 이미지 용량 제한 문제였습니다. 5MB 이하로 다시 시도해 주세요.', '2026-08-19 09:00:00'),
(2, 4, 4, 'QR 스캐너 앱 업데이트 후 정상 작동 확인했습니다. 감사합니다.', '2026-08-19 10:00:00');

-- ---------------------------------------------------------------------
-- notice (슈퍼어드민 공지사항, 2026-08-27 추가)
-- ---------------------------------------------------------------------
INSERT INTO notice (id, title, content, published, created_at, updated_at) VALUES
(1, '추석 연휴 픽업 운영 안내', '추석 연휴 기간(9/24~9/27) 매장별 픽업 운영시간이 다를 수 있습니다. 이용에 참고 부탁드립니다.', 1, '2026-08-18 10:00:00', '2026-08-18 10:00:00'),
(2, '서비스 정식 오픈 안내', '기리기리가 정식 오픈했습니다! 많은 이용 부탁드립니다.', 1, '2026-08-10 10:00:00', '2026-08-10 10:00:00');

-- ---------------------------------------------------------------------
-- complaint (슈퍼어드민 "신고 접수" 탭, 2026-09-01 추가) — 1건은 미답변(대기), 1건은 답변완료로 둬서
-- 두 상태가 다 보이게 한다(inquiry와 동일한 관례). "신고자/매장 클릭하면 디테일로" 요청으로
-- reporter_id/target_store_id를 실제 시드 회원(users.id=2,5)·매장(store.id=1,2)에 맞춰 채운다.
-- ---------------------------------------------------------------------
INSERT INTO complaint (id, target_name, target_store_id, reason, content, reporter_name, reporter_id, status, admin_reply, created_at, resolved_at) VALUES
(1, '다이스키 베이커리', 1, '상품 상태 불량', '포장 상태가 좋지 않고 유통기한이 임박한 상품이 섞여 있었습니다. 확인 부탁드립니다.', '알뜰소비자김태훈', 2, 'PENDING', NULL, '2026-08-19 13:20:00', NULL),
(2, '동네빵집 청파점', 2, '노쇼 과다 청구', '예약을 취소했는데 노쇼로 처리되어 위약금이 청구됐어요. 취소 시각을 확인해 주세요.', '노쇼왕문창호', 5, 'RESOLVED', '확인해보니 취소 접수가 픽업 시간 이후로 늦게 처리된 케이스였습니다. 위약금은 취소 처리했습니다.', '2026-08-17 09:40:00', '2026-08-18 11:15:00');

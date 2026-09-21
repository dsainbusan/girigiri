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
--
-- 대량 확충 (2026-09-21) — 왜: 지도/홈 화면이 매장 1곳뿐이라 텅 비어 보이고, 점주 대시보드·
-- 가계부·뱃지·알림함처럼 데이터가 쌓여야 그림이 나오는 화면들이 전부 휑했다("샘플 데이터 왕창
-- 넣어달라"는 요청). 매장을 카테고리별(베이커리/카페/반찬/도시락)로 늘리고, 그만큼 상품·예약·
-- 리뷰·찜·알림·뱃지를 같이 채웠다. 좌표는 기존 서울 중구·용산 클러스터(37.54~37.57 / 126.96~126.99)
-- 안에 모아서 지도에서 자연스럽게 모여 보이게 했다.
-- =====================================================================

-- 기존 데이터 초기화 (재실행 대비, FK 매핑이 아직 없어 순서 상관없이 삭제 가능)
DELETE FROM notification;
DELETE FROM user_badge;
DELETE FROM complaint;
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
-- users (소비자 8명 + 점주 6명 + 운영자 1명, 총 15명)
-- 정지 회원(문창호) 1명, 입점 승인 대기 신청자(김태훈, role은 아직 USER) 1명 포함.
--
-- 운영자(id=4)만 oauth_provider='email'이라 실제로 로그인 테스트가 가능하다 — 비밀번호는
-- 이 세션에서 계속 쓴 테스트용 해시(평문 Test1234!)를 그대로 넣었다. 나머지는 kakao/google
-- 더미 계정이라 실제 소셜 로그인으로는 들어갈 수 없다(로그인 테스트가 필요하면 오너 계정용
-- QA 계정을 직접 만들 것 — 이 세션에서 반복한 qa-*@test.local 패턴 참고).
-- ---------------------------------------------------------------------
INSERT INTO users (id, oauth_provider, oauth_id, role, status, nickname, email, password, region, profile_completed, terms_agreed, privacy_agreed, marketing_agreed, representative_badge, savings_goal_amount, latitude, longitude, created_at, updated_at) VALUES
(1, 'google', 'google_1001', 'USER', 'ACTIVE', '구제왕나은', 'noeun@example.com', NULL, '서울 중구', 1, 1, 1, 0, 'RESCUE_5', 30000, 37.566826, 126.978656, NOW(), NOW()),
(2, 'kakao', 'kakao_1001', 'USER', 'ACTIVE', '알뜰소비자김태훈', NULL, NULL, '서울 중구', 1, 1, 1, 0, NULL, NULL, 37.550000, 126.990000, NOW(), NOW()),
(3, 'google', 'google_1002', 'OWNER', 'ACTIVE', '사장님송채현', 'songchaehyeon@example.com', NULL, '서울 중구', 1, 1, 1, 0, NULL, NULL, 37.560000, 126.985000, NOW(), NOW()),
(4, 'email', 'admin@girigiri.com', 'ADMIN', 'ACTIVE', '운영자', 'admin@girigiri.com', '$2y$10$6nyyAwiBHlqcou0BthHFS.S52XXI9AHKHKeBKL70nQKcJVWHBiCGG', '서울 중구', 1, 1, 1, 0, NULL, NULL, 37.560000, 126.985000, NOW(), NOW()),
(5, 'kakao', 'kakao_1002', 'USER', 'SUSPENDED', '노쇼왕문창호', NULL, NULL, '서울 중구', 1, 1, 1, 0, NULL, NULL, 37.552000, 126.988000, NOW(), NOW()),
(6, 'kakao', 'kakao_1003', 'OWNER', 'ACTIVE', '브런치사장이도현', 'brunch.on@example.com', NULL, '서울 용산구', 1, 1, 1, 0, NULL, NULL, 37.565100, 126.976900, NOW(), NOW()),
(7, 'google', 'google_1003', 'OWNER', 'ACTIVE', '커피사장박서준', 'coffeewow@example.com', NULL, '서울 중구', 1, 1, 1, 0, NULL, NULL, 37.558000, 126.991000, NOW(), NOW()),
(8, 'kakao', 'kakao_1004', 'OWNER', 'ACTIVE', '반찬사장정미경', 'ommaban@example.com', NULL, '서울 용산구', 1, 1, 1, 0, NULL, NULL, 37.549500, 126.970000, NOW(), NOW()),
(9, 'google', 'google_1004', 'OWNER', 'ACTIVE', '정성반찬사장김윤호', 'jungsungban@example.com', NULL, '서울 용산구', 1, 1, 1, 0, NULL, NULL, 37.543000, 126.968000, NOW(), NOW()),
(10, 'kakao', 'kakao_1005', 'OWNER', 'ACTIVE', '도시락사장한상우', 'dundunlunch@example.com', NULL, '서울 중구', 1, 1, 1, 0, NULL, NULL, 37.558300, 126.978400, NOW(), NOW()),
(11, 'google', 'google_1005', 'OWNER', 'ACTIVE', '매일도시락사장오세영', 'maeillunch@example.com', NULL, '서울 용산구', 1, 1, 1, 0, NULL, NULL, 37.545500, 126.991700, NOW(), NOW()),
(12, 'kakao', 'kakao_1006', 'USER', 'ACTIVE', '자취생박지민', NULL, NULL, '서울 중구', 1, 1, 1, 0, 'RESCUE_1', NULL, 37.560500, 126.980200, NOW(), NOW()),
(13, 'google', 'google_1006', 'USER', 'ACTIVE', '다이어터최유나', 'yunachoi@example.com', NULL, '서울 중구', 1, 1, 1, 0, NULL, 50000, 37.562300, 126.983100, NOW(), NOW()),
(14, 'kakao', 'kakao_1007', 'USER', 'ACTIVE', '야근러강태양', NULL, NULL, '서울 용산구', 1, 1, 1, 0, NULL, NULL, 37.558700, 126.986300, NOW(), NOW()),
(15, 'google', 'google_1007', 'USER', 'ACTIVE', '대학생윤서아', 'seoayoon@example.com', NULL, '서울 중구', 1, 1, 1, 0, 'SAVE_10K', 20000, 37.554200, 126.975500, NOW(), NOW());

-- ---------------------------------------------------------------------
-- store (승인 매장 7곳 — 베이커리1/카페2/반찬2/도시락2 + 입점 승인 대기 1건)
-- ---------------------------------------------------------------------
INSERT INTO store (id, store_name, category, address, latitude, longitude, operating_hours, phone, business_number, role, owner_id, approval_status, reliability_suspension_count, reliability_banned, created_at, updated_at) VALUES
(1, '다이스키 베이커리', '베이커리', '서울시 중구 을지로 100', 37.560000, 126.985000, '09:00 ~ 22:00', '02-1234-5678', '123-45-67890', 'OWNER', 3, 'APPROVED', 0, 0, NOW(), NOW()),
(2, '동네빵집 청파점', '베이커리', '서울시 용산구 청파로 10', 37.541000, 126.965000, '08:00 ~ 20:00', '02-111-2222', '222-11-22222', 'OWNER', 2, 'PENDING', 0, 0, '2026-08-20 09:00:00', '2026-08-20 09:00:00'),
(3, '브런치카페 온', '카페', '서울시 용산구 이태원로 45', 37.565100, 126.976900, '08:00 ~ 21:00', '02-333-4444', '333-22-33333', 'OWNER', 6, 'APPROVED', 0, 0, NOW(), NOW()),
(4, '커피와우 명동점', '카페', '서울시 중구 명동길 20', 37.558000, 126.991000, '07:30 ~ 22:00', '02-444-5555', '444-33-44444', 'OWNER', 7, 'APPROVED', 0, 0, NOW(), NOW()),
(5, '엄마손반찬', '반찬', '서울시 용산구 원효로 60', 37.549500, 126.970000, '10:00 ~ 20:00', '02-555-6666', '555-44-55555', 'OWNER', 8, 'APPROVED', 0, 0, NOW(), NOW()),
(6, '정성반찬가게', '반찬', '서울시 용산구 한강대로 88', 37.543000, 126.968000, '09:30 ~ 20:30', '02-666-7777', '666-55-66666', 'OWNER', 9, 'APPROVED', 0, 0, NOW(), NOW()),
(7, '든든도시락', '도시락', '서울시 중구 다산로 30', 37.558300, 126.978400, '10:00 ~ 21:00', '02-777-8888', '777-66-77777', 'OWNER', 10, 'APPROVED', 0, 0, NOW(), NOW()),
(8, '매일도시락 용산점', '도시락', '서울시 용산구 한강대로 120', 37.545500, 126.991700, '10:30 ~ 21:30', '02-888-9999', '888-77-88888', 'OWNER', 11, 'APPROVED', 0, 0, NOW(), NOW());

-- ---------------------------------------------------------------------
-- product (매장당 3~4개, active/sold/expired 다양하게, 할인율도 다양하게)
-- ---------------------------------------------------------------------
INSERT INTO product (id, store_id, name, original_price, discounted_price, quantity, remaining_quantity, image_url, description, status, registered_at) VALUES
-- 다이스키 베이커리 (store 1)
(1, 1, '식빵 마감세트', 6000, 3000, 10, 4, '/images/product1.jpg', '오늘 구운 식빵, 마감 할인 50%', 'active', NOW()),
(2, 1, '크루아상 3개입', 9000, 4500, 5, 0, '/images/product2.jpg', '버터 크루아상 3개 세트', 'sold', NOW()),
(3, 1, '어제 만든 케이크', 15000, 6000, 3, 3, '/images/product3.jpg', '유통기한 임박 조각 케이크', 'expired', NOW()),
(4, 1, '단팥빵 5개입', 7000, 4200, 8, 5, '/images/product4.jpg', '팥이 꽉 찬 단팥빵 5개 세트', 'active', NOW()),
-- 브런치카페 온 (store 3)
(5, 3, '크로플 세트 (2개)', 8000, 4800, 6, 2, '/images/product5.jpg', '바삭한 크로플 2개 + 시럽', 'active', NOW()),
(6, 3, '오늘의 샌드위치', 7500, 3750, 4, 4, '/images/product6.jpg', '마감 임박 수제 샌드위치', 'active', NOW()),
(7, 3, '아메리카노 원두 마감', 12000, 6000, 2, 0, '/images/product7.jpg', '오늘 로스팅한 원두 봉지', 'sold', NOW()),
-- 커피와우 명동점 (store 4)
(8, 4, '베이글 2개 세트', 6500, 3200, 7, 3, '/images/product8.jpg', '플레인/에브리싱 베이글 2개', 'active', NOW()),
(9, 4, '디저트 3종 모음', 11000, 5500, 3, 1, '/images/product9.jpg', '마감 임박 디저트 3종', 'active', NOW()),
(10, 4, '어제 구운 스콘 4개', 9000, 3600, 4, 4, '/images/product10.jpg', '유통기한 임박 스콘 4개입', 'expired', NOW()),
-- 엄마손반찬 (store 5)
(11, 5, '오늘의 나물 반찬세트', 12000, 7200, 6, 2, '/images/product11.jpg', '3가지 제철 나물 반찬 세트', 'active', NOW()),
(12, 5, '잡채 한 팩', 9000, 5400, 5, 5, '/images/product12.jpg', '당일 조리 잡채 500g', 'active', NOW()),
(13, 5, '계란말이 + 진미채', 8000, 4000, 4, 0, '/images/product13.jpg', '밑반찬 2종 세트', 'sold', NOW()),
-- 정성반찬가게 (store 6)
(14, 6, '김치찌개용 김치 1kg', 10000, 6000, 5, 3, '/images/product14.jpg', '숙성 배추김치 1kg', 'active', NOW()),
(15, 6, '멸치볶음 + 콩자반', 7000, 3500, 6, 6, '/images/product15.jpg', '밑반찬 2종 세트', 'active', NOW()),
(16, 6, '어제 만든 불고기', 14000, 5600, 2, 2, '/images/product16.jpg', '유통기한 임박 양념 불고기', 'expired', NOW()),
-- 든든도시락 (store 7)
(17, 7, '제육볶음 도시락', 8500, 4250, 8, 4, '/images/product17.jpg', '오늘의 제육볶음 도시락', 'active', NOW()),
(18, 7, '오늘의 도시락 (랜덤)', 7000, 3500, 10, 6, '/images/product18.jpg', '남은 반찬으로 구성한 랜덤 도시락', 'active', NOW()),
(19, 7, '돈까스 도시락', 9000, 3600, 3, 0, '/images/product19.jpg', '바삭한 돈까스 도시락', 'sold', NOW()),
-- 매일도시락 용산점 (store 8)
(20, 8, '샐러드 도시락', 8000, 4800, 5, 2, '/images/product20.jpg', '건강한 샐러드+닭가슴살 도시락', 'active', NOW()),
(21, 8, '김밥 3줄 세트', 6000, 3000, 6, 3, '/images/product21.jpg', '당일 마감 김밥 3줄', 'active', NOW()),
(22, 8, '어제 만든 불고기 도시락', 8500, 3400, 2, 2, '/images/product22.jpg', '유통기한 임박 불고기 도시락', 'expired', NOW());

-- ---------------------------------------------------------------------
-- reservation (상태 다양하게: pending/confirmed/ready/picked/cancelled/noshowed)
-- 픽업완료(picked) 건은 리뷰·영수증·정산·가계부 집계와 이어지므로 이번 달 위주로 넉넉히 넣는다.
-- ---------------------------------------------------------------------
INSERT INTO reservation (id, user_id, product_id, product_name, store_id, reserved_quantity, total_price, pickup_time, pickup_code, status, reserved_at, picked_at, accepted_at, cancel_reason, cancelled_by) VALUES
(1, 1, 1, '식빵 마감세트', 1, 2, 6000, DATE_ADD(NOW(), INTERVAL 2 HOUR), 'PICK-1001', 'confirmed', NOW(), NULL, NOW(), NULL, NULL),
(2, 2, 2, '크루아상 3개입', 1, 1, 4500, NOW(), 'PICK-1002', 'picked', DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), NULL, NULL),
(3, 1, 3, '어제 만든 케이크', 1, 1, 6000, DATE_ADD(NOW(), INTERVAL 1 DAY), 'PICK-1003', 'pending', NOW(), NULL, NULL, NULL, NULL),
(4, 12, 4, '단팥빵 5개입', 1, 1, 4200, DATE_SUB(NOW(), INTERVAL 3 DAY), 'PICK-1004', 'picked', DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), NULL, NULL),
(5, 13, 5, '크로플 세트 (2개)', 3, 1, 4800, DATE_SUB(NOW(), INTERVAL 1 DAY), 'PICK-1005', 'picked', DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), NULL, NULL),
(6, 14, 6, '오늘의 샌드위치', 3, 1, 3750, DATE_ADD(NOW(), INTERVAL 3 HOUR), 'PICK-1006', 'ready', NOW(), NULL, NOW(), NULL, NULL),
(7, 15, 8, '베이글 2개 세트', 4, 1, 3200, DATE_SUB(NOW(), INTERVAL 5 DAY), 'PICK-1007', 'picked', DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY), NULL, NULL),
(8, 1, 9, '디저트 3종 모음', 4, 1, 5500, DATE_ADD(NOW(), INTERVAL 4 HOUR), 'PICK-1008', 'confirmed', NOW(), NULL, NOW(), NULL, NULL),
(9, 12, 11, '오늘의 나물 반찬세트', 5, 1, 7200, DATE_SUB(NOW(), INTERVAL 4 DAY), 'PICK-1009', 'picked', DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY), NULL, NULL),
(10, 13, 12, '잡채 한 팩', 5, 2, 10800, DATE_SUB(NOW(), INTERVAL 10 DAY), 'PICK-1010', 'picked', DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_SUB(NOW(), INTERVAL 10 DAY), NULL, NULL),
(11, 14, 14, '김치찌개용 김치 1kg', 6, 1, 6000, DATE_SUB(NOW(), INTERVAL 6 DAY), 'PICK-1011', 'picked', DATE_SUB(NOW(), INTERVAL 6 DAY), DATE_SUB(NOW(), INTERVAL 6 DAY), DATE_SUB(NOW(), INTERVAL 6 DAY), NULL, NULL),
(12, 15, 15, '멸치볶음 + 콩자반', 6, 1, 3500, DATE_ADD(NOW(), INTERVAL 6 HOUR), 'PICK-1012', 'confirmed', NOW(), NULL, NOW(), NULL, NULL),
(13, 2, 17, '제육볶음 도시락', 7, 1, 4250, DATE_SUB(NOW(), INTERVAL 7 DAY), 'PICK-1013', 'picked', DATE_SUB(NOW(), INTERVAL 7 DAY), DATE_SUB(NOW(), INTERVAL 7 DAY), DATE_SUB(NOW(), INTERVAL 7 DAY), NULL, NULL),
(14, 12, 18, '오늘의 도시락 (랜덤)', 7, 2, 7000, DATE_SUB(NOW(), INTERVAL 35 DAY), 'PICK-1014', 'picked', DATE_SUB(NOW(), INTERVAL 35 DAY), DATE_SUB(NOW(), INTERVAL 35 DAY), DATE_SUB(NOW(), INTERVAL 35 DAY), NULL, NULL),
(15, 13, 20, '샐러드 도시락', 8, 1, 4800, DATE_SUB(NOW(), INTERVAL 40 DAY), 'PICK-1015', 'picked', DATE_SUB(NOW(), INTERVAL 40 DAY), DATE_SUB(NOW(), INTERVAL 40 DAY), DATE_SUB(NOW(), INTERVAL 40 DAY), NULL, NULL),
(16, 14, 21, '김밥 3줄 세트', 8, 1, 3000, DATE_SUB(NOW(), INTERVAL 45 DAY), 'PICK-1016', 'picked', DATE_SUB(NOW(), INTERVAL 45 DAY), DATE_SUB(NOW(), INTERVAL 45 DAY), DATE_SUB(NOW(), INTERVAL 45 DAY), NULL, NULL),
(17, 15, 1, '식빵 마감세트', 1, 1, 3000, DATE_SUB(NOW(), INTERVAL 1 DAY), 'PICK-1017', 'cancelled', DATE_SUB(NOW(), INTERVAL 1 DAY), NULL, DATE_SUB(NOW(), INTERVAL 1 DAY), '재고 소진으로 매장에서 취소', 'STORE'),
(18, 5, 5, '크로플 세트 (2개)', 3, 1, 4800, DATE_SUB(NOW(), INTERVAL 2 DAY), 'PICK-1018', 'noshowed', DATE_SUB(NOW(), INTERVAL 2 DAY), NULL, DATE_SUB(NOW(), INTERVAL 2 DAY), NULL, NULL),
(19, 5, 11, '오늘의 나물 반찬세트', 5, 1, 7200, DATE_SUB(NOW(), INTERVAL 3 DAY), 'PICK-1019', 'cancelled', DATE_SUB(NOW(), INTERVAL 3 DAY), NULL, NULL, '단순 변심', 'USER'),
(20, 1, 17, '제육볶음 도시락', 7, 1, 4250, DATE_ADD(NOW(), INTERVAL 1 HOUR), 'PICK-1020', 'ready', NOW(), NULL, NOW(), NULL, NULL),
(21, 12, 8, '베이글 2개 세트', 4, 1, 3200, DATE_SUB(NOW(), INTERVAL 1 DAY), 'PICK-1021', 'picked', DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), NULL, NULL),
(22, 13, 4, '단팥빵 5개입', 1, 1, 4200, DATE_SUB(NOW(), INTERVAL 8 DAY), 'PICK-1022', 'picked', DATE_SUB(NOW(), INTERVAL 8 DAY), DATE_SUB(NOW(), INTERVAL 8 DAY), DATE_SUB(NOW(), INTERVAL 8 DAY), NULL, NULL),
(23, 14, 20, '샐러드 도시락', 8, 1, 4800, DATE_SUB(NOW(), INTERVAL 9 DAY), 'PICK-1023', 'picked', DATE_SUB(NOW(), INTERVAL 9 DAY), DATE_SUB(NOW(), INTERVAL 9 DAY), DATE_SUB(NOW(), INTERVAL 9 DAY), NULL, NULL),
(24, 15, 21, '김밥 3줄 세트', 8, 2, 6000, DATE_SUB(NOW(), INTERVAL 12 DAY), 'PICK-1024', 'picked', DATE_SUB(NOW(), INTERVAL 12 DAY), DATE_SUB(NOW(), INTERVAL 12 DAY), DATE_SUB(NOW(), INTERVAL 12 DAY), NULL, NULL),
(25, 2, 18, '오늘의 도시락 (랜덤)', 7, 1, 3500, DATE_ADD(NOW(), INTERVAL 5 HOUR), 'PICK-1025', 'confirmed', NOW(), NULL, NOW(), NULL, NULL),
(26, 1, 12, '잡채 한 팩', 5, 1, 5400, DATE_SUB(NOW(), INTERVAL 5 HOUR), 'PICK-1026', 'picked', DATE_SUB(NOW(), INTERVAL 5 HOUR), DATE_SUB(NOW(), INTERVAL 5 HOUR), DATE_SUB(NOW(), INTERVAL 5 HOUR), NULL, NULL);

-- ---------------------------------------------------------------------
-- review (픽업 완료 건에 대한 리뷰 — 일부는 사장님 답글까지 채워서 답글 기능도 보이게 한다)
-- ---------------------------------------------------------------------
INSERT INTO review (id, user_id, store_id, rating, content, edited, reply_edited, image_url, reply_content, reply_created_at, created_at) VALUES
(1, 2, 1, 5, '빵이 신선하고 마감할인이라 정말 저렴했어요!', 0, 0, NULL, '맛있게 드셔주셔서 감사합니다! 다음에도 좋은 빵으로 찾아뵐게요 :)', DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY)),
(2, 12, 1, 4, '단팥빵이 생각보다 양이 많아서 좋았어요. 재구매 의사 있습니다.', 0, 0, NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 3 DAY)),
(3, 13, 3, 5, '크로플이 아직 바삭해서 놀랐어요! 커피랑 같이 먹으니 최고.', 0, 0, NULL, '감사합니다 :) 마감 시간대에도 최대한 신선하게 준비하고 있어요!', DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY)),
(4, 15, 4, 4, '베이글 두 개 다 신선했어요. 다만 양이 조금 적은 느낌.', 0, 0, NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 5 DAY)),
(5, 12, 5, 5, '나물 반찬 세트 진짜 집밥 느낌 나요. 자취생한테 최고!', 0, 0, NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 4 DAY)),
(6, 13, 5, 3, '잡채가 조금 짰지만 양은 만족스러웠어요.', 0, 0, NULL, '피드백 감사합니다! 간을 조금 더 신경 써서 준비할게요.', DATE_SUB(NOW(), INTERVAL 9 DAY), DATE_SUB(NOW(), INTERVAL 10 DAY)),
(7, 14, 6, 5, '김치가 잘 익어서 찌개 끓이기 딱 좋았어요.', 0, 0, NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 6 DAY)),
(8, 2, 7, 4, '제육볶음 도시락 양 많고 맛있어요. 마감 할인이라 더 만족.', 0, 0, NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 7 DAY)),
(9, 13, 8, 5, '샐러드 도시락 신선하고 닭가슴살도 부드러웠어요.', 0, 0, NULL, '항상 이용해주셔서 감사해요! 앞으로도 좋은 재료로 준비할게요.', DATE_SUB(NOW(), INTERVAL 39 DAY), DATE_SUB(NOW(), INTERVAL 40 DAY)),
(10, 14, 8, 4, '김밥 3줄이 한 끼로 딱 좋았습니다.', 0, 0, NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 9 DAY));

-- ---------------------------------------------------------------------
-- likes (찜한 매장 — 여러 유저가 여러 매장을 찜하도록 다양하게)
-- ---------------------------------------------------------------------
INSERT INTO likes (id, user_id, store_id, created_at) VALUES
(1, 1, 1, NOW()),
(2, 2, 1, NOW()),
(3, 1, 3, NOW()),
(4, 1, 7, NOW()),
(5, 12, 1, NOW()),
(6, 12, 5, NOW()),
(7, 12, 7, NOW()),
(8, 13, 3, NOW()),
(9, 13, 4, NOW()),
(10, 13, 8, NOW()),
(11, 14, 6, NOW()),
(12, 14, 8, NOW()),
(13, 15, 1, NOW()),
(14, 15, 4, NOW()),
(15, 15, 8, NOW()),
(16, 2, 7, NOW()),
(17, 5, 3, NOW());

-- ---------------------------------------------------------------------
-- receipt (픽업 완료된 예약의 영수증 — picked 상태인 예약과 1:1)
-- ---------------------------------------------------------------------
INSERT INTO receipt (id, reservation_id, pdf_url, generated_at) VALUES
(1, 2, '/receipts/reservation-2.pdf', DATE_SUB(NOW(), INTERVAL 2 DAY)),
(2, 4, '/receipts/reservation-4.pdf', DATE_SUB(NOW(), INTERVAL 3 DAY)),
(3, 5, '/receipts/reservation-5.pdf', DATE_SUB(NOW(), INTERVAL 1 DAY)),
(4, 7, '/receipts/reservation-7.pdf', DATE_SUB(NOW(), INTERVAL 5 DAY)),
(5, 9, '/receipts/reservation-9.pdf', DATE_SUB(NOW(), INTERVAL 4 DAY)),
(6, 10, '/receipts/reservation-10.pdf', DATE_SUB(NOW(), INTERVAL 10 DAY)),
(7, 11, '/receipts/reservation-11.pdf', DATE_SUB(NOW(), INTERVAL 6 DAY)),
(8, 13, '/receipts/reservation-13.pdf', DATE_SUB(NOW(), INTERVAL 7 DAY)),
(9, 14, '/receipts/reservation-14.pdf', DATE_SUB(NOW(), INTERVAL 35 DAY)),
(10, 15, '/receipts/reservation-15.pdf', DATE_SUB(NOW(), INTERVAL 40 DAY)),
(11, 16, '/receipts/reservation-16.pdf', DATE_SUB(NOW(), INTERVAL 45 DAY)),
(12, 21, '/receipts/reservation-21.pdf', DATE_SUB(NOW(), INTERVAL 1 DAY)),
(13, 22, '/receipts/reservation-22.pdf', DATE_SUB(NOW(), INTERVAL 8 DAY)),
(14, 23, '/receipts/reservation-23.pdf', DATE_SUB(NOW(), INTERVAL 9 DAY)),
(15, 24, '/receipts/reservation-24.pdf', DATE_SUB(NOW(), INTERVAL 12 DAY)),
(16, 26, '/receipts/reservation-26.pdf', DATE_SUB(NOW(), INTERVAL 5 HOUR));

-- ---------------------------------------------------------------------
-- report (승인 매장 7곳의 오늘자 판매/폐기 리포트)
-- ---------------------------------------------------------------------
INSERT INTO report (id, store_id, report_date, registered_count, sold_count, expired_count, total_sales, total_discount, saved_co2, excel_url, pdf_url, generated_at) VALUES
(1, 1, CURDATE(), 4, 2, 1, 7500, 7500, 1.6, '/reports/store1-today.xlsx', '/reports/store1-today.pdf', NOW()),
(2, 3, CURDATE(), 3, 1, 0, 4800, 4200, 0.8, '/reports/store3-today.xlsx', '/reports/store3-today.pdf', NOW()),
(3, 4, CURDATE(), 3, 1, 1, 3200, 5800, 0.9, '/reports/store4-today.xlsx', '/reports/store4-today.pdf', NOW()),
(4, 5, CURDATE(), 3, 1, 0, 7200, 4800, 1.1, '/reports/store5-today.xlsx', '/reports/store5-today.pdf', NOW()),
(5, 6, CURDATE(), 3, 0, 1, 0, 0, 0.0, '/reports/store6-today.xlsx', '/reports/store6-today.pdf', NOW()),
(6, 7, CURDATE(), 3, 2, 0, 7750, 8250, 1.7, '/reports/store7-today.xlsx', '/reports/store7-today.pdf', NOW()),
(7, 8, CURDATE(), 3, 1, 1, 4800, 3200, 0.8, '/reports/store8-today.xlsx', '/reports/store8-today.pdf', NOW());

-- ---------------------------------------------------------------------
-- notification (알림함 데모용 — 읽음/안읽음 섞어서)
-- ---------------------------------------------------------------------
INSERT INTO notification (id, user_id, type, message, link_url, source_key, is_read, created_at) VALUES
(1, 1, 'RESERVATION_CONFIRMED', '"식빵 마감세트" 예약 확정', '/reservation/my', 'reservation_confirmed:1', 0, NOW()),
(2, 1, 'REVIEW_REPLY', '내 리뷰에 사장님이 답글을 남겼어요', '/user/reviews/my', 'review_reply:1', 0, DATE_SUB(NOW(), INTERVAL 2 DAY)),
(3, 1, 'LIKE_STORE_OPEN', '찜한 "든든도시락"에서 마감세일이 시작됐어요', '/user/stores/7', 'like_store_open:1:7', 1, DATE_SUB(NOW(), INTERVAL 1 DAY)),
(4, 2, 'RESERVATION_PICKUP_SOON', '"제육볶음 도시락" 픽업 시간이 다가와요', '/reservation/my', 'reservation_pickup_soon:13', 1, DATE_SUB(NOW(), INTERVAL 7 DAY)),
(5, 12, 'WELCOME_COUPON', '가입을 환영해요! 첫 예약 쿠폰이 발급됐어요', '/mypage', 'welcome_coupon:12', 1, DATE_SUB(NOW(), INTERVAL 20 DAY)),
(6, 12, 'REVIEW_REPLY', '내 리뷰에 사장님이 답글을 남겼어요', '/user/reviews/my', 'review_reply:2', 0, DATE_SUB(NOW(), INTERVAL 3 DAY)),
(7, 13, 'RESERVATION_CONFIRMED', '"크로플 세트 (2개)" 예약 확정', '/reservation/my', 'reservation_confirmed:5', 1, DATE_SUB(NOW(), INTERVAL 1 DAY)),
(8, 5, 'RESERVATION_NOSHOW', '"크로플 세트 (2개)" 픽업 시간 경과 (노쇼 처리)', '/reservation/my', 'reservation_noshow:18', 0, DATE_SUB(NOW(), INTERVAL 2 DAY)),
(9, 14, 'LIKE_STORE_OPEN', '찜한 "정성반찬가게"에서 마감세일이 시작됐어요', '/user/stores/6', 'like_store_open:14:6', 1, DATE_SUB(NOW(), INTERVAL 6 DAY)),
(10, 15, 'RESERVATION_CONFIRMED', '"김밥 3줄 세트" 예약 확정', '/reservation/my', 'reservation_confirmed:24', 0, DATE_SUB(NOW(), INTERVAL 12 DAY));

-- ---------------------------------------------------------------------
-- user_badge (구제/절약 뱃지 — representative_badge로 지정한 값 포함)
-- ---------------------------------------------------------------------
INSERT INTO user_badge (id, user_id, badge_code, earned_at, notified) VALUES
(1, 1, 'RESCUE_1', DATE_SUB(NOW(), INTERVAL 10 DAY), 1),
(2, 1, 'RESCUE_5', DATE_SUB(NOW(), INTERVAL 5 HOUR), 1),
(3, 12, 'RESCUE_1', DATE_SUB(NOW(), INTERVAL 1 DAY), 1),
(4, 13, 'RESCUE_1', DATE_SUB(NOW(), INTERVAL 40 DAY), 1),
(5, 13, 'SAVE_10K', DATE_SUB(NOW(), INTERVAL 9 DAY), 1),
(6, 15, 'RESCUE_1', DATE_SUB(NOW(), INTERVAL 12 DAY), 1),
(7, 15, 'SAVE_10K', DATE_SUB(NOW(), INTERVAL 12 DAY), 1),
(8, 14, 'RESCUE_1', DATE_SUB(NOW(), INTERVAL 9 DAY), 1);

-- ---------------------------------------------------------------------
-- inquiry / inquiry_comment (슈퍼어드민 "매장 문의"/"유저 문의" 답변 데모용, 2026-08-27 추가)
-- store_id가 있으면 매장 문의, 없으면 서비스 전체 문의.
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
-- complaint (슈퍼어드민 "신고 접수" 탭, 2026-09-01 추가) — 1건은 미답변(대기), 1건은 답변완료.
-- ---------------------------------------------------------------------
INSERT INTO complaint (id, target_name, target_store_id, reason, content, reporter_name, reporter_id, status, admin_reply, created_at, resolved_at) VALUES
(1, '다이스키 베이커리', 1, '상품 상태 불량', '포장 상태가 좋지 않고 유통기한이 임박한 상품이 섞여 있었습니다. 확인 부탁드립니다.', '알뜰소비자김태훈', 2, 'PENDING', NULL, '2026-08-19 13:20:00', NULL),
(2, '동네빵집 청파점', 2, '노쇼 과다 청구', '예약을 취소했는데 노쇼로 처리되어 위약금이 청구됐어요. 취소 시각을 확인해 주세요.', '노쇼왕문창호', 5, 'RESOLVED', '확인해보니 취소 접수가 픽업 시간 이후로 늦게 처리된 케이스였습니다. 위약금은 취소 처리했습니다.', '2026-08-17 09:40:00', '2026-08-18 11:15:00');

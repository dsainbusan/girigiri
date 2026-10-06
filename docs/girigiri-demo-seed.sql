-- =====================================================================
-- 기리기리 발표 시연용 샘플 데이터 (송채현, 2026-10-06)
-- =====================================================================
-- 목적: 발표 당일 다른 컴퓨터에서 로컬 MySQL에 그대로 import해서, 앱을 켜자마자
--       지도/매장/예약/정산/챗봇 등 전체 화면이 바로 시연 가능한 상태가 되게 한다.
--
-- 사용법 (발표용 컴퓨터에서):
--   1. 프로젝트를 내려받고 .env(DB 접속정보 등)를 채운 뒤, 앱을 한 번 실행해서
--      ddl-auto=update로 테이블을 먼저 생성한다 (./gradlew bootRun, 뜬 뒤 종료해도 됨).
--   2. 아래 명령으로 이 파일 하나만 그대로 넣는다:
--        mysql -u root -p --default-character-set=utf8mb4 girigiri < girigiri-demo-seed.sql
--      (기존 데이터는 전부 지우고 새로 채우므로, 리허설 중 몇 번을 다시 실행해도 항상
--       같은 상태로 리셋된다 — 시연 직전에 한 번 더 돌려도 안전하다.)
--
-- 구성: ① sql/sample-data.sql(+환불 테스트 데이터) 전체를 그대로 포함 — 회원/매장/상품/
--         예약/결제/리뷰/찜/알림/뱃지/문의/공지/신고/환불 등 핵심 24개 테이블.
--       ② 매장 신뢰도(취소율) 자동 정지 기능 시연용 매장 2곳 신규 추가 — 정지 중인 매장
--         1곳(2회차, 14일 정지 중), 영구정지된 매장 1곳. (담당: 송채현, StoreReliabilityService)
--       ③ 로그인 데모 계정(demo-user@test.local / Test1234!)에 실제 예약을 몇 건 넣어서,
--         챗봇에 "내 예약 확인해줘"라고 물으면 함수 호출(getCancelEligibilityForUser)로
--         진짜 데이터가 나오게 함. (담당: 송채현, ChatService)
--       ④ sql/sample-coupon-data.sql, sql/sample-region-data.sql 내용 그대로 포함 —
--         쿠폰 캠페인 관리·지역별 현황 화면도 같이 시연 가능하게.
--
-- 로그인 테스트 가능 계정 (나머지는 kakao/google 더미라 실제 소셜 로그인 불가):
--   - 운영자(슈퍼어드민): admin@girigiri.com / Test1234!
--   - 일반회원(데모용, 챗봇 시연용 예약 포함): demo-user@test.local / Test1234!
-- =====================================================================

SET FOREIGN_KEY_CHECKS=0;

-- 기존 데이터 전체 초기화 (재실행 대비 — FK 체크 꺼뒀으니 순서 상관없음)
DELETE FROM refund;
DELETE FROM reservation_status_history;
DELETE FROM notification;
DELETE FROM notification_setting;
DELETE FROM user_badge;
DELETE FROM complaint;
DELETE FROM notice;
DELETE FROM inquiry_comment;
DELETE FROM inquiry;
DELETE FROM payment_cancel;
DELETE FROM payment;
DELETE FROM receipt;
DELETE FROM likes;
DELETE FROM review_summary;
DELETE FROM review;
DELETE FROM reservation;
DELETE FROM coupon;
DELETE FROM coupon_region;
DELETE FROM coupon_store;
DELETE FROM coupon_campaign;
DELETE FROM coupon_policy;
DELETE FROM store_announcement;
DELETE FROM product;
DELETE FROM listing_template;
DELETE FROM menu_item;
DELETE FROM settlement;
DELETE FROM store;
DELETE FROM user_social_accounts;
DELETE FROM user_archive;
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
-- image_url: 실제 가게 사진 대신 안정적으로 핫링크 가능한 플레이스홀더(Lorem Picsum, seed 고정이라
-- 새로고침해도 매번 같은 사진). 실제 사진으로 교체하려면 이 컬럼 값만 바꾸면 된다.
INSERT INTO store (id, store_name, category, address, latitude, longitude, operating_hours, phone, business_number, role, owner_id, approval_status, reliability_suspension_count, reliability_banned, image_url, created_at, updated_at) VALUES
(1, '다이스키 베이커리', '베이커리', '서울시 중구 을지로 100', 37.560000, 126.985000, '09:00 ~ 22:00', '02-1234-5678', '123-45-67890', 'OWNER', 3, 'APPROVED', 0, 0, 'https://picsum.photos/seed/girigiri-store-1/600/400', NOW(), NOW()),
(2, '동네빵집 청파점', '베이커리', '서울시 용산구 청파로 10', 37.541000, 126.965000, '08:00 ~ 20:00', '02-111-2222', '222-11-22222', 'OWNER', 2, 'PENDING', 0, 0, 'https://picsum.photos/seed/girigiri-store-2/600/400', '2026-08-20 09:00:00', '2026-08-20 09:00:00'),
(3, '브런치카페 온', '카페', '서울시 용산구 이태원로 45', 37.565100, 126.976900, '08:00 ~ 21:00', '02-333-4444', '333-22-33333', 'OWNER', 6, 'APPROVED', 0, 0, 'https://picsum.photos/seed/girigiri-store-3/600/400', NOW(), NOW()),
(4, '커피와우 명동점', '카페', '서울시 중구 명동길 20', 37.558000, 126.991000, '07:30 ~ 22:00', '02-444-5555', '444-33-44444', 'OWNER', 7, 'APPROVED', 0, 0, 'https://picsum.photos/seed/girigiri-store-4/600/400', NOW(), NOW()),
(5, '엄마손반찬', '반찬', '서울시 용산구 원효로 60', 37.549500, 126.970000, '10:00 ~ 20:00', '02-555-6666', '555-44-55555', 'OWNER', 8, 'APPROVED', 0, 0, 'https://picsum.photos/seed/girigiri-store-5/600/400', NOW(), NOW()),
(6, '정성반찬가게', '반찬', '서울시 용산구 한강대로 88', 37.543000, 126.968000, '09:30 ~ 20:30', '02-666-7777', '666-55-66666', 'OWNER', 9, 'APPROVED', 0, 0, 'https://picsum.photos/seed/girigiri-store-6/600/400', NOW(), NOW()),
(7, '든든도시락', '도시락', '서울시 중구 다산로 30', 37.558300, 126.978400, '10:00 ~ 21:00', '02-777-8888', '777-66-77777', 'OWNER', 10, 'APPROVED', 0, 0, 'https://picsum.photos/seed/girigiri-store-7/600/400', NOW(), NOW()),
(8, '매일도시락 용산점', '도시락', '서울시 용산구 한강대로 120', 37.545500, 126.991700, '10:30 ~ 21:30', '02-888-9999', '888-77-88888', 'OWNER', 11, 'APPROVED', 0, 0, 'https://picsum.photos/seed/girigiri-store-8/600/400', NOW(), NOW());

-- ---------------------------------------------------------------------
-- product (매장당 3~4개, active/sold/expired 다양하게, 할인율도 다양하게)
-- ---------------------------------------------------------------------
INSERT INTO product (id, store_id, name, original_price, discounted_price, quantity, remaining_quantity, image_url, description, status, registered_at) VALUES
-- 다이스키 베이커리 (store 1)
(1, 1, '식빵 마감세트', 6000, 3000, 10, 4, '/images/pixabay/bakery/bakery-1077984.jpg', '오늘 구운 식빵, 마감 할인 50%', 'active', NOW()),
(2, 1, '크루아상 3개입', 9000, 4500, 5, 0, '/images/pixabay/bakery/bakery-1743939.jpg', '버터 크루아상 3개 세트', 'sold', NOW()),
(3, 1, '어제 만든 케이크', 15000, 6000, 3, 3, '/images/pixabay/dessert/dessert-1224044.jpg', '유통기한 임박 조각 케이크', 'expired', NOW()),
(4, 1, '단팥빵 5개입', 7000, 4200, 8, 5, '/images/pixabay/bakery/bakery-1868573.jpg', '팥이 꽉 찬 단팥빵 5개 세트', 'active', NOW()),
-- 브런치카페 온 (store 3)
(5, 3, '크로플 세트 (2개)', 8000, 4800, 6, 2, '/images/pixabay/dessert/dessert-1263099.jpg', '바삭한 크로플 2개 + 시럽', 'active', NOW()),
(6, 3, '오늘의 샌드위치', 7500, 3750, 4, 4, '/images/pixabay/food-general/food-general-1155132.jpg', '마감 임박 수제 샌드위치', 'active', NOW()),
(7, 3, '아메리카노 원두 마감', 12000, 6000, 2, 0, '/images/pixabay/cafe/cafe-2608864.jpg', '오늘 로스팅한 원두 봉지', 'sold', NOW()),
-- 커피와우 명동점 (store 4)
(8, 4, '베이글 2개 세트', 6500, 3200, 7, 3, '/images/pixabay/bakery/bakery-1194428.jpg', '플레인/에브리싱 베이글 2개', 'active', NOW()),
(9, 4, '디저트 3종 모음', 11000, 5500, 3, 1, '/images/pixabay/dessert/dessert-1850011.jpg', '마감 임박 디저트 3종', 'active', NOW()),
(10, 4, '어제 구운 스콘 4개', 9000, 3600, 4, 4, '/images/pixabay/bakery/bakery-3467243.jpg', '유통기한 임박 스콘 4개입', 'expired', NOW()),
-- 엄마손반찬 (store 5)
(11, 5, '오늘의 나물 반찬세트', 12000, 7200, 6, 2, '/images/pixabay/banchan/banchan-1141242.jpg', '3가지 제철 나물 반찬 세트', 'active', NOW()),
(12, 5, '잡채 한 팩', 9000, 5400, 5, 5, '/images/pixabay/banchan/banchan-207235.jpg', '당일 조리 잡채 500g', 'active', NOW()),
(13, 5, '계란말이 + 진미채', 8000, 4000, 4, 0, '/images/pixabay/banchan/banchan-207242.jpg', '밑반찬 2종 세트', 'sold', NOW()),
-- 정성반찬가게 (store 6)
(14, 6, '김치찌개용 김치 1kg', 10000, 6000, 5, 3, '/images/pixabay/banchan/banchan-2390565.jpg', '숙성 배추김치 1kg', 'active', NOW()),
(15, 6, '멸치볶음 + 콩자반', 7000, 3500, 6, 6, '/images/pixabay/banchan/banchan-2449656.jpg', '밑반찬 2종 세트', 'active', NOW()),
(16, 6, '어제 만든 불고기', 14000, 5600, 2, 2, '/images/pixabay/restaurant/restaurant-1284351.jpg', '유통기한 임박 양념 불고기', 'expired', NOW()),
-- 든든도시락 (store 7)
(17, 7, '제육볶음 도시락', 8500, 4250, 8, 4, '/images/pixabay/dosirak/dosirak-1702652.jpg', '오늘의 제육볶음 도시락', 'active', NOW()),
(18, 7, '오늘의 도시락 (랜덤)', 7000, 3500, 10, 6, '/images/pixabay/dosirak/dosirak-1743370.jpg', '남은 반찬으로 구성한 랜덤 도시락', 'active', NOW()),
(19, 7, '돈까스 도시락', 9000, 3600, 3, 0, '/images/pixabay/dosirak/dosirak-2720481.jpg', '바삭한 돈까스 도시락', 'sold', NOW()),
-- 매일도시락 용산점 (store 8)
(20, 8, '샐러드 도시락', 8000, 4800, 5, 2, '/images/pixabay/dosirak/dosirak-2720483.jpg', '건강한 샐러드+닭가슴살 도시락', 'active', NOW()),
(21, 8, '김밥 3줄 세트', 6000, 3000, 6, 3, '/images/pixabay/dosirak/dosirak-2806566.jpg', '당일 마감 김밥 3줄', 'active', NOW()),
(22, 8, '어제 만든 불고기 도시락', 8500, 3400, 2, 2, '/images/pixabay/dosirak/dosirak-4933112.jpg', '유통기한 임박 불고기 도시락', 'expired', NOW());

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
(26, 1, 12, '잡채 한 팩', 5, 1, 5400, DATE_SUB(NOW(), INTERVAL 5 HOUR), 'PICK-1026', 'picked', DATE_SUB(NOW(), INTERVAL 5 HOUR), DATE_SUB(NOW(), INTERVAL 5 HOUR), DATE_SUB(NOW(), INTERVAL 5 HOUR), NULL, NULL),
(27, 13, 12, '잡채 한 팩', 5, 1, 5400, DATE_SUB(NOW(), INTERVAL 20 HOUR), 'PICK-1027', 'refunded', DATE_SUB(NOW(), INTERVAL 20 HOUR), DATE_SUB(NOW(), INTERVAL 20 HOUR), DATE_SUB(NOW(), INTERVAL 20 HOUR), '상품 상태 불량 확인되어 전액 환불', 'ADMIN');

-- ---------------------------------------------------------------------
-- payment / refund (신고 기반 환불 테스트용, 2026-10-06) — 26·21은 PAID라 신고 상세의 [환불 처리]를
-- 눌러볼 수 있다(PortOne 키가 비어 있으면 "환불 실패"로 처리되는 실패 경로 확인용). 27은 이미 환불 완료.
-- ---------------------------------------------------------------------
INSERT INTO payment (id, reservation_id, merchant_uid, imp_uid, amount, pay_method, pay_status, fail_reason, paid_at, requested_at, updated_at) VALUES
(1, 26, 'SAMPLE-PAY-26', 'SAMPLE-IMP-26', 5400, 'card', 'PAID', NULL, DATE_SUB(NOW(), INTERVAL 5 HOUR), DATE_SUB(NOW(), INTERVAL 5 HOUR), DATE_SUB(NOW(), INTERVAL 5 HOUR)),
(2, 21, 'SAMPLE-PAY-21', 'SAMPLE-IMP-21', 3200, 'kakaopay', 'PAID', NULL, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY)),
(3, 27, 'SAMPLE-PAY-27', 'SAMPLE-IMP-27', 5400, 'card', 'CANCELLED', '상품 상태 불량 확인되어 전액 환불', DATE_SUB(NOW(), INTERVAL 20 HOUR), DATE_SUB(NOW(), INTERVAL 20 HOUR), DATE_SUB(NOW(), INTERVAL 2 HOUR));


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

-- 추가됨 (2026-10-06, 신고 기반 환불 테스트) — target_reservation_id(주문 연결)가 있는 신고. 상태별로 하나씩:
--  3: 픽업완료 + PAID, 대기 → [환불 처리] 버튼 노출   4: 같은 조건(카카오페이)
--  5: 이미 환불 완료(refund DONE) → "환불 완료" 배지   6: 노쇼 주문 신고 → 환불 불가 안내 배지
INSERT INTO complaint (id, target_name, target_store_id, target_reservation_id, reason, content, reporter_name, reporter_id, status, admin_reply, created_at, resolved_at) VALUES
(3, '엄마손반찬', 5, 26, '상품 상태 불량', '받아온 잡채가 상해서 신맛이 났어요. 환불 부탁드립니다.', '구제왕나은', 1, 'PENDING', NULL, DATE_SUB(NOW(), INTERVAL 2 HOUR), NULL),
(4, '커피와우 명동점', 4, 21, '수량 부족', '베이글이 2개라고 했는데 1개만 들어 있었어요.', '자취생박지민', 12, 'PENDING', NULL, DATE_SUB(NOW(), INTERVAL 5 HOUR), NULL),
(5, '엄마손반찬', 5, 27, '상품 상태 불량', '잡채에서 이물질이 나왔습니다.', '다이어터최유나', 13, 'RESOLVED', '확인 후 전액 환불 처리했어요. 불편을 드려 죄송합니다.', DATE_SUB(NOW(), INTERVAL 18 HOUR), DATE_SUB(NOW(), INTERVAL 2 HOUR)),
(6, '브런치카페 온', 3, 18, '노쇼 오처리', '방문했는데 노쇼로 처리됐어요.', '노쇼왕문창호', 5, 'PENDING', NULL, DATE_SUB(NOW(), INTERVAL 1 DAY), NULL);

INSERT INTO refund (id, order_id, report_id, amount, reason, status, pg_refund_tid, requested_by, created_at, updated_at) VALUES
(1, 27, 5, 5400, '상품 상태 불량 확인되어 전액 환불', 'DONE', 'SAMPLE-PAY-27', 4, DATE_SUB(NOW(), INTERVAL 2 HOUR), DATE_SUB(NOW(), INTERVAL 2 HOUR));

INSERT INTO reservation_status_history (id, reservation_id, from_status, to_status, changed_by, reason, created_at) VALUES
(1, 27, 'picked', 'refunded', 4, '상품 상태 불량 확인되어 전액 환불', DATE_SUB(NOW(), INTERVAL 2 HOUR));

SET NAMES utf8mb4;
INSERT INTO users (id, oauth_provider, oauth_id, role, status, nickname, email, password, region, profile_completed, terms_agreed, privacy_agreed, marketing_agreed, representative_badge, savings_goal_amount, latitude, longitude, created_at, updated_at) VALUES
(16, 'email', 'demo-user@test.local', 'USER', 'ACTIVE', 'demovideo', 'demo-user@test.local', '$2y$10$6nyyAwiBHlqcou0BthHFS.S52XXI9AHKHKeBKL70nQKcJVWHBiCGG', 'seoul jung-gu', 1, 1, 1, 0, NULL, NULL, 37.560000, 126.985000, NOW(), NOW());

-- =======================================================================
-- 매장 신뢰도(취소율) 자동 정지 시연용 매장 2곳 (담당: 송채현, 2026-10-06 발표 준비)
-- StoreReliabilityService 기준: 최근 30건 중 매장귀책 취소율로 신뢰도(=100-취소율)를
-- 계산하고, 70% 밑으로 떨어지면 1회차 7일→2회차 14일→3회차 30일→4회차 영구정지.
-- 아래 두 매장은 reliability_suspension_count/suspended_until/banned 값을 그 결과처럼
-- "이미 적용된 상태"로 직접 넣어뒀고, 그 근거가 되는 취소 내역(reservation)도 같이 채워서
-- 매장 상세 화면의 취소율 통계와 정지 상태가 서로 모순되지 않게 했다.
-- =======================================================================

-- 점주 계정 2명 (신뢰도 정지/영구정지 매장 각각의 사장님)
INSERT INTO users (id, oauth_provider, oauth_id, role, status, nickname, email, password, region, profile_completed, terms_agreed, privacy_agreed, marketing_agreed, latitude, longitude, created_at, updated_at) VALUES
(40, 'kakao', 'kakao_demo_40', 'OWNER', 'ACTIVE', '분식사장이민재', 'minjae.lee@example.com', NULL, '서울 마포구', 1, 1, 1, 0, 37.556000, 126.937000, NOW(), NOW()),
(41, 'kakao', 'kakao_demo_41', 'OWNER', 'ACTIVE', '분식사장정하늘', 'haneul.jeong@example.com', NULL, '서울 마포구', 1, 1, 1, 0, 37.550000, 126.925000, NOW(), NOW());

-- store 30: 2회차 위반 중 — 14일 정지 (reliability_suspended_until이 미래 → "정지 중")
-- store 31: 4회차 위반 — 영구정지 (reliability_banned=1, suspended_until은 NULL)
INSERT INTO store (id, store_name, category, address, latitude, longitude, operating_hours, phone, business_number, role, owner_id, approval_status, reliability_suspension_count, reliability_banned, reliability_suspended_until, image_url, created_at, updated_at) VALUES
(30, '분식집 번개떡볶이', '분식', '서울시 마포구 월드컵북로 60', 37.556000, 126.937000, '11:00 ~ 21:00', '02-910-3030', '930-11-93030', 'OWNER', 40, 'APPROVED', 2, 0, DATE_ADD(NOW(), INTERVAL 9 DAY), 'https://picsum.photos/seed/girigiri-store-30/600/400', DATE_SUB(NOW(), INTERVAL 60 DAY), NOW()),
(31, '분식집 매콤세상', '분식', '서울시 마포구 성산로 80', 37.550000, 126.925000, '11:30 ~ 21:30', '02-910-3031', '930-11-93031', 'OWNER', 41, 'APPROVED', 3, 1, NULL, 'https://picsum.photos/seed/girigiri-store-31/600/400', DATE_SUB(NOW(), INTERVAL 90 DAY), NOW());

UPDATE store SET sido = '서울' WHERE id IN (30, 31);

INSERT INTO product (id, store_id, name, original_price, discounted_price, quantity, remaining_quantity, image_url, description, status, registered_at) VALUES
(300, 30, '떡볶이 2인분', 7000, 3500, 5, 2, '/images/pixabay/food-general/food-general-2009590.jpg', '매콤 떡볶이 2인분 마감세트', 'active', NOW()),
(301, 30, '순대 한 접시', 6000, 3000, 4, 1, '/images/pixabay/food-general/food-general-2068220.jpg', '모둠 순대 한 접시', 'active', NOW()),
(310, 31, '즉석떡볶이 2인분', 9000, 4500, 5, 3, '/images/pixabay/food-general/food-general-217156.jpg', '즉석 떡볶이 2인분', 'active', NOW()),
(311, 31, '튀김 모둠', 7000, 3500, 4, 2, '/images/pixabay/food-general/food-general-2175326.jpg', '모둠 튀김 한 접시', 'active', NOW());

-- store 30 최근 예약 12건 — 그 중 8건이 매장귀책 취소(취소율 66.7% → 신뢰도 33.3%, 70% 밑돌아
-- 정지 사유로 자연스럽게 이어짐). 손님 쪽엔 "휴무"로만 보이므로 사유를 노출하는 화면이 아니면
-- 그대로 둬도 된다.
INSERT INTO reservation (id, user_id, product_id, product_name, store_id, reserved_quantity, total_price, pickup_time, pickup_code, status, reserved_at, picked_at, accepted_at, cancel_reason, cancelled_by) VALUES
(300, 1, 300, '떡볶이 2인분', 30, 1, 3500, DATE_SUB(NOW(), INTERVAL 1 DAY), 'PICK-R300', 'cancelled', DATE_SUB(NOW(), INTERVAL 1 DAY), NULL, DATE_SUB(NOW(), INTERVAL 1 DAY), '재료 소진으로 매장에서 취소', 'STORE'),
(301, 2, 301, '순대 한 접시', 30, 1, 3000, DATE_SUB(NOW(), INTERVAL 2 DAY), 'PICK-R301', 'cancelled', DATE_SUB(NOW(), INTERVAL 2 DAY), NULL, DATE_SUB(NOW(), INTERVAL 2 DAY), '재료 소진으로 매장에서 취소', 'STORE'),
(302, 12, 300, '떡볶이 2인분', 30, 1, 3500, DATE_SUB(NOW(), INTERVAL 3 DAY), 'PICK-R302', 'cancelled', DATE_SUB(NOW(), INTERVAL 3 DAY), NULL, DATE_SUB(NOW(), INTERVAL 3 DAY), '사정상 영업 조기 마감', 'STORE'),
(303, 13, 301, '순대 한 접시', 30, 1, 3000, DATE_SUB(NOW(), INTERVAL 4 DAY), 'PICK-R303', 'cancelled', DATE_SUB(NOW(), INTERVAL 4 DAY), NULL, DATE_SUB(NOW(), INTERVAL 4 DAY), '사정상 영업 조기 마감', 'STORE'),
(304, 14, 300, '떡볶이 2인분', 30, 1, 3500, DATE_SUB(NOW(), INTERVAL 5 DAY), 'PICK-R304', 'cancelled', DATE_SUB(NOW(), INTERVAL 5 DAY), NULL, DATE_SUB(NOW(), INTERVAL 5 DAY), '재료 소진으로 매장에서 취소', 'STORE'),
(305, 15, 301, '순대 한 접시', 30, 1, 3000, DATE_SUB(NOW(), INTERVAL 6 DAY), 'PICK-R305', 'cancelled', DATE_SUB(NOW(), INTERVAL 6 DAY), NULL, DATE_SUB(NOW(), INTERVAL 6 DAY), '재료 소진으로 매장에서 취소', 'STORE'),
(306, 1, 300, '떡볶이 2인분', 30, 1, 3500, DATE_SUB(NOW(), INTERVAL 7 DAY), 'PICK-R306', 'cancelled', DATE_SUB(NOW(), INTERVAL 7 DAY), NULL, DATE_SUB(NOW(), INTERVAL 7 DAY), '사정상 영업 조기 마감', 'STORE'),
(307, 2, 301, '순대 한 접시', 30, 1, 3000, DATE_SUB(NOW(), INTERVAL 8 DAY), 'PICK-R307', 'cancelled', DATE_SUB(NOW(), INTERVAL 8 DAY), NULL, DATE_SUB(NOW(), INTERVAL 8 DAY), '재료 소진으로 매장에서 취소', 'STORE'),
(308, 12, 300, '떡볶이 2인분', 30, 1, 3500, DATE_SUB(NOW(), INTERVAL 10 DAY), 'PICK-R308', 'picked', DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_SUB(NOW(), INTERVAL 10 DAY), NULL, NULL),
(309, 13, 301, '순대 한 접시', 30, 1, 3000, DATE_SUB(NOW(), INTERVAL 11 DAY), 'PICK-R309', 'picked', DATE_SUB(NOW(), INTERVAL 11 DAY), DATE_SUB(NOW(), INTERVAL 11 DAY), DATE_SUB(NOW(), INTERVAL 11 DAY), NULL, NULL),
(310, 14, 300, '떡볶이 2인분', 30, 1, 3500, DATE_SUB(NOW(), INTERVAL 12 DAY), 'PICK-R310', 'picked', DATE_SUB(NOW(), INTERVAL 12 DAY), DATE_SUB(NOW(), INTERVAL 12 DAY), DATE_SUB(NOW(), INTERVAL 12 DAY), NULL, NULL),
(311, 15, 301, '순대 한 접시', 30, 1, 3000, DATE_SUB(NOW(), INTERVAL 13 DAY), 'PICK-R311', 'picked', DATE_SUB(NOW(), INTERVAL 13 DAY), DATE_SUB(NOW(), INTERVAL 13 DAY), DATE_SUB(NOW(), INTERVAL 13 DAY), NULL, NULL);

-- store 31 최근 예약 12건 — 9건 매장귀책 취소(취소율 75% → 신뢰도 25%)로 영구정지까지 간 근거.
INSERT INTO reservation (id, user_id, product_id, product_name, store_id, reserved_quantity, total_price, pickup_time, pickup_code, status, reserved_at, picked_at, accepted_at, cancel_reason, cancelled_by) VALUES
(320, 1, 310, '즉석떡볶이 2인분', 31, 1, 4500, DATE_SUB(NOW(), INTERVAL 15 DAY), 'PICK-R320', 'cancelled', DATE_SUB(NOW(), INTERVAL 15 DAY), NULL, DATE_SUB(NOW(), INTERVAL 15 DAY), '노쇼 누적으로 영업 중단', 'STORE'),
(321, 2, 311, '튀김 모둠', 31, 1, 3500, DATE_SUB(NOW(), INTERVAL 16 DAY), 'PICK-R321', 'cancelled', DATE_SUB(NOW(), INTERVAL 16 DAY), NULL, DATE_SUB(NOW(), INTERVAL 16 DAY), '노쇼 누적으로 영업 중단', 'STORE'),
(322, 12, 310, '즉석떡볶이 2인분', 31, 1, 4500, DATE_SUB(NOW(), INTERVAL 17 DAY), 'PICK-R322', 'cancelled', DATE_SUB(NOW(), INTERVAL 17 DAY), NULL, DATE_SUB(NOW(), INTERVAL 17 DAY), '사정상 영업 중단', 'STORE'),
(323, 13, 311, '튀김 모둠', 31, 1, 3500, DATE_SUB(NOW(), INTERVAL 18 DAY), 'PICK-R323', 'cancelled', DATE_SUB(NOW(), INTERVAL 18 DAY), NULL, DATE_SUB(NOW(), INTERVAL 18 DAY), '사정상 영업 중단', 'STORE'),
(324, 14, 310, '즉석떡볶이 2인분', 31, 1, 4500, DATE_SUB(NOW(), INTERVAL 19 DAY), 'PICK-R324', 'cancelled', DATE_SUB(NOW(), INTERVAL 19 DAY), NULL, DATE_SUB(NOW(), INTERVAL 19 DAY), '재료 소진으로 매장에서 취소', 'STORE'),
(325, 15, 311, '튀김 모둠', 31, 1, 3500, DATE_SUB(NOW(), INTERVAL 20 DAY), 'PICK-R325', 'cancelled', DATE_SUB(NOW(), INTERVAL 20 DAY), NULL, DATE_SUB(NOW(), INTERVAL 20 DAY), '재료 소진으로 매장에서 취소', 'STORE'),
(326, 1, 310, '즉석떡볶이 2인분', 31, 1, 4500, DATE_SUB(NOW(), INTERVAL 21 DAY), 'PICK-R326', 'cancelled', DATE_SUB(NOW(), INTERVAL 21 DAY), NULL, DATE_SUB(NOW(), INTERVAL 21 DAY), '재료 소진으로 매장에서 취소', 'STORE'),
(327, 2, 311, '튀김 모둠', 31, 1, 3500, DATE_SUB(NOW(), INTERVAL 22 DAY), 'PICK-R327', 'cancelled', DATE_SUB(NOW(), INTERVAL 22 DAY), NULL, DATE_SUB(NOW(), INTERVAL 22 DAY), '재료 소진으로 매장에서 취소', 'STORE'),
(328, 12, 310, '즉석떡볶이 2인분', 31, 1, 4500, DATE_SUB(NOW(), INTERVAL 23 DAY), 'PICK-R328', 'cancelled', DATE_SUB(NOW(), INTERVAL 23 DAY), NULL, DATE_SUB(NOW(), INTERVAL 23 DAY), '재료 소진으로 매장에서 취소', 'STORE'),
(329, 13, 311, '튀김 모둠', 31, 1, 3500, DATE_SUB(NOW(), INTERVAL 24 DAY), 'PICK-R329', 'cancelled', DATE_SUB(NOW(), INTERVAL 24 DAY), NULL, DATE_SUB(NOW(), INTERVAL 24 DAY), '재료 소진으로 매장에서 취소', 'STORE'),
(330, 14, 310, '즉석떡볶이 2인분', 31, 1, 4500, DATE_SUB(NOW(), INTERVAL 25 DAY), 'PICK-R330', 'picked', DATE_SUB(NOW(), INTERVAL 25 DAY), DATE_SUB(NOW(), INTERVAL 25 DAY), DATE_SUB(NOW(), INTERVAL 25 DAY), NULL, NULL),
(331, 15, 311, '튀김 모둠', 31, 1, 3500, DATE_SUB(NOW(), INTERVAL 26 DAY), 'PICK-R331', 'picked', DATE_SUB(NOW(), INTERVAL 26 DAY), DATE_SUB(NOW(), INTERVAL 26 DAY), DATE_SUB(NOW(), INTERVAL 26 DAY), NULL, NULL),
(332, 1, 310, '즉석떡볶이 2인분', 31, 1, 4500, DATE_SUB(NOW(), INTERVAL 27 DAY), 'PICK-R332', 'picked', DATE_SUB(NOW(), INTERVAL 27 DAY), DATE_SUB(NOW(), INTERVAL 27 DAY), DATE_SUB(NOW(), INTERVAL 27 DAY), NULL, NULL);

-- =======================================================================
-- 챗봇(회원용 "내 예약 조회" 함수 호출) 시연용 — 데모 로그인 계정에 실제 예약 추가
-- (담당: 송채현, ChatService.buildReservationStatusJson / getCancelEligibilityForUser)
-- demo-user@test.local 로 로그인한 뒤 챗봇에 "내 예약 취소 가능한지 확인해줘" 같은 질문을
-- 하면, 아래 예약 중 결제 후 30분 이내인 건은 "취소 가능"으로, 아닌 건은 "취소 불가"로
-- 실제 DB 값을 조회해서 답한다.
-- =======================================================================
INSERT INTO reservation (id, user_id, product_id, product_name, store_id, reserved_quantity, total_price, pickup_time, pickup_code, status, reserved_at, picked_at, accepted_at, cancel_reason, cancelled_by) VALUES
(350, 16, 1, '식빵 마감세트', 1, 1, 3000, DATE_ADD(NOW(), INTERVAL 2 HOUR), 'PICK-DEMO350', 'confirmed', DATE_SUB(NOW(), INTERVAL 10 MINUTE), NULL, DATE_SUB(NOW(), INTERVAL 10 MINUTE), NULL, NULL),
(351, 16, 5, '크로플 세트 (2개)', 3, 1, 4800, DATE_ADD(NOW(), INTERVAL 1 HOUR), 'PICK-DEMO351', 'ready', DATE_SUB(NOW(), INTERVAL 2 HOUR), NULL, DATE_SUB(NOW(), INTERVAL 1 HOUR), NULL, NULL),
(352, 16, 17, '제육볶음 도시락', 7, 1, 4250, DATE_SUB(NOW(), INTERVAL 2 DAY), 'PICK-DEMO352', 'picked', DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), NULL, NULL);

INSERT INTO payment (id, reservation_id, merchant_uid, imp_uid, amount, pay_method, pay_status, fail_reason, paid_at, requested_at, updated_at) VALUES
(50, 350, 'DEMO-PAY-350', 'DEMO-IMP-350', 3000, 'card', 'PAID', NULL, DATE_SUB(NOW(), INTERVAL 10 MINUTE), DATE_SUB(NOW(), INTERVAL 10 MINUTE), DATE_SUB(NOW(), INTERVAL 10 MINUTE)),
(51, 351, 'DEMO-PAY-351', 'DEMO-IMP-351', 4800, 'card', 'PAID', NULL, DATE_SUB(NOW(), INTERVAL 2 HOUR), DATE_SUB(NOW(), INTERVAL 2 HOUR), DATE_SUB(NOW(), INTERVAL 2 HOUR)),
(52, 352, 'DEMO-PAY-352', 'DEMO-IMP-352', 4250, 'card', 'PAID', NULL, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY));

INSERT INTO receipt (id, reservation_id, pdf_url, generated_at) VALUES
(50, 350, '/receipts/reservation-350.pdf', DATE_SUB(NOW(), INTERVAL 10 MINUTE)),
(51, 351, '/receipts/reservation-351.pdf', DATE_SUB(NOW(), INTERVAL 2 HOUR)),
(52, 352, '/receipts/reservation-352.pdf', DATE_SUB(NOW(), INTERVAL 2 DAY));

-- =======================================================================
-- 쿠폰 캠페인 데이터 (sql/sample-coupon-data.sql 그대로)
-- =======================================================================
-- 쿠폰 캠페인 관리 화면(/superadmin/coupons) 확인용 "추가 전용" 샘플 데이터 (2026-10-06).
-- 기존 데이터를 지우지 않고 INSERT IGNORE 라 여러 번 실행해도 중복되지 않는다(id 901~906 사용).
-- 전제: users 1·2·5·12~15, store 1·4 가 있어야 한다(sample-data.sql 기준).
-- 실행: mysql -u root -p --default-character-set=utf8mb4 girigiri < sql/sample-coupon-data.sql
--
--  901 코드형 진행중(3명 발급) · 902 코드형 3일 내 만료(2명) · 903 매장 지정 정액(3/5명, 매장 2곳)
--  904 지역 지정 서울·경기(6/6명, 소진 100%) · 905 기한 만료(4명) · 906 비활성(0명)

INSERT IGNORE INTO coupon_campaign (id, name, code, scope, discount_type, discount_rate, discount_amount, max_discount_amount, min_order_amount, issue_limit, funded_by, issued_by_admin_id, issue_reason, expires_at, active, created_at) VALUES
(901, '추석 프로모션', 'AUTUMN2026', NULL, 'RATE', 15, NULL, NULL, 0, NULL, NULL, NULL, NULL, DATE_ADD(NOW(), INTERVAL 20 DAY), 1, DATE_SUB(NOW(), INTERVAL 1 DAY)),
(902, '가을 감사 이벤트', 'THANKS10', NULL, 'RATE', 10, NULL, NULL, 5000, NULL, NULL, NULL, NULL, DATE_ADD(NOW(), INTERVAL 2 DAY), 1, DATE_SUB(NOW(), INTERVAL 1 DAY)),
(903, '매장 지정 쿠폰 · 2026-10-02 (2곳)', NULL, 'STORE', 'AMOUNT', NULL, 2000, NULL, 0, 5, 'PLATFORM', 4, '소진율 점검 필요', DATE_ADD(NOW(), INTERVAL 5 DAY), 1, DATE_SUB(NOW(), INTERVAL 1 DAY)),
(904, '지역 지정 쿠폰 · 2026-10-04 (서울·경기)', NULL, 'REGION', 'RATE', 10, NULL, 3000, 0, 6, 'PLATFORM', 4, '지역 프로모션', DATE_ADD(NOW(), INTERVAL 6 DAY), 1, DATE_SUB(NOW(), INTERVAL 1 DAY)),
(905, '여름 마감세일 이벤트', 'SUMMER20', NULL, 'RATE', 20, NULL, NULL, 0, NULL, NULL, NULL, NULL, DATE_ADD(NOW(), INTERVAL -4 DAY), 1, DATE_SUB(NOW(), INTERVAL 4 DAY)),
(906, '테스트 캠페인', 'TESTONLY', NULL, 'RATE', 5, NULL, NULL, 0, NULL, NULL, NULL, NULL, DATE_ADD(NOW(), INTERVAL 15 DAY), 0, DATE_SUB(NOW(), INTERVAL 1 DAY));

INSERT IGNORE INTO coupon_store (id, campaign_id, store_id) VALUES (901, 903, 1), (902, 903, 4);

INSERT IGNORE INTO coupon_region (id, campaign_id, sido) VALUES (901, 904, '서울'), (902, 904, '경기');

INSERT IGNORE INTO coupon (id, source, issued_to_user_id, campaign_id, discount_rate, scope, discount_type, discount_amount, max_discount_amount, min_order_amount, expires_at, used, created_at) VALUES
(9001, 'PROMOTION', 12, 901, 15, NULL, 'RATE', NULL, NULL, 0, DATE_ADD(NOW(), INTERVAL 20 DAY), 0, DATE_SUB(NOW(), INTERVAL 1 DAY)),
(9002, 'PROMOTION', 13, 901, 15, NULL, 'RATE', NULL, NULL, 0, DATE_ADD(NOW(), INTERVAL 20 DAY), 0, DATE_SUB(NOW(), INTERVAL 1 DAY)),
(9003, 'PROMOTION', 14, 901, 15, NULL, 'RATE', NULL, NULL, 0, DATE_ADD(NOW(), INTERVAL 20 DAY), 0, DATE_SUB(NOW(), INTERVAL 1 DAY)),
(9004, 'PROMOTION', 1, 902, 10, NULL, 'RATE', NULL, NULL, 5000, DATE_ADD(NOW(), INTERVAL 2 DAY), 0, DATE_SUB(NOW(), INTERVAL 1 DAY)),
(9005, 'PROMOTION', 15, 902, 10, NULL, 'RATE', NULL, NULL, 5000, DATE_ADD(NOW(), INTERVAL 2 DAY), 0, DATE_SUB(NOW(), INTERVAL 1 DAY)),
(9006, 'PROMOTION', 1, 903, NULL, 'STORE', 'AMOUNT', 2000, NULL, 0, DATE_ADD(NOW(), INTERVAL 5 DAY), 0, DATE_SUB(NOW(), INTERVAL 1 DAY)),
(9007, 'PROMOTION', 2, 903, NULL, 'STORE', 'AMOUNT', 2000, NULL, 0, DATE_ADD(NOW(), INTERVAL 5 DAY), 0, DATE_SUB(NOW(), INTERVAL 1 DAY)),
(9008, 'PROMOTION', 12, 903, NULL, 'STORE', 'AMOUNT', 2000, NULL, 0, DATE_ADD(NOW(), INTERVAL 5 DAY), 0, DATE_SUB(NOW(), INTERVAL 1 DAY)),
(9009, 'PROMOTION', 1, 904, 10, 'REGION', 'RATE', NULL, 3000, 0, DATE_ADD(NOW(), INTERVAL 6 DAY), 0, DATE_SUB(NOW(), INTERVAL 1 DAY)),
(9010, 'PROMOTION', 2, 904, 10, 'REGION', 'RATE', NULL, 3000, 0, DATE_ADD(NOW(), INTERVAL 6 DAY), 0, DATE_SUB(NOW(), INTERVAL 1 DAY)),
(9011, 'PROMOTION', 5, 904, 10, 'REGION', 'RATE', NULL, 3000, 0, DATE_ADD(NOW(), INTERVAL 6 DAY), 0, DATE_SUB(NOW(), INTERVAL 1 DAY)),
(9012, 'PROMOTION', 12, 904, 10, 'REGION', 'RATE', NULL, 3000, 0, DATE_ADD(NOW(), INTERVAL 6 DAY), 0, DATE_SUB(NOW(), INTERVAL 1 DAY)),
(9013, 'PROMOTION', 13, 904, 10, 'REGION', 'RATE', NULL, 3000, 0, DATE_ADD(NOW(), INTERVAL 6 DAY), 0, DATE_SUB(NOW(), INTERVAL 1 DAY)),
(9014, 'PROMOTION', 14, 904, 10, 'REGION', 'RATE', NULL, 3000, 0, DATE_ADD(NOW(), INTERVAL 6 DAY), 0, DATE_SUB(NOW(), INTERVAL 1 DAY)),
(9015, 'PROMOTION', 12, 905, 20, NULL, 'RATE', NULL, NULL, 0, DATE_ADD(NOW(), INTERVAL -4 DAY), 0, DATE_SUB(NOW(), INTERVAL 1 DAY)),
(9016, 'PROMOTION', 13, 905, 20, NULL, 'RATE', NULL, NULL, 0, DATE_ADD(NOW(), INTERVAL -4 DAY), 0, DATE_SUB(NOW(), INTERVAL 1 DAY)),
(9017, 'PROMOTION', 14, 905, 20, NULL, 'RATE', NULL, NULL, 0, DATE_ADD(NOW(), INTERVAL -4 DAY), 0, DATE_SUB(NOW(), INTERVAL 1 DAY)),
(9018, 'PROMOTION', 15, 905, 20, NULL, 'RATE', NULL, NULL, 0, DATE_ADD(NOW(), INTERVAL -4 DAY), 0, DATE_SUB(NOW(), INTERVAL 1 DAY));

-- =======================================================================
-- 지역별 현황 데이터 (sql/sample-region-data.sql 그대로)
-- =======================================================================
-- 지역별 매장 현황(슈퍼어드민 대시보드 "지역별 현황" · /superadmin/regions/{시도}/stores) 확인용
-- "추가 전용" 샘플 데이터 (2026-10-06). 기존 데이터를 지우지 않고 INSERT IGNORE 라 여러 번 실행해도 중복되지 않는다.
-- 전제: users 12~15, 서울 매장 1~8(sample-data.sql 기준)이 있어야 한다.
-- 실행: mysql -u root -p --default-character-set=utf8mb4 girigiri < sql/sample-region-data.sql
--
-- 지역별 결과(최근 7일, 마감 지난 상품 기준 — 표본 5건 미만은 "표본 부족", 소진율 20% 미만은 "점검 필요"):
--   경기 정상(21건 중 11 소진) · 부산 점검 필요(14건 중 2) · 대구 정상(11건 중 7) · 인천 표본 부족(3건)
--   대전 정상(5건 중 3) · 광주 표본 부족(2건) · 제주 정상(6건 중 5) · 강원 등록 0건(표본 부족)
--   충북은 승인 대기(PENDING) 매장뿐이라 "미진출", 나머지 시도(충남·세종·경북·경남·울산·전북·전남)도 미진출.
-- 서울은 기존 8개 매장 중 승인된 7곳이 같은 방식으로 집계된다(store.sido 가 비어 있던 것을 아래에서 채운다).

-- 0. 기존 서울 매장의 시도 값 채우기 (지역 집계는 store.sido 로 묶는다)
UPDATE store SET sido = '서울' WHERE sido IS NULL AND address LIKE '서울%';

-- 1. 지역 매장 사장님 계정
INSERT IGNORE INTO users (id, oauth_provider, oauth_id, role, status, nickname, email, password, region, profile_completed, terms_agreed, privacy_agreed, marketing_agreed, representative_badge, savings_goal_amount, latitude, longitude, created_at, updated_at) VALUES
(21, 'google', 'google_region_21', 'OWNER', 'ACTIVE', '수원 행궁빵집 사장님', 'owner21@example.com', NULL, '경기도', 1, 1, 1, 0, NULL, NULL, 37.2819, 127.016, NOW(), NOW()),
(22, 'google', 'google_region_22', 'OWNER', 'ACTIVE', '성남 모란반찬 사장님', 'owner22@example.com', NULL, '경기도', 1, 1, 1, 0, NULL, NULL, 37.432, 127.129, NOW(), NOW()),
(23, 'google', 'google_region_23', 'OWNER', 'ACTIVE', '고양 일산도시락 사장님', 'owner23@example.com', NULL, '경기도', 1, 1, 1, 0, NULL, NULL, 37.6584, 126.774, NOW(), NOW()),
(24, 'google', 'google_region_24', 'OWNER', 'ACTIVE', '해운대 바다카페 사장님', 'owner24@example.com', NULL, '부산광역시', 1, 1, 1, 0, NULL, NULL, 35.1587, 129.1604, NOW(), NOW()),
(25, 'google', 'google_region_25', 'OWNER', 'ACTIVE', '서면 단팥공방 사장님', 'owner25@example.com', NULL, '부산광역시', 1, 1, 1, 0, NULL, NULL, 35.1578, 129.0594, NOW(), NOW()),
(26, 'google', 'google_region_26', 'OWNER', 'ACTIVE', '광안리 든든도시락 사장님', 'owner26@example.com', NULL, '부산광역시', 1, 1, 1, 0, NULL, NULL, 35.1532, 129.1186, NOW(), NOW()),
(27, 'google', 'google_region_27', 'OWNER', 'ACTIVE', '동성로 할매반찬 사장님', 'owner27@example.com', NULL, '대구광역시', 1, 1, 1, 0, NULL, NULL, 35.8714, 128.5958, NOW(), NOW()),
(28, 'google', 'google_region_28', 'OWNER', 'ACTIVE', '수성 아침빵집 사장님', 'owner28@example.com', NULL, '대구광역시', 1, 1, 1, 0, NULL, NULL, 35.858, 128.63, NOW(), NOW()),
(29, 'google', 'google_region_29', 'OWNER', 'ACTIVE', '부평 한끼도시락 사장님', 'owner29@example.com', NULL, '인천광역시', 1, 1, 1, 0, NULL, NULL, 37.489, 126.724, NOW(), NOW()),
(30, 'google', 'google_region_30', 'OWNER', 'ACTIVE', '둔산 오후의카페 사장님', 'owner30@example.com', NULL, '대전광역시', 1, 1, 1, 0, NULL, NULL, 36.351, 127.378, NOW(), NOW()),
(31, 'google', 'google_region_31', 'OWNER', 'ACTIVE', '상무 정성반찬 사장님', 'owner31@example.com', NULL, '광주광역시', 1, 1, 1, 0, NULL, NULL, 35.153, 126.851, NOW(), NOW()),
(32, 'google', 'google_region_32', 'OWNER', 'ACTIVE', '제주 오름베이커리 사장님', 'owner32@example.com', NULL, '제주특별자치도', 1, 1, 1, 0, NULL, NULL, 33.489, 126.498, NOW(), NOW()),
(33, 'google', 'google_region_33', 'OWNER', 'ACTIVE', '춘천 닭갈비도시락 사장님', 'owner33@example.com', NULL, '강원특별자치도', 1, 1, 1, 0, NULL, NULL, 37.8813, 127.7298, NOW(), NOW()),
(34, 'google', 'google_region_34', 'OWNER', 'ACTIVE', '청주 아침카페 사장님', 'owner34@example.com', NULL, '충청북도', 1, 1, 1, 0, NULL, NULL, 36.635, 127.489, NOW(), NOW());

-- 2. 지역 매장 (승인 13곳 + 승인 대기 1곳)
INSERT IGNORE INTO store (id, store_name, category, address, latitude, longitude, operating_hours, phone, business_number, role, owner_id, approval_status, reliability_suspension_count, reliability_banned, image_url, created_at, updated_at, sido) VALUES
(11, '수원 행궁빵집', '베이커리', '경기도 수원시 팔달구 행궁로 25', 37.2819, 127.016, '08:00 ~ 21:00', '011-000-0011', '911-11-00011', 'OWNER', 21, 'APPROVED', 0, 0, 'https://picsum.photos/seed/girigiri-store-11/600/400', NOW(), NOW(), '경기'),
(12, '성남 모란반찬', '반찬', '경기도 성남시 중원구 성남대로 1100', 37.432, 127.129, '10:00 ~ 20:00', '012-000-0012', '912-11-00012', 'OWNER', 22, 'APPROVED', 0, 0, 'https://picsum.photos/seed/girigiri-store-12/600/400', NOW(), NOW(), '경기'),
(13, '고양 일산도시락', '도시락', '경기도 고양시 일산동구 중앙로 1200', 37.6584, 126.774, '10:00 ~ 21:00', '013-000-0013', '913-11-00013', 'OWNER', 23, 'APPROVED', 0, 0, 'https://picsum.photos/seed/girigiri-store-13/600/400', NOW(), NOW(), '경기'),
(14, '해운대 바다카페', '카페', '부산광역시 해운대구 해운대해변로 264', 35.1587, 129.1604, '09:00 ~ 22:00', '014-000-0014', '914-11-00014', 'OWNER', 24, 'APPROVED', 0, 0, 'https://picsum.photos/seed/girigiri-store-14/600/400', NOW(), NOW(), '부산'),
(15, '서면 단팥공방', '베이커리', '부산광역시 부산진구 서면로 68', 35.1578, 129.0594, '08:30 ~ 21:00', '015-000-0015', '915-11-00015', 'OWNER', 25, 'APPROVED', 0, 0, 'https://picsum.photos/seed/girigiri-store-15/600/400', NOW(), NOW(), '부산'),
(16, '광안리 든든도시락', '도시락', '부산광역시 수영구 광안해변로 219', 35.1532, 129.1186, '10:00 ~ 21:30', '016-000-0016', '916-11-00016', 'OWNER', 26, 'APPROVED', 0, 0, 'https://picsum.photos/seed/girigiri-store-16/600/400', NOW(), NOW(), '부산'),
(17, '동성로 할매반찬', '반찬', '대구광역시 중구 동성로 30', 35.8714, 128.5958, '09:30 ~ 20:30', '017-000-0017', '917-11-00017', 'OWNER', 27, 'APPROVED', 0, 0, 'https://picsum.photos/seed/girigiri-store-17/600/400', NOW(), NOW(), '대구'),
(18, '수성 아침빵집', '베이커리', '대구광역시 수성구 달구벌대로 2500', 35.858, 128.63, '07:30 ~ 21:00', '018-000-0018', '918-11-00018', 'OWNER', 28, 'APPROVED', 0, 0, 'https://picsum.photos/seed/girigiri-store-18/600/400', NOW(), NOW(), '대구'),
(19, '부평 한끼도시락', '도시락', '인천광역시 부평구 부평대로 100', 37.489, 126.724, '10:00 ~ 21:00', '019-000-0019', '919-11-00019', 'OWNER', 29, 'APPROVED', 0, 0, 'https://picsum.photos/seed/girigiri-store-19/600/400', NOW(), NOW(), '인천'),
(20, '둔산 오후의카페', '카페', '대전광역시 서구 둔산로 100', 36.351, 127.378, '09:00 ~ 22:00', '020-000-0020', '920-11-00020', 'OWNER', 30, 'APPROVED', 0, 0, 'https://picsum.photos/seed/girigiri-store-20/600/400', NOW(), NOW(), '대전'),
(21, '상무 정성반찬', '반찬', '광주광역시 서구 상무중앙로 110', 35.153, 126.851, '10:00 ~ 20:00', '021-000-0021', '921-11-00021', 'OWNER', 31, 'APPROVED', 0, 0, 'https://picsum.photos/seed/girigiri-store-21/600/400', NOW(), NOW(), '광주'),
(22, '제주 오름베이커리', '베이커리', '제주특별자치도 제주시 연동 300', 33.489, 126.498, '08:00 ~ 20:00', '022-000-0022', '922-11-00022', 'OWNER', 32, 'APPROVED', 0, 0, 'https://picsum.photos/seed/girigiri-store-22/600/400', NOW(), NOW(), '제주'),
(23, '춘천 닭갈비도시락', '도시락', '강원특별자치도 춘천시 중앙로 50', 37.8813, 127.7298, '10:00 ~ 20:30', '023-000-0023', '923-11-00023', 'OWNER', 33, 'APPROVED', 0, 0, 'https://picsum.photos/seed/girigiri-store-23/600/400', NOW(), NOW(), '강원'),
(24, '청주 아침카페', '카페', '충청북도 청주시 상당구 상당로 80', 36.635, 127.489, '08:00 ~ 21:00', '024-000-0024', '924-11-00024', 'OWNER', 34, 'PENDING', 0, 0, 'https://picsum.photos/seed/girigiri-store-24/600/400', NOW(), NOW(), '충북');

-- 3. 상품: 최근 1~6일 전에 등록된 마감 지난 상품 (sold=판매, expired=폐기)
INSERT IGNORE INTO product (id, store_id, name, original_price, discounted_price, quantity, remaining_quantity, image_url, description, status, registered_at) VALUES
(101, 11, '식빵 마감세트', 6000, 3000, 5, 0, '/images/pixabay/bakery/bakery-1077984.jpg', '식빵 마감세트 마감 할인', 'sold', DATE_SUB(NOW(), INTERVAL 1 DAY)),
(102, 11, '크루아상 3개입', 9000, 4500, 5, 0, '/images/pixabay/bakery/bakery-1743939.jpg', '크루아상 3개입 마감 할인', 'sold', DATE_SUB(NOW(), INTERVAL 2 DAY)),
(103, 11, '소보로빵 4개', 7000, 3500, 5, 0, '/images/pixabay/bakery/bakery-2561.jpg', '소보로빵 4개 마감 할인', 'sold', DATE_SUB(NOW(), INTERVAL 3 DAY)),
(104, 11, '단팥빵 5개입', 8400, 4200, 5, 0, '/images/pixabay/bakery/bakery-1868573.jpg', '단팥빵 5개입 마감 할인', 'sold', DATE_SUB(NOW(), INTERVAL 4 DAY)),
(105, 11, '식빵 마감세트', 6000, 3000, 5, 0, '/images/pixabay/bakery/bakery-1077984.jpg', '식빵 마감세트 마감 할인', 'sold', DATE_SUB(NOW(), INTERVAL 5 DAY)),
(106, 11, '크루아상 3개입', 9000, 4500, 5, 0, '/images/pixabay/bakery/bakery-1743939.jpg', '크루아상 3개입 마감 할인', 'sold', DATE_SUB(NOW(), INTERVAL 6 DAY)),
(107, 11, '소보로빵 4개', 7000, 3500, 5, 2, '/images/pixabay/bakery/bakery-2561.jpg', '소보로빵 4개 마감 할인', 'expired', DATE_SUB(NOW(), INTERVAL 1 DAY)),
(108, 11, '단팥빵 5개입', 8400, 4200, 5, 2, '/images/pixabay/bakery/bakery-1868573.jpg', '단팥빵 5개입 마감 할인', 'expired', DATE_SUB(NOW(), INTERVAL 2 DAY)),
(109, 12, '오늘의 나물 반찬세트', 9000, 5400, 5, 0, '/images/pixabay/banchan/banchan-1141242.jpg', '오늘의 나물 반찬세트 마감 할인', 'sold', DATE_SUB(NOW(), INTERVAL 1 DAY)),
(110, 12, '잡채 한 팩', 9000, 5400, 5, 0, '/images/pixabay/banchan/banchan-207235.jpg', '잡채 한 팩 마감 할인', 'sold', DATE_SUB(NOW(), INTERVAL 2 DAY)),
(111, 12, '멸치볶음 + 콩자반', 5000, 3000, 5, 0, '/images/pixabay/banchan/banchan-2449656.jpg', '멸치볶음 + 콩자반 마감 할인', 'sold', DATE_SUB(NOW(), INTERVAL 3 DAY)),
(112, 12, '오늘의 나물 반찬세트', 9000, 5400, 5, 0, '/images/pixabay/banchan/banchan-1141242.jpg', '오늘의 나물 반찬세트 마감 할인', 'sold', DATE_SUB(NOW(), INTERVAL 4 DAY)),
(113, 12, '잡채 한 팩', 9000, 5400, 5, 2, '/images/pixabay/banchan/banchan-207235.jpg', '잡채 한 팩 마감 할인', 'expired', DATE_SUB(NOW(), INTERVAL 5 DAY)),
(114, 12, '멸치볶음 + 콩자반', 5000, 3000, 5, 2, '/images/pixabay/banchan/banchan-2449656.jpg', '멸치볶음 + 콩자반 마감 할인', 'expired', DATE_SUB(NOW(), INTERVAL 6 DAY)),
(115, 12, '오늘의 나물 반찬세트', 9000, 5400, 5, 2, '/images/pixabay/banchan/banchan-1141242.jpg', '오늘의 나물 반찬세트 마감 할인', 'expired', DATE_SUB(NOW(), INTERVAL 1 DAY)),
(116, 13, '제육볶음 도시락', 6500, 4000, 5, 0, '/images/pixabay/dosirak/dosirak-1702652.jpg', '제육볶음 도시락 마감 할인', 'sold', DATE_SUB(NOW(), INTERVAL 1 DAY)),
(117, 13, '오늘의 도시락', 7000, 3500, 5, 2, '/images/pixabay/dosirak/dosirak-1743370.jpg', '오늘의 도시락 마감 할인', 'expired', DATE_SUB(NOW(), INTERVAL 2 DAY)),
(118, 13, '김밥 3줄 세트', 6000, 3000, 5, 2, '/images/pixabay/dosirak/dosirak-2806566.jpg', '김밥 3줄 세트 마감 할인', 'expired', DATE_SUB(NOW(), INTERVAL 3 DAY)),
(119, 13, '제육볶음 도시락', 6500, 4000, 5, 2, '/images/pixabay/dosirak/dosirak-1702652.jpg', '제육볶음 도시락 마감 할인', 'expired', DATE_SUB(NOW(), INTERVAL 4 DAY)),
(120, 13, '오늘의 도시락', 7000, 3500, 5, 2, '/images/pixabay/dosirak/dosirak-1743370.jpg', '오늘의 도시락 마감 할인', 'expired', DATE_SUB(NOW(), INTERVAL 5 DAY)),
(121, 13, '김밥 3줄 세트', 6000, 3000, 5, 2, '/images/pixabay/dosirak/dosirak-2806566.jpg', '김밥 3줄 세트 마감 할인', 'expired', DATE_SUB(NOW(), INTERVAL 6 DAY)),
(122, 14, '케이크 조각 세트', 12000, 6000, 5, 0, '/images/pixabay/dessert/dessert-1914463.jpg', '케이크 조각 세트 마감 할인', 'sold', DATE_SUB(NOW(), INTERVAL 1 DAY)),
(123, 14, '샌드위치 세트', 7500, 3750, 5, 0, '/images/pixabay/food-general/food-general-1331447.jpg', '샌드위치 세트 마감 할인', 'sold', DATE_SUB(NOW(), INTERVAL 2 DAY)),
(124, 14, '쿠키 모음', 6000, 3000, 5, 2, '/images/pixabay/dessert/dessert-1971552.jpg', '쿠키 모음 마감 할인', 'expired', DATE_SUB(NOW(), INTERVAL 3 DAY)),
(125, 14, '케이크 조각 세트', 12000, 6000, 5, 2, '/images/pixabay/dessert/dessert-1914463.jpg', '케이크 조각 세트 마감 할인', 'expired', DATE_SUB(NOW(), INTERVAL 4 DAY)),
(126, 14, '샌드위치 세트', 7500, 3750, 5, 2, '/images/pixabay/food-general/food-general-1331447.jpg', '샌드위치 세트 마감 할인', 'expired', DATE_SUB(NOW(), INTERVAL 5 DAY)),
(127, 14, '쿠키 모음', 6000, 3000, 5, 2, '/images/pixabay/dessert/dessert-1971552.jpg', '쿠키 모음 마감 할인', 'expired', DATE_SUB(NOW(), INTERVAL 6 DAY)),
(128, 15, '식빵 마감세트', 6000, 3000, 5, 2, '/images/pixabay/bakery/bakery-1077984.jpg', '식빵 마감세트 마감 할인', 'expired', DATE_SUB(NOW(), INTERVAL 1 DAY)),
(129, 15, '크루아상 3개입', 9000, 4500, 5, 2, '/images/pixabay/bakery/bakery-1743939.jpg', '크루아상 3개입 마감 할인', 'expired', DATE_SUB(NOW(), INTERVAL 2 DAY)),
(130, 15, '소보로빵 4개', 7000, 3500, 5, 2, '/images/pixabay/bakery/bakery-2561.jpg', '소보로빵 4개 마감 할인', 'expired', DATE_SUB(NOW(), INTERVAL 3 DAY)),
(131, 15, '단팥빵 5개입', 8400, 4200, 5, 2, '/images/pixabay/bakery/bakery-1868573.jpg', '단팥빵 5개입 마감 할인', 'expired', DATE_SUB(NOW(), INTERVAL 4 DAY)),
(132, 15, '식빵 마감세트', 6000, 3000, 5, 2, '/images/pixabay/bakery/bakery-1077984.jpg', '식빵 마감세트 마감 할인', 'expired', DATE_SUB(NOW(), INTERVAL 5 DAY)),
(133, 16, '제육볶음 도시락', 6500, 4000, 5, 2, '/images/pixabay/dosirak/dosirak-1702652.jpg', '제육볶음 도시락 마감 할인', 'expired', DATE_SUB(NOW(), INTERVAL 1 DAY)),
(134, 16, '오늘의 도시락', 7000, 3500, 5, 2, '/images/pixabay/dosirak/dosirak-1743370.jpg', '오늘의 도시락 마감 할인', 'expired', DATE_SUB(NOW(), INTERVAL 2 DAY)),
(135, 16, '김밥 3줄 세트', 6000, 3000, 5, 2, '/images/pixabay/dosirak/dosirak-2806566.jpg', '김밥 3줄 세트 마감 할인', 'expired', DATE_SUB(NOW(), INTERVAL 3 DAY)),
(136, 17, '오늘의 나물 반찬세트', 9000, 5400, 5, 0, '/images/pixabay/banchan/banchan-1141242.jpg', '오늘의 나물 반찬세트 마감 할인', 'sold', DATE_SUB(NOW(), INTERVAL 1 DAY)),
(137, 17, '잡채 한 팩', 9000, 5400, 5, 0, '/images/pixabay/banchan/banchan-207235.jpg', '잡채 한 팩 마감 할인', 'sold', DATE_SUB(NOW(), INTERVAL 2 DAY)),
(138, 17, '멸치볶음 + 콩자반', 5000, 3000, 5, 0, '/images/pixabay/banchan/banchan-2449656.jpg', '멸치볶음 + 콩자반 마감 할인', 'sold', DATE_SUB(NOW(), INTERVAL 3 DAY)),
(139, 17, '오늘의 나물 반찬세트', 9000, 5400, 5, 0, '/images/pixabay/banchan/banchan-1141242.jpg', '오늘의 나물 반찬세트 마감 할인', 'sold', DATE_SUB(NOW(), INTERVAL 4 DAY)),
(140, 17, '잡채 한 팩', 9000, 5400, 5, 2, '/images/pixabay/banchan/banchan-207235.jpg', '잡채 한 팩 마감 할인', 'expired', DATE_SUB(NOW(), INTERVAL 5 DAY)),
(141, 17, '멸치볶음 + 콩자반', 5000, 3000, 5, 2, '/images/pixabay/banchan/banchan-2449656.jpg', '멸치볶음 + 콩자반 마감 할인', 'expired', DATE_SUB(NOW(), INTERVAL 6 DAY)),
(142, 18, '식빵 마감세트', 6000, 3000, 5, 0, '/images/pixabay/bakery/bakery-1077984.jpg', '식빵 마감세트 마감 할인', 'sold', DATE_SUB(NOW(), INTERVAL 1 DAY)),
(143, 18, '크루아상 3개입', 9000, 4500, 5, 0, '/images/pixabay/bakery/bakery-1743939.jpg', '크루아상 3개입 마감 할인', 'sold', DATE_SUB(NOW(), INTERVAL 2 DAY)),
(144, 18, '소보로빵 4개', 7000, 3500, 5, 0, '/images/pixabay/bakery/bakery-2561.jpg', '소보로빵 4개 마감 할인', 'sold', DATE_SUB(NOW(), INTERVAL 3 DAY)),
(145, 18, '단팥빵 5개입', 8400, 4200, 5, 2, '/images/pixabay/bakery/bakery-1868573.jpg', '단팥빵 5개입 마감 할인', 'expired', DATE_SUB(NOW(), INTERVAL 4 DAY)),
(146, 18, '식빵 마감세트', 6000, 3000, 5, 2, '/images/pixabay/bakery/bakery-1077984.jpg', '식빵 마감세트 마감 할인', 'expired', DATE_SUB(NOW(), INTERVAL 5 DAY)),
(147, 19, '제육볶음 도시락', 6500, 4000, 5, 0, '/images/pixabay/dosirak/dosirak-1702652.jpg', '제육볶음 도시락 마감 할인', 'sold', DATE_SUB(NOW(), INTERVAL 1 DAY)),
(148, 19, '오늘의 도시락', 7000, 3500, 5, 2, '/images/pixabay/dosirak/dosirak-1743370.jpg', '오늘의 도시락 마감 할인', 'expired', DATE_SUB(NOW(), INTERVAL 2 DAY)),
(149, 19, '김밥 3줄 세트', 6000, 3000, 5, 2, '/images/pixabay/dosirak/dosirak-2806566.jpg', '김밥 3줄 세트 마감 할인', 'expired', DATE_SUB(NOW(), INTERVAL 3 DAY)),
(150, 20, '케이크 조각 세트', 12000, 6000, 5, 0, '/images/pixabay/dessert/dessert-1914463.jpg', '케이크 조각 세트 마감 할인', 'sold', DATE_SUB(NOW(), INTERVAL 1 DAY)),
(151, 20, '샌드위치 세트', 7500, 3750, 5, 0, '/images/pixabay/food-general/food-general-1331447.jpg', '샌드위치 세트 마감 할인', 'sold', DATE_SUB(NOW(), INTERVAL 2 DAY)),
(152, 20, '쿠키 모음', 6000, 3000, 5, 0, '/images/pixabay/dessert/dessert-1971552.jpg', '쿠키 모음 마감 할인', 'sold', DATE_SUB(NOW(), INTERVAL 3 DAY)),
(153, 20, '케이크 조각 세트', 12000, 6000, 5, 2, '/images/pixabay/dessert/dessert-1914463.jpg', '케이크 조각 세트 마감 할인', 'expired', DATE_SUB(NOW(), INTERVAL 4 DAY)),
(154, 20, '샌드위치 세트', 7500, 3750, 5, 2, '/images/pixabay/food-general/food-general-1331447.jpg', '샌드위치 세트 마감 할인', 'expired', DATE_SUB(NOW(), INTERVAL 5 DAY)),
(155, 21, '오늘의 나물 반찬세트', 9000, 5400, 5, 0, '/images/pixabay/banchan/banchan-1141242.jpg', '오늘의 나물 반찬세트 마감 할인', 'sold', DATE_SUB(NOW(), INTERVAL 1 DAY)),
(156, 21, '잡채 한 팩', 9000, 5400, 5, 0, '/images/pixabay/banchan/banchan-207235.jpg', '잡채 한 팩 마감 할인', 'sold', DATE_SUB(NOW(), INTERVAL 2 DAY)),
(157, 22, '식빵 마감세트', 6000, 3000, 5, 0, '/images/pixabay/bakery/bakery-1077984.jpg', '식빵 마감세트 마감 할인', 'sold', DATE_SUB(NOW(), INTERVAL 1 DAY)),
(158, 22, '크루아상 3개입', 9000, 4500, 5, 0, '/images/pixabay/bakery/bakery-1743939.jpg', '크루아상 3개입 마감 할인', 'sold', DATE_SUB(NOW(), INTERVAL 2 DAY)),
(159, 22, '소보로빵 4개', 7000, 3500, 5, 0, '/images/pixabay/bakery/bakery-2561.jpg', '소보로빵 4개 마감 할인', 'sold', DATE_SUB(NOW(), INTERVAL 3 DAY)),
(160, 22, '단팥빵 5개입', 8400, 4200, 5, 0, '/images/pixabay/bakery/bakery-1868573.jpg', '단팥빵 5개입 마감 할인', 'sold', DATE_SUB(NOW(), INTERVAL 4 DAY)),
(161, 22, '식빵 마감세트', 6000, 3000, 5, 0, '/images/pixabay/bakery/bakery-1077984.jpg', '식빵 마감세트 마감 할인', 'sold', DATE_SUB(NOW(), INTERVAL 5 DAY)),
(162, 22, '크루아상 3개입', 9000, 4500, 5, 2, '/images/pixabay/bakery/bakery-1743939.jpg', '크루아상 3개입 마감 할인', 'expired', DATE_SUB(NOW(), INTERVAL 6 DAY));

-- 4. 판매된 상품의 픽업완료 예약 (대시보드 지역표는 picked 예약이 있는 상품을 '판매'로 센다)
INSERT IGNORE INTO reservation (id, user_id, product_id, product_name, store_id, reserved_quantity, total_price, pickup_time, pickup_code, status, reserved_at, picked_at, accepted_at, cancel_reason, cancelled_by) VALUES
(101, 13, 101, '식빵 마감세트', 11, 1, 3000, DATE_SUB(NOW(), INTERVAL 1 DAY), 'REG-101', 'picked', DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), NULL, NULL),
(102, 14, 102, '크루아상 3개입', 11, 1, 4500, DATE_SUB(NOW(), INTERVAL 2 DAY), 'REG-102', 'picked', DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), NULL, NULL),
(103, 15, 103, '소보로빵 4개', 11, 1, 3500, DATE_SUB(NOW(), INTERVAL 3 DAY), 'REG-103', 'picked', DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), NULL, NULL),
(104, 1, 104, '단팥빵 5개입', 11, 1, 4200, DATE_SUB(NOW(), INTERVAL 4 DAY), 'REG-104', 'picked', DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY), NULL, NULL),
(105, 12, 105, '식빵 마감세트', 11, 1, 3000, DATE_SUB(NOW(), INTERVAL 5 DAY), 'REG-105', 'picked', DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY), NULL, NULL),
(106, 13, 106, '크루아상 3개입', 11, 1, 4500, DATE_SUB(NOW(), INTERVAL 6 DAY), 'REG-106', 'picked', DATE_SUB(NOW(), INTERVAL 6 DAY), DATE_SUB(NOW(), INTERVAL 6 DAY), DATE_SUB(NOW(), INTERVAL 6 DAY), NULL, NULL),
(107, 14, 109, '오늘의 나물 반찬세트', 12, 1, 5400, DATE_SUB(NOW(), INTERVAL 1 DAY), 'REG-107', 'picked', DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), NULL, NULL),
(108, 15, 110, '잡채 한 팩', 12, 1, 5400, DATE_SUB(NOW(), INTERVAL 2 DAY), 'REG-108', 'picked', DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), NULL, NULL),
(109, 1, 111, '멸치볶음 + 콩자반', 12, 1, 3000, DATE_SUB(NOW(), INTERVAL 3 DAY), 'REG-109', 'picked', DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), NULL, NULL),
(110, 12, 112, '오늘의 나물 반찬세트', 12, 1, 5400, DATE_SUB(NOW(), INTERVAL 4 DAY), 'REG-110', 'picked', DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY), NULL, NULL),
(111, 13, 116, '제육볶음 도시락', 13, 1, 4000, DATE_SUB(NOW(), INTERVAL 1 DAY), 'REG-111', 'picked', DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), NULL, NULL),
(112, 14, 122, '케이크 조각 세트', 14, 1, 6000, DATE_SUB(NOW(), INTERVAL 1 DAY), 'REG-112', 'picked', DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), NULL, NULL),
(113, 15, 123, '샌드위치 세트', 14, 1, 3750, DATE_SUB(NOW(), INTERVAL 2 DAY), 'REG-113', 'picked', DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), NULL, NULL),
(114, 1, 136, '오늘의 나물 반찬세트', 17, 1, 5400, DATE_SUB(NOW(), INTERVAL 1 DAY), 'REG-114', 'picked', DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), NULL, NULL),
(115, 12, 137, '잡채 한 팩', 17, 1, 5400, DATE_SUB(NOW(), INTERVAL 2 DAY), 'REG-115', 'picked', DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), NULL, NULL),
(116, 13, 138, '멸치볶음 + 콩자반', 17, 1, 3000, DATE_SUB(NOW(), INTERVAL 3 DAY), 'REG-116', 'picked', DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), NULL, NULL),
(117, 14, 139, '오늘의 나물 반찬세트', 17, 1, 5400, DATE_SUB(NOW(), INTERVAL 4 DAY), 'REG-117', 'picked', DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY), NULL, NULL),
(118, 15, 142, '식빵 마감세트', 18, 1, 3000, DATE_SUB(NOW(), INTERVAL 1 DAY), 'REG-118', 'picked', DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), NULL, NULL),
(119, 1, 143, '크루아상 3개입', 18, 1, 4500, DATE_SUB(NOW(), INTERVAL 2 DAY), 'REG-119', 'picked', DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), NULL, NULL),
(120, 12, 144, '소보로빵 4개', 18, 1, 3500, DATE_SUB(NOW(), INTERVAL 3 DAY), 'REG-120', 'picked', DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), NULL, NULL),
(121, 13, 147, '제육볶음 도시락', 19, 1, 4000, DATE_SUB(NOW(), INTERVAL 1 DAY), 'REG-121', 'picked', DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), NULL, NULL),
(122, 14, 150, '케이크 조각 세트', 20, 1, 6000, DATE_SUB(NOW(), INTERVAL 1 DAY), 'REG-122', 'picked', DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), NULL, NULL),
(123, 15, 151, '샌드위치 세트', 20, 1, 3750, DATE_SUB(NOW(), INTERVAL 2 DAY), 'REG-123', 'picked', DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), NULL, NULL),
(124, 1, 152, '쿠키 모음', 20, 1, 3000, DATE_SUB(NOW(), INTERVAL 3 DAY), 'REG-124', 'picked', DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), NULL, NULL),
(125, 12, 155, '오늘의 나물 반찬세트', 21, 1, 5400, DATE_SUB(NOW(), INTERVAL 1 DAY), 'REG-125', 'picked', DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), NULL, NULL),
(126, 13, 156, '잡채 한 팩', 21, 1, 5400, DATE_SUB(NOW(), INTERVAL 2 DAY), 'REG-126', 'picked', DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), NULL, NULL),
(127, 14, 157, '식빵 마감세트', 22, 1, 3000, DATE_SUB(NOW(), INTERVAL 1 DAY), 'REG-127', 'picked', DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), NULL, NULL),
(128, 15, 158, '크루아상 3개입', 22, 1, 4500, DATE_SUB(NOW(), INTERVAL 2 DAY), 'REG-128', 'picked', DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), NULL, NULL),
(129, 1, 159, '소보로빵 4개', 22, 1, 3500, DATE_SUB(NOW(), INTERVAL 3 DAY), 'REG-129', 'picked', DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), NULL, NULL),
(130, 12, 160, '단팥빵 5개입', 22, 1, 4200, DATE_SUB(NOW(), INTERVAL 4 DAY), 'REG-130', 'picked', DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY), NULL, NULL),
(131, 13, 161, '식빵 마감세트', 22, 1, 3000, DATE_SUB(NOW(), INTERVAL 5 DAY), 'REG-131', 'picked', DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY), NULL, NULL);

SET FOREIGN_KEY_CHECKS=1;

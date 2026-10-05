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
